"""Completed nutrition-day statistics. Missing registration is never zero intake."""
from datetime import date, timedelta
from typing import Literal

from pydantic import BaseModel, Field

from app.nutrition_days import nutrition_date_for

MACROS = ("caloriesKcal", "proteinG", "carbsG", "fatG", "fiberG")
FIELDS = ("calories_kcal", "protein_g", "carbs_g", "fat_g", "fiber_g")


class StatisticsValues(BaseModel):
    caloriesKcal: float | None
    proteinG: float | None
    carbsG: float | None
    fatG: float | None
    fiberG: float | None


class StatisticsPoint(BaseModel):
    date: date
    hasMeals: bool
    totals: StatisticsValues


class StatisticsWindow(BaseModel):
    days: Literal[7, 14, 30]
    fromDate: date
    toDate: date
    loggedDays: int = Field(ge=0, le=30)
    average: StatisticsValues
    coverage: dict[str, int]


class NutritionStatisticsResponse(BaseModel):
    fromDate: date
    toDate: date
    currentNutritionDate: date
    serverTimezone: str
    nutritionResetHour: int = Field(ge=0, le=23)
    points: list[StatisticsPoint]
    windows: list[StatisticsWindow]


def build_statistics(meals, user, timezone_name, current_day):
    end = current_day - timedelta(days=1)
    start = current_day - timedelta(days=30)
    days = {start + timedelta(days=i): [] for i in range(30)}
    for meal in meals:
        day = nutrition_date_for(meal.eaten_at, user, timezone_name)
        if day in days:
            days[day].append(meal)
    points = []
    for day, entries in days.items():
        values = {}
        for key, field in zip(MACROS, FIELDS):
            known = entries and all(getattr(meal, field) is not None for meal in entries)
            values[key] = round(sum(float(getattr(meal, field)) for meal in entries), 2) if known else None
        points.append(dict(date=day, hasMeals=bool(entries), totals=values))
    windows = []
    for length in (7, 14, 30):
        selected = points[-length:]
        averages = {}
        coverage = {}
        for key in MACROS:
            values = [point["totals"][key] for point in selected if point["totals"][key] is not None]
            coverage[key] = len(values)
            averages[key] = round(sum(values) / len(values), 2) if values else None
        windows.append(dict(days=length, fromDate=end - timedelta(days=length - 1), toDate=end,
                            loggedDays=sum(point["hasMeals"] for point in selected),
                            average=averages, coverage=coverage))
    return dict(fromDate=start, toDate=end, currentNutritionDate=current_day,
                serverTimezone=timezone_name, nutritionResetHour=user.nutrition_reset_hour,
                points=points, windows=windows)
