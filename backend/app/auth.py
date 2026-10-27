import hashlib
import re
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import Depends, HTTPException, Request, status
from pwdlib import PasswordHash
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.models import BrowserSession, IngestToken, User

_PASSWORD_HASH = PasswordHash.recommended()
_USERNAME_RE = re.compile(r"^[A-Za-z0-9._-]{3,64}$")


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def normalize_username(username: str) -> str:
    cleaned = username.strip()
    if not _USERNAME_RE.fullmatch(cleaned):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Username must be 3-64 characters using letters, numbers, dot, underscore, or hyphen",
        )
    return cleaned.casefold()


def validate_password(password: str) -> None:
    if len(password) < 12:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Password must be at least 12 characters")


def hash_password(password: str) -> str:
    validate_password(password)
    return _PASSWORD_HASH.hash(password)


def verify_password(password: str, password_hash: str) -> bool:
    if password_hash.startswith("!"):
        return False
    return _PASSWORD_HASH.verify(password, password_hash)


def create_browser_session(db: Session, user: User, settings: Settings) -> tuple[str, BrowserSession]:
    raw_token = secrets.token_urlsafe(32)
    now = datetime.now(timezone.utc)
    session = BrowserSession(
        user_id=user.id,
        token_hash=token_hash(raw_token),
        created_at=now,
        last_used_at=now,
        expires_at=now + timedelta(days=settings.session_days),
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return raw_token, session


def get_current_user(
    request: Request,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> User:
    raw_token = request.cookies.get(settings.session_cookie_name)
    if not raw_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
    now = datetime.now(timezone.utc)
    session = db.scalar(
        select(BrowserSession).where(
            BrowserSession.token_hash == token_hash(raw_token),
            BrowserSession.revoked_at.is_(None),
            BrowserSession.expires_at > now,
        )
    )
    if session is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session expired or invalid")
    user = db.get(User, session.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Account is disabled")
    session.last_used_at = now
    db.commit()
    return user


def require_data_user(user: User = Depends(get_current_user)) -> User:
    if user.must_change_password:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Password change required")
    return user


def require_admin_user(user: User = Depends(require_data_user)) -> User:
    if not user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Administrator access required")
    return user


def require_ingest_user(request: Request, db: Session = Depends(get_db)) -> User:
    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    ingest_token = db.scalar(
        select(IngestToken).where(
            IngestToken.token_hash == token_hash(token),
            IngestToken.revoked_at.is_(None),
        )
    )
    if ingest_token is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid bearer token")
    user = db.get(User, ingest_token.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Account is disabled")
    return user



def require_meal_write_user(
    request: Request,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> User:
    """Allow meal management from either ingest token or browser session."""
    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() == "bearer" and token:
        return require_ingest_user(request, db)
    validate_request_origin(request, settings)
    user = get_current_user(request, db, settings)
    if user.must_change_password:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Password change required")
    return user

def validate_request_origin(request: Request, settings: Settings) -> None:
    origin = request.headers.get("Origin")
    if origin and origin not in settings.cors_origins:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Origin not allowed")
