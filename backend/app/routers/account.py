import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.auth import (
    create_browser_session,
    get_current_user,
    hash_password,
    normalize_username,
    require_data_user,
    token_hash,
    validate_request_origin,
    verify_password,
)
from app.config import Settings, get_settings
from app.database import get_db
from app.models import BrowserSession, IngestToken, User
from app.nutrition_days import current_nutrition_date
from app.schemas import (
    IngestTokenCreated,
    IngestTokenStatus,
    LoginRequest,
    PasswordChangeRequest,
    UserOut,
    UserPreferencesUpdate,
    NutritionGoals,
)

router = APIRouter(prefix="/api/v1", tags=["account"])


def user_out(user: User, settings: Settings) -> UserOut:
    return UserOut(
        id=user.id,
        username=user.username,
        displayName=user.display_name,
        isAdmin=user.is_admin,
        isActive=user.is_active,
        mustChangePassword=user.must_change_password,
        nutritionResetHour=user.nutrition_reset_hour,
        nutritionGoals=NutritionGoals(
            caloriesKcal=user.nutrition_goal_calories_kcal,
            proteinG=user.nutrition_goal_protein_g,
            carbsG=user.nutrition_goal_carbs_g,
            fatG=user.nutrition_goal_fat_g,
            fiberG=user.nutrition_goal_fiber_g,
        ),
        serverTimezone=settings.app_timezone,
        currentNutritionDate=current_nutrition_date(user, settings.app_timezone),
    )


@router.post("/auth/login", response_model=UserOut)
def login(
    payload: LoginRequest,
    request: Request,
    response: Response,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> UserOut:
    validate_request_origin(request, settings)
    normalized = normalize_username(payload.username)
    user = db.scalar(select(User).where(User.username_normalized == normalized))
    if user is None or not user.is_active or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    raw_token, session = create_browser_session(db, user, settings)
    response.set_cookie(
        settings.session_cookie_name,
        raw_token,
        max_age=settings.session_days * 86400,
        expires=session.expires_at,
        httponly=True,
        secure=settings.session_cookie_secure,
        samesite="lax",
        path="/",
    )
    return user_out(user, settings)


@router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(
    request: Request,
    response: Response,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> None:
    validate_request_origin(request, settings)
    raw_token = request.cookies.get(settings.session_cookie_name)
    if raw_token:
        session = db.scalar(select(BrowserSession).where(BrowserSession.token_hash == token_hash(raw_token)))
        if session and session.revoked_at is None:
            session.revoked_at = datetime.now(timezone.utc)
            db.commit()
    response.delete_cookie(settings.session_cookie_name, path="/", secure=settings.session_cookie_secure, samesite="lax")


@router.get("/auth/me", response_model=UserOut)
def me(user: User = Depends(get_current_user), settings: Settings = Depends(get_settings)) -> UserOut:
    return user_out(user, settings)


@router.post("/auth/change-password", response_model=UserOut)
def change_password(
    payload: PasswordChangeRequest,
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings),
) -> UserOut:
    validate_request_origin(request, settings)
    if not verify_password(payload.currentPassword, user.password_hash):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Current password is incorrect")
    user.password_hash = hash_password(payload.newPassword)
    user.must_change_password = False
    current_hash = token_hash(request.cookies.get(settings.session_cookie_name, ""))
    now = datetime.now(timezone.utc)
    db.execute(
        update(BrowserSession)
        .where(BrowserSession.user_id == user.id, BrowserSession.token_hash != current_hash, BrowserSession.revoked_at.is_(None))
        .values(revoked_at=now)
    )
    db.commit()
    db.refresh(user)
    return user_out(user, settings)


@router.patch("/account/preferences", response_model=UserOut)
def update_preferences(
    payload: UserPreferencesUpdate,
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> UserOut:
    validate_request_origin(request, settings)
    if payload.nutritionResetHour is not None:
        user.nutrition_reset_hour = payload.nutritionResetHour
    if payload.nutritionGoals is not None:
        goals = payload.nutritionGoals
        user.nutrition_goal_calories_kcal = goals.caloriesKcal
        user.nutrition_goal_protein_g = goals.proteinG
        user.nutrition_goal_carbs_g = goals.carbsG
        user.nutrition_goal_fat_g = goals.fatG
        user.nutrition_goal_fiber_g = goals.fiberG
    db.commit()
    db.refresh(user)
    return user_out(user, settings)


@router.get("/account/ingest-token", response_model=IngestTokenStatus)
def ingest_token_status(db: Session = Depends(get_db), user: User = Depends(require_data_user)) -> IngestTokenStatus:
    token = db.scalar(
        select(IngestToken)
        .where(IngestToken.user_id == user.id, IngestToken.revoked_at.is_(None))
        .order_by(IngestToken.created_at.desc())
    )
    return IngestTokenStatus(
        active=token is not None,
        prefix=token.token_prefix if token else None,
        createdAt=token.created_at if token else None,
    )


@router.post("/account/ingest-token/rotate", response_model=IngestTokenCreated)
def rotate_ingest_token(
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> IngestTokenCreated:
    validate_request_origin(request, settings)
    now = datetime.now(timezone.utc)
    db.execute(
        update(IngestToken)
        .where(IngestToken.user_id == user.id, IngestToken.revoked_at.is_(None))
        .values(revoked_at=now)
    )
    raw_token = "hd_ing_" + secrets.token_urlsafe(32)
    created = IngestToken(
        user_id=user.id,
        token_hash=token_hash(raw_token),
        token_prefix=raw_token[:15],
        created_at=now,
    )
    db.add(created)
    db.commit()
    db.refresh(created)
    return IngestTokenCreated(active=True, prefix=created.token_prefix, createdAt=created.created_at, token=raw_token)


@router.delete("/account/ingest-token", status_code=status.HTTP_204_NO_CONTENT)
def revoke_ingest_token(
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> None:
    validate_request_origin(request, settings)
    db.execute(
        update(IngestToken)
        .where(IngestToken.user_id == user.id, IngestToken.revoked_at.is_(None))
        .values(revoked_at=datetime.now(timezone.utc))
    )
    db.commit()
