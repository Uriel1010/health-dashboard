import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth import hash_password, normalize_username, require_admin_user, validate_request_origin
from app.config import Settings, get_settings
from app.database import get_db
from app.models import BrowserSession, User
from app.routers.account import user_out
from app.schemas import AdminUserCreate, AdminUserCreated, AdminUserUpdate, PasswordResetResponse, UserOut

router = APIRouter(prefix="/api/v1/admin/users", tags=["user administration"])


@router.get("", response_model=list[UserOut])
def list_users(
    db: Session = Depends(get_db),
    _: User = Depends(require_admin_user),
    settings: Settings = Depends(get_settings),
) -> list[UserOut]:
    users = db.scalars(select(User).order_by(User.is_admin.desc(), User.username_normalized)).all()
    return [user_out(user, settings) for user in users]


@router.post("", response_model=AdminUserCreated, status_code=status.HTTP_201_CREATED)
def create_user(
    payload: AdminUserCreate,
    request: Request,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin_user),
    settings: Settings = Depends(get_settings),
) -> AdminUserCreated:
    validate_request_origin(request, settings)
    normalized = normalize_username(payload.username)
    display_name = payload.displayName.strip()
    if not display_name:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Display name is required")
    temporary_password = secrets.token_urlsafe(12)
    created = User(
        username=payload.username.strip(),
        username_normalized=normalized,
        display_name=display_name,
        password_hash=hash_password(temporary_password),
        is_admin=False,
        is_active=True,
        must_change_password=True,
        nutrition_reset_hour=0,
    )
    db.add(created)
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already exists") from exc
    db.refresh(created)
    return AdminUserCreated(user=user_out(created, settings), temporaryPassword=temporary_password)


def regular_user_or_404(db: Session, user_id: int) -> User:
    target = db.get(User, user_id)
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    if target.is_admin:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="The sole administrator cannot be changed")
    return target


@router.patch("/{user_id}", response_model=UserOut)
def update_user(
    user_id: int,
    payload: AdminUserUpdate,
    request: Request,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin_user),
    settings: Settings = Depends(get_settings),
) -> UserOut:
    validate_request_origin(request, settings)
    target = regular_user_or_404(db, user_id)
    fields = payload.model_dump(exclude_unset=True)
    if "displayName" in fields:
        cleaned = fields["displayName"].strip()
        if not cleaned:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Display name is required")
        target.display_name = cleaned
    if "isActive" in fields:
        target.is_active = fields["isActive"]
        if not target.is_active:
            db.execute(
                update(BrowserSession)
                .where(BrowserSession.user_id == target.id, BrowserSession.revoked_at.is_(None))
                .values(revoked_at=datetime.now(timezone.utc))
            )
    db.commit()
    db.refresh(target)
    return user_out(target, settings)


@router.post("/{user_id}/reset-password", response_model=PasswordResetResponse)
def reset_password(
    user_id: int,
    request: Request,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin_user),
    settings: Settings = Depends(get_settings),
) -> PasswordResetResponse:
    validate_request_origin(request, settings)
    target = regular_user_or_404(db, user_id)
    temporary_password = secrets.token_urlsafe(12)
    target.password_hash = hash_password(temporary_password)
    target.must_change_password = True
    db.execute(
        update(BrowserSession)
        .where(BrowserSession.user_id == target.id, BrowserSession.revoked_at.is_(None))
        .values(revoked_at=datetime.now(timezone.utc))
    )
    db.commit()
    db.refresh(target)
    return PasswordResetResponse(user=user_out(target, settings), temporaryPassword=temporary_password)
