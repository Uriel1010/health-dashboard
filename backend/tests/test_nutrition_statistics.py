import unittest
from datetime import date, datetime
from types import SimpleNamespace
from zoneinfo import ZoneInfo

from app.nutrition_statistics import build_statistics, NutritionStatisticsResponse


class StatisticsTests(unittest.TestCase):
    def setUp(self):
        self.user = SimpleNamespace(nutrition_reset_hour=4)

    def meal(self, day, calories=2000, fiber=25):
        return SimpleNamespace(eaten_at=datetime(2026, 10, day, 12, tzinfo=ZoneInfo("Asia/Jerusalem")),
                               calories_kcal=calories, protein_g=100, carbs_g=200, fat_g=60, fiber_g=fiber)

    def result(self, meals):
        return build_statistics(meals, self.user, "Asia/Jerusalem", date(2026, 10, 27))

    def test_current_day_and_outside_window_excluded(self):
        result = self.result([self.meal(26), self.meal(27, 9000)])
        self.assertEqual(result["toDate"], date(2026, 10, 26))
        self.assertEqual(result["fromDate"], date(2026, 9, 27))
        self.assertEqual(len(result["points"]), 30)
        self.assertEqual(result["windows"][0]["average"]["caloriesKcal"], 2000)

    def test_missing_days_excluded_but_registered_zero_included(self):
        result = self.result([self.meal(25, 0), self.meal(26, 2000)])
        window = result["windows"][0]
        self.assertEqual(window["loggedDays"], 2)
        self.assertEqual(window["average"]["caloriesKcal"], 1000)
        self.assertEqual(window["coverage"]["caloriesKcal"], 2)

    def test_windows_have_exact_calendar_boundaries(self):
        result = self.result([self.meal(10, 1000), self.meal(18, 2000), self.meal(26, 3000)])
        self.assertEqual([w["average"]["caloriesKcal"] for w in result["windows"]], [3000, 2500, 2000])

    def test_empty_history_is_unknown_not_zero(self):
        for window in self.result([])["windows"]:
            self.assertIsNone(window["average"]["caloriesKcal"])
            self.assertEqual(window["loggedDays"], 0)

    def test_unknown_fiber_does_not_lower_average(self):
        window = self.result([self.meal(25, fiber=None), self.meal(26, fiber=30)])["windows"][0]
        self.assertEqual(window["coverage"]["fiberG"], 1)
        self.assertEqual(window["average"]["fiberG"], 30)
        self.assertEqual(window["coverage"]["proteinG"], 2)

    def test_reset_hour_and_multiple_meals_aggregate(self):
        meal = self.meal(27, 1000)
        meal.eaten_at = meal.eaten_at.replace(hour=3)
        window = self.result([meal, self.meal(26, 1000)])["windows"][0]
        self.assertEqual(window["loggedDays"], 1)
        self.assertEqual(window["average"]["caloriesKcal"], 2000)

    def test_oldest_boundary_included_and_older_meals_excluded(self):
        oldest = self.meal(27, 1500)
        oldest.eaten_at = oldest.eaten_at.replace(month=9)
        older = self.meal(26, 9000)
        older.eaten_at = older.eaten_at.replace(month=9)
        window = self.result([oldest, older])["windows"][2]
        self.assertEqual(window["loggedDays"], 1)
        self.assertEqual(window["average"]["caloriesKcal"], 1500)

    def test_partially_unknown_fiber_day_is_not_a_complete_fiber_total(self):
        window = self.result([self.meal(26, fiber=None), self.meal(26, fiber=12)])["windows"][0]
        self.assertEqual(window["coverage"]["fiberG"], 0)
        self.assertIsNone(window["average"]["fiberG"])
        self.assertEqual(window["average"]["caloriesKcal"], 4000)

    def test_response_contract_serializes_dates_nulls_and_coverage(self):
        data = NutritionStatisticsResponse.model_validate(self.result([])).model_dump(mode="json")
        self.assertEqual(data["toDate"], "2026-10-26")
        self.assertEqual([window["days"] for window in data["windows"]], [7, 14, 30])
        self.assertIsNone(data["windows"][0]["average"]["fiberG"])
        self.assertEqual(data["windows"][0]["coverage"]["fiberG"], 0)
