from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import require_data_user, require_meal_write_user
from app.config import Settings, get_settings
from app.database import get_db
from app.models import Meal, User
from app.nutrition_days import current_nutrition_date, nutrition_date_for, nutrition_day_bounds
from app.nutrition_statistics import build_statistics, NutritionStatisticsResponse
from app.schemas import (
    MacroValues,
    MealCreate,
    MealListResponse,
    MealOut,
    MealTypeSummary,
    MealUpdate,
    NutritionSummaryResponse,
    NutritionTrendPoint,
    NutritionTrendsResponse,
)

router = APIRouter(prefix="/api/v1/nutrition", tags=["nutrition"])


@router.get("/statistics", response_model=NutritionStatisticsResponse)
def nutrition_statistics(
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> dict:
    current = current_nutrition_date(user, settings.app_timezone)
    meals = _meals_for_range(db, user, current - timedelta(days=30), current - timedelta(days=1), settings.app_timezone)
    return build_statistics(meals, user, settings.app_timezone, current)


@router.post("/meals", response_model=MealOut)
def create_meal(
    payload: MealCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_meal_write_user),
    settings: Settings = Depends(get_settings),
) -> MealOut:
    existing = _find_existing_client_meal(db, user.id, payload.deviceId, payload.clientMealId)
    if existing is not None:
        _apply_meal_payload(existing, payload)
        db.commit()
        db.refresh(existing)
        return _meal_out(existing, user, settings.app_timezone)
    meal = Meal(user_id=user.id)
    _apply_meal_payload(meal, payload)
    db.add(meal)
    db.commit()
    db.refresh(meal)
    return _meal_out(meal, user, settings.app_timezone)


@router.patch("/meals/{meal_id}", response_model=MealOut)
def update_meal(
    meal_id: int,
    payload: MealUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_meal_write_user),
    settings: Settings = Depends(get_settings),
) -> MealOut:
    meal = _get_active_meal(db, user.id, meal_id)
    _apply_meal_update(meal, payload)
    db.commit()
    db.refresh(meal)
    return _meal_out(meal, user, settings.app_timezone)


@router.delete("/meals/{meal_id}")
def delete_meal(
    meal_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(require_meal_write_user),
) -> dict[str, bool | int]:
    meal = _get_active_meal(db, user.id, meal_id)
    meal.deleted_at = datetime.now(timezone.utc)
    db.commit()
    return {"ok": True, "mealId": meal.id}


@router.get("/meals", response_model=MealListResponse)
def list_meals(
    date_: date | None = Query(default=None, alias="date"),
    from_: date | None = Query(default=None, alias="from"),
    to: date | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> MealListResponse:
    from_date, to_date = _date_range(date_, from_, to, user, settings.app_timezone)
    meals = _meals_for_range(db, user, from_date, to_date, settings.app_timezone)
    return MealListResponse(meals=[_meal_out(meal, user, settings.app_timezone) for meal in meals])


@router.get("/summary/daily", response_model=NutritionSummaryResponse)
def daily_summary(
    date_: date = Query(alias="date"),
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> NutritionSummaryResponse:
    return _summary(db, user, date_, date_, settings.app_timezone)


@router.get("/summary/range", response_model=NutritionSummaryResponse)
def range_summary(
    from_: date = Query(alias="from"),
    to: date = Query(),
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> NutritionSummaryResponse:
    if from_ > to:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be before or equal to to")
    return _summary(db, user, from_, to, settings.app_timezone)


@router.get("/trends", response_model=NutritionTrendsResponse)
def nutrition_trends(
    to: date | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
    settings: Settings = Depends(get_settings),
) -> NutritionTrendsResponse:
    end_date = to or current_nutrition_date(user, settings.app_timezone)
    visible_start = end_date - timedelta(days=29)
    warm_start = visible_start - timedelta(days=29)
    meals = _meals_for_range(db, user, warm_start, end_date, settings.app_timezone)
    totals = {warm_start + timedelta(days=i): _empty_macros() for i in range(59)}
    counts = {day: 0 for day in totals}
    for meal in meals:
        day = nutrition_date_for(meal.eaten_at, user, settings.app_timezone)
        if day in totals:
            counts[day] += 1
            values = totals[day]
            values["caloriesKcal"] += float(meal.calories_kcal)
            values["proteinG"] += float(meal.protein_g)
            values["carbsG"] += float(meal.carbs_g)
            values["fatG"] += float(meal.fat_g)

    ordered_days = sorted(totals)
    now_day = current_nutrition_date(user, settings.app_timezone)
    points: list[NutritionTrendPoint] = []
    for index, day in enumerate(ordered_days):
        if day < visible_start:
            continue
        points.append(
            NutritionTrendPoint(
                date=day,
                hasMeals=counts[day] > 0,
                isIncomplete=day == now_day,
                daily=MacroValues(**_rounded(totals[day])),
                average7=MacroValues(**_rolling_average(ordered_days, totals, index, 7)),
                average14=MacroValues(**_rolling_average(ordered_days, totals, index, 14)),
                average30=MacroValues(**_rolling_average(ordered_days, totals, index, 30)),
            )
        )
    return NutritionTrendsResponse(
        fromDate=visible_start,
        toDate=end_date,
        serverTimezone=settings.app_timezone,
        nutritionResetHour=user.nutrition_reset_hour,
        points=points,
    )


def _find_existing_client_meal(db: Session, user_id: int, device_id: str, client_meal_id: str | None) -> Meal | None:
    if not client_meal_id:
        return None
    return db.scalar(
        select(Meal).where(
            Meal.user_id == user_id,
            Meal.source_device_id == device_id,
            Meal.client_meal_id == client_meal_id,
        )
    )


def _get_active_meal(db: Session, user_id: int, meal_id: int) -> Meal:
    meal = db.scalar(select(Meal).where(Meal.id == meal_id, Meal.user_id == user_id, Meal.deleted_at.is_(None)))
    if meal is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Meal not found")
    return meal


def _meals_for_range(db: Session, user: User, from_date: date, to_date: date, timezone_name: str) -> list[Meal]:
    start, _ = nutrition_day_bounds(from_date, user, timezone_name)
    _, end = nutrition_day_bounds(to_date, user, timezone_name)
    return list(
        db.scalars(
            select(Meal)
            .where(
                Meal.user_id == user.id,
                Meal.deleted_at.is_(None),
                Meal.eaten_at >= start,
                Meal.eaten_at < end,
            )
            .order_by(Meal.eaten_at, Meal.id)
        ).all()
    )


def _date_range(
    date_value: date | None,
    from_date: date | None,
    to_date: date | None,
    user: User,
    timezone_name: str,
) -> tuple[date, date]:
    if date_value and (from_date or to_date):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Use either date or from/to, not both")
    if date_value:
        return date_value, date_value
    start = from_date or current_nutrition_date(user, timezone_name)
    end = to_date or start
    if start > end:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be before or equal to to")
    return start, end


def _summary(db: Session, user: User, from_date: date, to_date: date, timezone_name: str) -> NutritionSummaryResponse:
    meals = _meals_for_range(db, user, from_date, to_date, timezone_name)
    by_type: dict[str, list[Meal]] = {}
    for meal in meals:
        by_type.setdefault(meal.meal_type, []).append(meal)
    return NutritionSummaryResponse(
        fromDate=from_date,
        toDate=to_date,
        mealCount=len(meals),
        caloriesKcal=_sum(meals, "calories_kcal"),
        proteinG=_sum(meals, "protein_g"),
        carbsG=_sum(meals, "carbs_g"),
        fatG=_sum(meals, "fat_g"),
        fiberG=_sum(meals, "fiber_g"),
        sugarG=_sum(meals, "sugar_g"),
        sodiumMg=_sum(meals, "sodium_mg"),
        byMealType=[
            MealTypeSummary(
                mealType=meal_type,
                mealCount=len(group),
                caloriesKcal=_sum(group, "calories_kcal"),
                proteinG=_sum(group, "protein_g"),
                carbsG=_sum(group, "carbs_g"),
                fatG=_sum(group, "fat_g"),
                fiberG=_sum(group, "fiber_g"),
                sugarG=_sum(group, "sugar_g"),
                sodiumMg=_sum(group, "sodium_mg"),
            )
            for meal_type, group in sorted(by_type.items())
        ],
    )


def _apply_meal_payload(meal: Meal, payload: MealCreate) -> None:
    eaten_at, local_date = _normalize_eaten_at(payload.eatenAt, payload.timezone)
    meal.client_meal_id = _clean_optional(payload.clientMealId)
    meal.source_device_id = payload.deviceId.strip()
    meal.eaten_at = eaten_at
    meal.local_date = local_date
    meal.timezone = payload.timezone.strip() or "Asia/Jerusalem"
    meal.meal_type = payload.mealType
    meal.title = payload.title.strip()
    meal.notes = _clean_optional(payload.notes)
    meal.calories_kcal = _decimal(payload.caloriesKcal)
    meal.protein_g = _decimal(payload.proteinG)
    meal.carbs_g = _decimal(payload.carbsG)
    meal.fat_g = _decimal(payload.fatG)
    meal.fiber_g = _decimal_optional(payload.fiberG)
    meal.sugar_g = _decimal_optional(payload.sugarG)
    meal.sodium_mg = _decimal_optional(payload.sodiumMg)
    meal.deleted_at = None


def _apply_meal_update(meal: Meal, payload: MealUpdate) -> None:
    fields = payload.model_dump(exclude_unset=True)
    timezone_name = fields.get("timezone", meal.timezone)
    if "eatenAt" in fields or "timezone" in fields:
        meal.eaten_at, meal.local_date = _normalize_eaten_at(fields.get("eatenAt", meal.eaten_at), timezone_name)
        meal.timezone = timezone_name.strip() or "Asia/Jerusalem"
    mappings = {
        "clientMealId": ("client_meal_id", _clean_optional),
        "deviceId": ("source_device_id", lambda value: value.strip()),
        "mealType": ("meal_type", lambda value: value),
        "title": ("title", lambda value: value.strip()),
        "notes": ("notes", _clean_optional),
        "caloriesKcal": ("calories_kcal", _decimal),
        "proteinG": ("protein_g", _decimal),
        "carbsG": ("carbs_g", _decimal),
        "fatG": ("fat_g", _decimal),
        "fiberG": ("fiber_g", _decimal_optional),
        "sugarG": ("sugar_g", _decimal_optional),
        "sodiumMg": ("sodium_mg", _decimal_optional),
    }
    for input_name, (attribute, converter) in mappings.items():
        if input_name in fields:
            setattr(meal, attribute, converter(fields[input_name]))


def _normalize_eaten_at(value: datetime, timezone_name: str) -> tuple[datetime, date]:
    timezone_name = timezone_name.strip() or "Asia/Jerusalem"
    try:
        zone = ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="timezone must be a valid IANA timezone") from exc
    if value.tzinfo is None:
        value = value.replace(tzinfo=zone)
    return value, value.astimezone(zone).date()


def _meal_out(meal: Meal, user: User, timezone_name: str) -> MealOut:
    return MealOut(
        id=meal.id,
        clientMealId=meal.client_meal_id,
        deviceId=meal.source_device_id,
        eatenAt=meal.eaten_at,
        localDate=meal.local_date,
        nutritionDate=nutrition_date_for(meal.eaten_at, user, timezone_name),
        timezone=meal.timezone,
        mealType=meal.meal_type,
        title=meal.title,
        notes=meal.notes,
        caloriesKcal=float(meal.calories_kcal),
        proteinG=float(meal.protein_g),
        carbsG=float(meal.carbs_g),
        fatG=float(meal.fat_g),
        fiberG=float(meal.fiber_g) if meal.fiber_g is not None else None,
        sugarG=float(meal.sugar_g) if meal.sugar_g is not None else None,
        sodiumMg=float(meal.sodium_mg) if meal.sodium_mg is not None else None,
        createdAt=meal.created_at,
        updatedAt=meal.updated_at,
    )


def _sum(meals: list[Meal], field: str) -> float:
    return float(sum((getattr(meal, field) or Decimal("0") for meal in meals), Decimal("0")))


def _empty_macros() -> dict[str, float]:
    return {"caloriesKcal": 0.0, "proteinG": 0.0, "carbsG": 0.0, "fatG": 0.0}


def _rounded(values: dict[str, float]) -> dict[str, float]:
    return {key: round(value, 2) for key, value in values.items()}


def _rolling_average(
    ordered_days: list[date],
    totals: dict[date, dict[str, float]],
    index: int,
    window: int,
) -> dict[str, float]:
    selected = ordered_days[index - window + 1 : index + 1]
    return {
        key: round(sum(totals[day][key] for day in selected) / window, 2)
        for key in _empty_macros()
    }


def _decimal(value: float | int | Decimal) -> Decimal:
    return Decimal(str(value))


def _decimal_optional(value: float | int | Decimal | None) -> Decimal | None:
    return None if value is None else _decimal(value)


def _clean_optional(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.strip()
    return cleaned or None
