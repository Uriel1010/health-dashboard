import os
os.environ.setdefault("DATABASE_URL", "sqlite://")
import unittest
from datetime import datetime, timedelta, timezone, date
from unittest.mock import patch
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.compiler import compiles
from sqlalchemy.dialects.postgresql import JSONB
from app.database import Base
from app.models import User, BrowserSession, Meal
from app.push import PushInstallation, MealPushDelivery, drain_one, InvalidToken
from app.push import Registration, register, notification_meal
from app.auth import token_hash
from fastapi import HTTPException
from types import SimpleNamespace
import hashlib


@compiles(JSONB, "sqlite")
def sqlite_json(type_, compiler, **kw):
    return "JSON"


class PushTests(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://")
        Base.metadata.create_all(self.engine)
        self.factory = sessionmaker(bind=self.engine)
        self.db = self.factory()
        self.user = User(username="test", username_normalized="test",display_name="Test",password_hash="not-a-real-password")
        self.other = User(username="other",username_normalized="other",display_name="Other",password_hash="not-a-real-password")
        self.db.add_all([self.user,self.other]); self.db.flush()
        self.session = BrowserSession(user_id=self.user.id,token_hash=token_hash("synthetic-session"),expires_at=datetime.now(timezone.utc)+timedelta(days=1))
        self.db.add(self.session); self.db.flush()
        self.install = PushInstallation(id="11111111-1111-1111-1111-111111111111",secret_hash=hashlib.sha256(b'x'*48).hexdigest(),user_id=self.user.id,session_id=self.session.id,
            token="t"*64,token_hash=hashlib.sha256(b't'*64).hexdigest(),binding="binding",enabled=True)
        self.db.add(self.install); self.db.commit()

    def tearDown(self):
        self.db.close(); self.engine.dispose()

    def meal(self,user_id=None):
        meal=Meal(user_id=user_id or self.user.id,source_device_id="test",eaten_at=datetime.now(timezone.utc),local_date=date.today(),
            timezone="UTC",meal_type="lunch",title="Synthetic meal",calories_kcal=500,protein_g=25,carbs_g=50,fat_g=10)
        self.db.add(meal); return meal

    def count(self):
        return len(self.db.scalars(select(MealPushDelivery)).all())

    def drain(self,sender):
        with patch("app.push.SessionLocal",self.factory):
            return drain_one(sender)

    def test_new_meals_queue_atomically_and_updates_do_not(self):
        meal=self.meal(); self.db.commit(); self.assertEqual(self.count(),1)
        meal.title="Changed"; self.db.commit(); self.assertEqual(self.count(),1)
        self.meal(); self.db.flush(); self.assertEqual(self.count(),2)
        self.db.rollback(); self.assertEqual(self.count(),1)

    def test_other_account_and_disabled_installation_do_not_queue(self):
        self.meal(self.other.id); self.db.commit(); self.assertEqual(self.count(),0)
        self.install.enabled=False; self.db.commit(); self.meal(); self.db.commit(); self.assertEqual(self.count(),0)

    def test_new_installation_does_not_receive_old_meals(self):
        self.db.delete(self.install); self.db.commit(); self.meal(); self.db.commit(); self.assertEqual(self.count(),0)

    def test_deliver_once_after_success(self):
        self.meal(); self.db.commit(); sent=[]
        self.assertTrue(self.drain(lambda i,d:sent.append(d.id)))
        self.assertFalse(self.drain(lambda i,d:sent.append(d.id))); self.assertEqual(len(sent),1)

    def test_revoked_session_prevents_delivery(self):
        self.meal(); self.session.revoked_at=datetime.now(timezone.utc); self.db.commit()
        self.drain(lambda i,d:self.fail("Revoked session received push"))
        self.db.expire_all(); self.assertIsNotNone(self.db.scalar(select(MealPushDelivery)).completed_at)

    def test_account_rebinding_invalidates_queued_events(self):
        self.meal(); self.db.commit(); self.install.binding="another-account"; self.db.commit()
        self.drain(lambda i,d:self.fail("Old binding received push"))

    def test_transient_failure_retries_and_invalid_token_disables(self):
        self.meal(); self.db.commit()
        def fail(i,d): raise RuntimeError("synthetic provider failure")
        self.drain(fail); self.db.expire_all(); delivery=self.db.scalar(select(MealPushDelivery))
        self.assertEqual(delivery.attempts,1); self.assertIsNone(delivery.completed_at)
        delivery.next_attempt_at=datetime.now(timezone.utc)-timedelta(seconds=1); self.db.commit()
        def invalid(i,d): raise InvalidToken()
        self.drain(invalid); self.db.expire_all(); self.assertFalse(self.install.enabled)

    def test_deleted_meal_does_not_notify(self):
        meal=self.meal(); self.db.commit(); meal.deleted_at=datetime.now(timezone.utc); self.db.commit()
        self.drain(lambda i,d:self.fail("Deleted meal received push"))

    def request(self):
        return SimpleNamespace(headers={},cookies={"health_dashboard_session":"synthetic-session"})

    def test_installation_ownership_and_token_ownership(self):
        payload=Registration(installationId=self.install.id,installationSecret="wrong"*10,token="t"*64)
        with self.assertRaises(HTTPException) as denied: register(payload,self.request(),self.db,self.user)
        self.assertEqual(denied.exception.status_code,403)
        payload.installationSecret="x"*48
        result=register(payload,self.request(),self.db,self.user)
        self.assertEqual(result["binding"],"binding")
        payload.installationId="22222222-2222-2222-2222-222222222222"
        with self.assertRaises(HTTPException) as denied: register(payload,self.request(),self.db,self.user)
        self.assertEqual(denied.exception.status_code,409)

    def test_registration_must_match_authenticated_session(self):
        payload=Registration(installationId=self.install.id,installationSecret="x"*48,token="t"*64)
        with self.assertRaises(HTTPException) as denied: register(payload,self.request(),self.db,self.other)
        self.assertEqual(denied.exception.status_code,401)

    def test_meal_fetch_is_account_scoped(self):
        meal=self.meal(); self.db.commit()
        with self.assertRaises(HTTPException) as denied: notification_meal(meal.id,self.db,self.other)
        self.assertEqual(denied.exception.status_code,404)
        self.assertEqual(notification_meal(meal.id,self.db,self.user).id,meal.id)
