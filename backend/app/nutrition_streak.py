"""Consecutive logged nutrition days, independent of chart display windows."""
from datetime import date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Meal, User
from app.nutrition_days import nutrition_date_for, nutrition_day_bounds


def streak_from_timestamps(timestamps, user: User, timezone_name: str, reference_day: date) -> int:
    """Consume newest first; allow today to remain unlogged until its boundary."""
    expected = reference_day
    count = 0
    for timestamp in timestamps:
        day = nutrition_date_for(timestamp, user, timezone_name)
        if day > reference_day:
            continue
        if count == 0 and day == reference_day - timedelta(days=1):
            expected = day
        if day > expected:
            continue  # More than one meal on an already counted day.
        if day < expected:
            break
        count += 1
        expected -= timedelta(days=1)
    return count


def current_streak_days(db: Session, user: User, timezone_name: str, reference_day: date) -> int:
    _, end = nutrition_day_bounds(reference_day, user, timezone_name)
    statement = (
        select(Meal.eaten_at)
        .where(Meal.user_id == user.id, Meal.deleted_at.is_(None), Meal.eaten_at < end)
        .order_by(Meal.eaten_at.desc())
        .execution_options(yield_per=256)
    )
    # No fixed lookback limit. Stop at the first actual missing day, closing the
    # streaming cursor so long histories do not need to be loaded in memory.
    with db.execute(statement) as rows:
        return streak_from_timestamps((row[0] for row in rows), user, timezone_name, reference_day)
