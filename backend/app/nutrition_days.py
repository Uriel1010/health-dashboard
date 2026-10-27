from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

from app.models import User


def nutrition_date_for(value: datetime, user: User, timezone_name: str) -> date:
    local = value.astimezone(ZoneInfo(timezone_name))
    assigned = local.date()
    if local.hour < user.nutrition_reset_hour:
        assigned -= timedelta(days=1)
    return assigned


def nutrition_day_bounds(day: date, user: User, timezone_name: str) -> tuple[datetime, datetime]:
    zone = ZoneInfo(timezone_name)
    start_local = datetime.combine(day, time(hour=user.nutrition_reset_hour), tzinfo=zone)
    end_local = datetime.combine(day + timedelta(days=1), time(hour=user.nutrition_reset_hour), tzinfo=zone)
    return start_local.astimezone(timezone.utc), end_local.astimezone(timezone.utc)


def current_nutrition_date(user: User, timezone_name: str, now: datetime | None = None) -> date:
    return nutrition_date_for(now or datetime.now(timezone.utc), user, timezone_name)
