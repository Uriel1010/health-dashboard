import unittest
from datetime import date, datetime
from zoneinfo import ZoneInfo

from app.models import User
from app.nutrition_days import current_nutrition_date, nutrition_date_for, nutrition_day_bounds


class NutritionDayTests(unittest.TestCase):
    def setUp(self) -> None:
        self.user = User(
            id=1,
            username="test",
            username_normalized="test",
            display_name="Test",
            password_hash="unused",
            is_admin=False,
            is_active=True,
            must_change_password=False,
            nutrition_reset_hour=4,
        )

    def test_before_reset_belongs_to_previous_day(self) -> None:
        value = datetime(2026, 8, 20, 3, 59, tzinfo=ZoneInfo("Asia/Jerusalem"))
        self.assertEqual(nutrition_date_for(value, self.user, "Asia/Jerusalem"), date(2026, 8, 19))

    def test_reset_hour_starts_new_day(self) -> None:
        value = datetime(2026, 8, 20, 4, 0, tzinfo=ZoneInfo("Asia/Jerusalem"))
        self.assertEqual(nutrition_date_for(value, self.user, "Asia/Jerusalem"), date(2026, 8, 20))

    def test_midnight_reset_matches_calendar_day(self) -> None:
        self.user.nutrition_reset_hour = 0
        value = datetime(2026, 8, 20, 0, 1, tzinfo=ZoneInfo("Asia/Jerusalem"))
        self.assertEqual(nutrition_date_for(value, self.user, "Asia/Jerusalem"), date(2026, 8, 20))

    def test_bounds_span_adjacent_reset_boundaries(self) -> None:
        start, end = nutrition_day_bounds(date(2026, 8, 20), self.user, "Asia/Jerusalem")
        self.assertLess(start, end)
        self.assertEqual(nutrition_date_for(start, self.user, "Asia/Jerusalem"), date(2026, 8, 20))

    def test_current_date_uses_reset_boundary(self) -> None:
        now = datetime(2026, 8, 20, 2, 0, tzinfo=ZoneInfo("Asia/Jerusalem"))
        self.assertEqual(current_nutrition_date(self.user, "Asia/Jerusalem", now), date(2026, 8, 19))


if __name__ == "__main__":
    unittest.main()
