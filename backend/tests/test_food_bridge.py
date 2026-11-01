"""Only temporary SQLite data and ephemeral signing keys; no network."""
import base64
import hashlib
import json
import os
import tempfile
import time
import unittest
from pathlib import Path
from uuid import uuid4

os.environ["DATABASE_URL"] = "sqlite://"

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from app.database import get_db
from app.models import Meal, User
from app.routers.food_bridge import BridgeNonce, ISSUER, ORIGIN, PREFIX, router


def encode(value):
    return base64.urlsafe_b64encode(value).decode().rstrip("=")


class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.grant_path = Path(self.tmp.name) / "grant.json"
        self.previous = os.environ.get("FOOD_BRIDGE_GRANT_FILE")
        os.environ["FOOD_BRIDGE_GRANT_FILE"] = str(self.grant_path)
        self.grant = {"enabled": True, "key_id": "test-key", "sites_owner_id": "test-ido", "user_id": 1, "username_normalized": "test-ido", "public_key_pem": self.key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()}
        self.grant_path.write_text(json.dumps(self.grant))
        self.engine = create_engine("sqlite://", poolclass=StaticPool, connect_args={"check_same_thread": False})
        for table in [User.__table__, Meal.__table__, BridgeNonce.__table__]:
            table.create(self.engine)
        with Session(self.engine) as db:
            for i, name in [(1, "test-ido"), (2, "test-other")]:
                db.add(User(id=i, username=name, username_normalized=name, display_name=name, password_hash="!test", nutrition_reset_hour=4, nutrition_timezone="Asia/Jerusalem"))
            db.commit()
        app = FastAPI()
        app.include_router(router)
        def session():
            with Session(self.engine) as db:
                yield db
        app.dependency_overrides[get_db] = session
        self.client = TestClient(app)
        self.body = {"clientMealId": str(uuid4()), "deviceId": "chatgpt-food-test-ido", "eatenAt": "2026-11-01T12:00:00+02:00", "timezone": "Asia/Jerusalem", "mealType": "lunch", "title": "isolated fixture", "notes": "", "caloriesKcal": 123, "proteinG": 12, "carbsG": 10, "fatG": 3, "fiberG": 2}

    def tearDown(self):
        self.client.close()
        self.engine.dispose()
        self.tmp.cleanup()
        if self.previous is None:
            os.environ.pop("FOOD_BRIDGE_GRANT_FILE", None)
        else:
            os.environ["FOOD_BRIDGE_GRANT_FILE"] = self.previous

    def sign(self, method, path, raw=b"", **override):
        now = int(time.time())
        claims = {"iss": ISSUER, "aud": ORIGIN, "sub": "test-ido", "iat": now, "exp": now+60, "jti": str(uuid4()), "htm": method, "htu": ORIGIN+path, "body_sha256": hashlib.sha256(raw).hexdigest(), **override}
        value = encode(json.dumps({"alg": "PS256", "typ": "JWT", "kid": "test-key"}).encode()) + "." + encode(json.dumps(claims).encode())
        signature = self.key.sign(value.encode(), padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=32), hashes.SHA256())
        return "Bearer " + value + "." + encode(signature)

    def call(self, method, suffix, body=None, **claims):
        raw = b"" if body is None else json.dumps(body).encode()
        path = PREFIX+suffix
        return self.client.request(method, path, content=raw, headers={"content-type": "application/json", "authorization": self.sign(method, path, raw, **claims)})

    def test_authentication_binding_and_revocation(self):
        self.assertEqual(self.client.get(PREFIX+"/status").status_code, 401)
        self.assertEqual(self.call("GET", "/status").json()["username"], "test-ido")
        for claims in [{"sub": "test-other"}, {"aud": "https://evil.invalid"}, {"exp": int(time.time())-1}, {"htm": "POST"}, {"htu": ORIGIN+"/api/admin"}]:
            self.assertEqual(self.call("GET", "/status", **claims).status_code, 401)
        self.grant["enabled"] = False
        self.grant_path.write_text(json.dumps(self.grant))
        self.assertEqual(self.call("GET", "/status").status_code, 503)

    def test_payload_tampering_and_replay(self):
        raw = json.dumps(self.body).encode()
        path = PREFIX+"/meals"
        token = self.sign("POST", path, raw)
        headers = {"authorization": token, "content-type": "application/json"}
        self.assertEqual(self.client.post(path, content=raw.replace(b'123', b'124'), headers=headers).status_code, 401)
        self.assertEqual(self.client.post(path, content=raw, headers=headers).status_code, 200)
        self.assertEqual(self.client.post(path, content=raw, headers=headers).status_code, 409)

    def test_idempotency_update_delete_and_user_separation(self):
        first = self.call("POST", "/meals", self.body)
        self.assertEqual(first.status_code, 200, first.text)
        meal_id = first.json()["id"]
        self.assertEqual(self.call("POST", "/meals", self.body).json()["id"], meal_id)
        with Session(self.engine) as db:
            self.assertEqual(len(db.scalars(select(Meal)).all()), 1)
        edited = {**self.body, "caloriesKcal": 200}
        self.assertEqual(self.call("PATCH", f"/meals/{meal_id}", edited).json()["caloriesKcal"], 200)
        self.assertEqual(self.call("PATCH", f"/meals/{meal_id}", {**edited, "clientMealId": str(uuid4())}).status_code, 409)
        with Session(self.engine) as db:
            db.get(Meal, meal_id).user_id = 2
            db.commit()
        self.assertEqual(self.call("PATCH", f"/meals/{meal_id}", edited).status_code, 404)
        self.assertEqual(self.call("DELETE", f"/meals/{meal_id}").status_code, 404)
        with Session(self.engine) as db:
            db.get(Meal, meal_id).user_id = 1
            db.get(Meal, meal_id).source_device_id = "other-app"
            db.commit()
        self.assertEqual(self.call("DELETE", f"/meals/{meal_id}").status_code, 404)
        with Session(self.engine) as db:
            db.get(Meal, meal_id).source_device_id = self.body["deviceId"]
            db.commit()
        self.assertEqual(self.call("DELETE", f"/meals/{meal_id}").json(), {"ok": True, "mealId": meal_id})

    def test_no_arbitrary_user_device_or_extra_permissions(self):
        for body in [{**self.body, "user_id": 2}, {**self.body, "deviceId": "other-app"}, {**self.body, "timezone": "UTC"}]:
            self.assertEqual(self.call("POST", "/meals", body).status_code, 401)
        self.grant["username_normalized"] = "test-other"
        self.grant_path.write_text(json.dumps(self.grant))
        self.assertEqual(self.call("GET", "/status").status_code, 401)


if __name__ == "__main__":
    unittest.main()
