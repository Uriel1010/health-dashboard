import os
os.environ["DATABASE_URL"] = "sqlite://"

import unittest
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import Meal, User
from app.nutrition_streak import current_streak_days, streak_from_timestamps
from app.routers.nutrition_user import create_meal, nutrition_trends
from app.schemas import MealCreate


class NutritionStreakTests(unittest.TestCase):
    def setUp(self):
        self.user = User(id=1, username="test", username_normalized="test", display_name="Test", password_hash="!test", nutrition_reset_hour=4, nutrition_timezone="Asia/Jerusalem")
        self.today = date(2026, 11, 1)

    def timestamps(self, offsets):
        return [datetime.combine(self.today-timedelta(days=n), datetime.min.time().replace(hour=12), ZoneInfo("Asia/Jerusalem")) for n in offsets]

    def count(self, offsets):
        return streak_from_timestamps(self.timestamps(offsets), self.user, "UTC", self.today)

    def test_uncapped_streak_and_duplicate_meals(self):
        self.assertEqual(self.count([n for n in range(90) for _ in range(3)]), 90)

    def test_unfinished_today_preserves_yesterdays_streak(self):
        self.assertEqual(self.count(range(1, 46)), 45)

    def test_actual_gap_breaks_streak(self):
        self.assertEqual(self.count([0, 1, 2, 4, 5]), 3)
        self.assertEqual(self.count([2, 3, 4]), 0)
        self.assertEqual(self.count([]), 0)

    def test_future_days_do_not_inflate_streak(self):
        self.assertEqual(self.count([-2, -1, 0, 1]), 2)

    def test_four_am_boundary_and_personal_timezone(self):
        values = [datetime(2026,11,1,3,59,tzinfo=ZoneInfo("Asia/Jerusalem")), datetime(2026,10,31,3,59,tzinfo=ZoneInfo("Asia/Jerusalem"))]
        self.assertEqual(streak_from_timestamps(values,self.user,"UTC",self.today),2)

    def test_database_user_isolation_deletion_and_chart_window(self):
        engine = create_engine("sqlite://")
        User.__table__.create(engine)
        Meal.__table__.create(engine)
        settings = Settings(APP_TIMEZONE="UTC")
        with Session(engine) as db:
            db.add(self.user)
            other = User(id=2,username="other",username_normalized="other",display_name="Other",password_hash="!test",nutrition_reset_hour=4,nutrition_timezone="Asia/Jerusalem")
            db.add(other)
            db.commit()
            for i,timestamp in enumerate(self.timestamps(range(45))):
                create_meal(MealCreate(clientMealId=f"fixture-{i}",deviceId="isolated",eatenAt=timestamp,timezone="Asia/Jerusalem",mealType="lunch",title="fixture",caloriesKcal=100,proteinG=10,carbsG=10,fatG=2),db,self.user,settings)
            self.assertEqual(current_streak_days(db,other,"UTC",self.today),0)
            response = nutrition_trends(to=self.today,db=db,user=self.user,settings=settings)
            self.assertEqual(response.currentStreakDays,45)
            self.assertEqual(len(response.points),30)
            self.assertEqual(response.serverTimezone,"Asia/Jerusalem")
            # Removing all own meals on a day breaks the streak even when another
            # user has logged that day. Restore no production data in this test.
            meal = db.query(Meal).filter(Meal.client_meal_id=="fixture-2").one()
            meal.deleted_at=datetime.now(ZoneInfo("UTC"))
            db.commit()
            create_meal(MealCreate(clientMealId="other-gap",deviceId="isolated",eatenAt=self.timestamps([2])[0],timezone="Asia/Jerusalem",mealType="lunch",title="other fixture",caloriesKcal=100,proteinG=10,carbsG=10,fatG=2),db,other,settings)
            self.assertEqual(current_streak_days(db,self.user,"UTC",self.today),2)
        engine.dispose()


if __name__ == "__main__":
    unittest.main()
