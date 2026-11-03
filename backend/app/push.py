"""Account/session-bound meal push outbox. No meal contents leave the server."""
import asyncio
import hashlib
import logging
import os
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, UniqueConstraint, event, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from app.auth import require_data_user, token_hash, validate_request_origin
from app.config import get_settings
from app.database import Base, SessionLocal, get_db
from app.models import BrowserSession, Meal, User

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/push", tags=["push"])


def now():
    return datetime.now(timezone.utc)


class PushInstallation(Base):
    __tablename__ = "push_installations"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    secret_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    session_id: Mapped[int] = mapped_column(ForeignKey("browser_sessions.id", ondelete="CASCADE"))
    token: Mapped[str] = mapped_column(Text, nullable=False)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    binding: Mapped[str] = mapped_column(String(64), nullable=False)
    enabled: Mapped[bool] = mapped_column(default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class MealPushDelivery(Base):
    __tablename__ = "meal_push_deliveries"
    __table_args__ = (UniqueConstraint("installation_id", "meal_id", name="uq_push_installation_meal"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    installation_id: Mapped[str] = mapped_column(ForeignKey("push_installations.id", ondelete="CASCADE"))
    meal_id: Mapped[int] = mapped_column(ForeignKey("meals.id", ondelete="CASCADE"))
    binding: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)


class Registration(BaseModel):
    installationId: str = Field(pattern=r"^[a-f0-9-]{36}$")
    installationSecret: str = Field(min_length=40, max_length=128)
    token: str = Field(min_length=32, max_length=4096)
    enabled: bool = True


@router.get("/meals/{meal_id}")
def notification_meal(meal_id: int, db: Session = Depends(get_db), user: User = Depends(require_data_user)):
    from app.routers.nutrition_user import _meal_out
    meal = db.scalar(select(Meal).where(Meal.id == meal_id, Meal.user_id == user.id, Meal.deleted_at.is_(None)))
    if meal is None:
        raise HTTPException(404, "Meal unavailable")
    return _meal_out(meal, user, get_settings().app_timezone)


@router.post("/installations")
def register(payload: Registration, request: Request, db: Session = Depends(get_db), user: User = Depends(require_data_user)):
    settings = get_settings()
    validate_request_origin(request, settings)
    session = db.scalar(select(BrowserSession).where(BrowserSession.token_hash == token_hash(request.cookies.get(settings.session_cookie_name, ""))))
    if not session or session.user_id != user.id:
        raise HTTPException(401, "A dashboard session is required")
    row = db.get(PushInstallation, payload.installationId)
    secret = hashlib.sha256(payload.installationSecret.encode()).hexdigest()
    if row and not secrets.compare_digest(row.secret_hash, secret):
        raise HTTPException(403, "Installation ownership mismatch")
    hashed_token = hashlib.sha256(payload.token.encode()).hexdigest()
    owner = db.scalar(select(PushInstallation).where(PushInstallation.token_hash == hashed_token))
    if owner and owner.id != payload.installationId:
        raise HTTPException(409, "Token already registered")
    if row is None:
        row = PushInstallation(id=payload.installationId, secret_hash=secret, user_id=user.id, session_id=session.id, binding=secrets.token_hex(24))
        db.add(row)
    if row.user_id != user.id or row.session_id != session.id or not row.enabled:
        row.binding = secrets.token_hex(24)
    row.user_id, row.session_id = user.id, session.id
    row.token, row.token_hash = payload.token, hashed_token
    row.enabled, row.updated_at = payload.enabled, now()
    db.commit()
    return {"binding": row.binding, "available": bool(os.getenv("FCM_PROJECT_ID"))}


@event.listens_for(Session, "after_flush")
def enqueue_new_meals(db, _):
    # Covers every ORM meal insertion path, including integrations. Updates do not notify.
    for meal in list(db.new):
        if not isinstance(meal, Meal):
            continue
        recipients = db.scalars(select(PushInstallation).where(PushInstallation.user_id == meal.user_id, PushInstallation.enabled.is_(True))).all()
        for recipient in recipients:
            db.add(MealPushDelivery(installation_id=recipient.id, meal_id=meal.id, binding=recipient.binding))


class InvalidToken(Exception):
    pass


def send_fcm(installation, delivery):
    import google.auth
    from google.auth.transport.requests import AuthorizedSession
    credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/firebase.messaging"])
    with AuthorizedSession(credentials) as http:
        result = http.post(f"https://fcm.googleapis.com/v1/projects/{os.environ['FCM_PROJECT_ID']}/messages:send", timeout=15, json={"message": {
            "token": installation.token,
            "data": {"type": "meal.created", "mealId": str(delivery.meal_id), "eventId": str(delivery.id), "binding": delivery.binding},
            "android": {"priority": "high", "ttl": "86400s"},
        }})
        if any(e.get("errorCode") == "UNREGISTERED" for e in result.json().get("error", {}).get("details", [])):
            raise InvalidToken()
        result.raise_for_status()


def drain_one(sender=send_fcm):
    with SessionLocal() as db:
        delivery = db.scalar(select(MealPushDelivery).where(MealPushDelivery.completed_at.is_(None), MealPushDelivery.next_attempt_at <= now())
            .order_by(MealPushDelivery.id).with_for_update(skip_locked=True).limit(1))
        if delivery is None:
            return False
        installation = db.get(PushInstallation, delivery.installation_id)
        meal = db.get(Meal, delivery.meal_id)
        session = db.get(BrowserSession, installation.session_id) if installation else None
        user = db.get(User, installation.user_id) if installation else None
        valid = (installation and installation.enabled and installation.binding == delivery.binding and meal and not meal.deleted_at
                 and installation.user_id == meal.user_id and session and not session.revoked_at
                 and session.expires_at.replace(tzinfo=timezone.utc) > now() and user and user.is_active and not user.must_change_password
                 and delivery.created_at.replace(tzinfo=timezone.utc) > now() - timedelta(days=1))
        if not valid:
            delivery.completed_at = now()
        else:
            try:
                sender(installation, delivery)
                delivery.completed_at = now()
            except InvalidToken:
                installation.enabled = False
                delivery.completed_at = now()
            except Exception:
                # Never log tokens, payloads, credentials, or provider response bodies.
                delivery.attempts += 1
                delivery.next_attempt_at = now() + timedelta(seconds=min(900, 2 ** min(delivery.attempts, 10)))
                logger.warning("Push delivery retry scheduled, event=%s", delivery.id)
        db.commit()
        return True


async def run_sender():
    while True:
        try:
            active = bool(os.getenv("FCM_PROJECT_ID")) and await asyncio.to_thread(drain_one)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.warning("Push sender temporarily unavailable")
            active = False
        await asyncio.sleep(0.1 if active else 1)
