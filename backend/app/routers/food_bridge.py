"""Opt-in, account-bound nutrition grant. Never accepts a personal ingest token."""
import base64
import hashlib
import json
import os
import time
from pathlib import Path
from uuid import UUID

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import BigInteger, String, delete
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Mapped, Session, mapped_column

from app.config import Settings, get_settings
from app.database import Base, get_db
from app.models import User
from app.nutrition_days import nutrition_timezone_for
from app.routers import nutrition_user as nutrition
from app.schemas import MealCreate, MealOut, MealUpdate

ISSUER = "appgprj_6ae63356d64c8191acdaa074ac3a09ce"
ORIGIN = "https://healthconnect.urielmz.com"
PREFIX = "/api/v1/integrations/ido-food-journal"
FIELDS = {"clientMealId", "deviceId", "eatenAt", "timezone", "mealType", "title", "notes", "caloriesKcal", "proteinG", "carbsG", "fatG", "fiberG"}
router = APIRouter(prefix=PREFIX, tags=["food-journal-bridge"])


class BridgeNonce(Base):
    __tablename__ = "food_bridge_nonces"
    nonce: Mapped[str] = mapped_column(String(36), primary_key=True)
    expires_at: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)


def decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


async def principal(request: Request, db: Session = Depends(get_db)) -> User:
    # No grant file means disabled. Re-read for immediate revocation on every request.
    try:
        grant = json.loads(Path(os.environ["FOOD_BRIDGE_GRANT_FILE"]).read_text())
        if grant.get("enabled") is not True:
            raise ValueError()
    except Exception:
        raise HTTPException(503, "Nutrition bridge disabled") from None
    try:
        authorization = request.headers.get("authorization", "")
        if not authorization.startswith("Bearer ") or len(authorization) > 8192 or request.url.query:
            raise ValueError()
        header64, claims64, signature64 = authorization[7:].split(".")
        header = json.loads(decode(header64))
        if header != {"alg": "PS256", "typ": "JWT", "kid": grant["key_id"]}:
            raise ValueError()
        key = serialization.load_pem_public_key(grant["public_key_pem"].encode())
        if not isinstance(key, rsa.RSAPublicKey) or key.key_size < 2048:
            raise ValueError()
        key.verify(decode(signature64), (header64 + "." + claims64).encode(), padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=32), hashes.SHA256())
        claims = json.loads(decode(claims64))
        now = int(time.time())
        if any(type(claims.get(k)) is not int for k in ["iat", "exp"]):
            raise ValueError()
        if not (now - 60 <= claims["iat"] <= now + 5 and now < claims["exp"] <= claims["iat"] + 60):
            raise ValueError()
        if (claims.get("iss"), claims.get("aud"), claims.get("sub")) != (ISSUER, ORIGIN, grant["sites_owner_id"]):
            raise ValueError()
        raw_path = request.scope.get("raw_path", request.url.path.encode()).decode("ascii")
        if claims.get("htm") != request.method or claims.get("htu") != ORIGIN + raw_path:
            raise ValueError()
        raw = await request.body()
        if len(raw) > 65536 or claims.get("body_sha256") != hashlib.sha256(raw).hexdigest():
            raise ValueError()
        nonce = str(UUID(claims["jti"]))
        if nonce != claims["jti"]:
            raise ValueError()
        user = db.get(User, grant["user_id"])
        if user is None or user.username_normalized != grant["username_normalized"] or not user.is_active or user.must_change_password:
            raise ValueError()
        if user.nutrition_reset_hour != 4 or nutrition_timezone_for(user, get_settings().app_timezone) != "Asia/Jerusalem":
            raise ValueError()
        device = "chatgpt-food-" + grant["sites_owner_id"]
        if request.method in {"POST", "PATCH"}:
            body = json.loads(raw)
            if not isinstance(body, dict) or set(body) - FIELDS or body.get("deviceId") != device or body.get("timezone") != "Asia/Jerusalem":
                raise ValueError()
            UUID(body["clientMealId"])
        request.state.bridge_device = device
    except Exception:
        raise HTTPException(401, "Invalid nutrition bridge authorization") from None
    # Persist before mutation. Replayed signatures cannot overwrite later corrections.
    try:
        db.execute(delete(BridgeNonce).where(BridgeNonce.expires_at < now - 60))
        db.add(BridgeNonce(nonce=nonce, expires_at=claims["exp"]))
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Signature already used; retry with a fresh signature") from None
    return user


@router.get("/status")
def bridge_status(user: User = Depends(principal)):
    return {"authenticated": True, "username": user.username, "scopes": ["nutrition:meals:create", "nutrition:meals:update:own-journal", "nutrition:meals:delete:own-journal"], "timezone": "Asia/Jerusalem", "dayStartsAt": "04:00"}


@router.post("/meals", response_model=MealOut)
def create(payload: MealCreate, request: Request, user: User = Depends(principal), db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return nutrition.create_meal(payload, db, user, settings)


def owned(meal_id: int, request: Request, user: User, db: Session):
    meal = nutrition._get_active_meal(db, user.id, meal_id)
    if meal.source_device_id != request.state.bridge_device:
        raise HTTPException(404, "Meal not found")
    return meal


@router.patch("/meals/{meal_id}", response_model=MealOut)
def update(meal_id: int, payload: MealUpdate, request: Request, user: User = Depends(principal), db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    meal = owned(meal_id, request, user, db)
    if payload.clientMealId != meal.client_meal_id:
        raise HTTPException(409, "Stable client meal ID required")
    return nutrition.update_meal(meal_id, payload, db, user, settings)


@router.delete("/meals/{meal_id}")
def remove(meal_id: int, request: Request, user: User = Depends(principal), db: Session = Depends(get_db)):
    owned(meal_id, request, user, db)
    return nutrition.delete_meal(meal_id, db, user)
