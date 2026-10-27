from datetime import date, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class DeviceOut(BaseModel):
    deviceId: str
    deviceName: str | None
    firstSeenAt: datetime | None = None
    lastSeenAt: datetime | None = None


class SnapshotOut(BaseModel):
    id: int
    capturedAt: datetime
    receivedAt: datetime
    timezone: str | None
    stepsToday: int | None
    distanceKmToday: float | None
    activeCaloriesKcalToday: int | None
    totalCaloriesKcalToday: int | None
    latestHeartRateBpm: int | None
    restingHeartRateBpm: int | None
    sleepMinutesLastNight: int | None
    latestWeightKg: float | None
    recentExercises: Any | None = None
    permissions: Any | None = None
    errors: Any | None = None


class LatestSnapshotResponse(BaseModel):
    device: DeviceOut
    snapshot: SnapshotOut | None


class RecentSnapshotsResponse(BaseModel):
    device: DeviceOut
    snapshots: list[SnapshotOut]


class SnapshotDetailResponse(BaseModel):
    device: DeviceOut
    snapshot: SnapshotOut
    rawPayload: Any


class HistoryPoint(BaseModel):
    capturedAt: datetime
    receivedAt: datetime
    value: float | int | None


class HistoryResponse(BaseModel):
    deviceId: str
    metric: str
    points: list[HistoryPoint]


class IngestResponse(BaseModel):
    ok: bool
    snapshotId: int
    receivedAt: datetime


class HealthResponse(BaseModel):
    status: str
    database: str
    timestamp: datetime


class AdminDeviceStats(BaseModel):
    deviceId: str
    deviceName: str | None
    snapshotCount: int
    firstSnapshotAt: datetime | None = None
    lastSnapshotAt: datetime | None = None


class AdminStatsResponse(BaseModel):
    deviceCount: int
    snapshotCount: int
    oldestSnapshotAt: datetime | None = None
    newestSnapshotAt: datetime | None = None
    devices: list[AdminDeviceStats]


class AdminCleanupResponse(BaseModel):
    action: str
    dryRun: bool
    criteria: dict[str, Any]
    matchedDevices: int
    matchedSnapshots: int
    deletedDevices: int
    deletedSnapshots: int
    before: AdminStatsResponse
    after: AdminStatsResponse


MealType = Literal["breakfast", "lunch", "dinner", "snack", "other"]


class MealBase(BaseModel):
    clientMealId: str | None = Field(default=None, max_length=256)
    deviceId: str = Field(min_length=1, max_length=512)
    eatenAt: datetime
    timezone: str = Field(default="Asia/Jerusalem", min_length=1, max_length=128)
    mealType: MealType
    title: str = Field(min_length=1, max_length=512)
    notes: str | None = Field(default=None, max_length=4000)
    caloriesKcal: float = Field(default=0, ge=0, le=10000)
    proteinG: float = Field(default=0, ge=0, le=2000)
    carbsG: float = Field(default=0, ge=0, le=2000)
    fatG: float = Field(default=0, ge=0, le=2000)
    fiberG: float | None = Field(default=None, ge=0, le=500)
    sugarG: float | None = Field(default=None, ge=0, le=1000)
    sodiumMg: float | None = Field(default=None, ge=0, le=100000)

    @model_validator(mode="after")
    def require_title(self) -> "MealBase":
        if not self.title.strip():
            raise ValueError("title is required")
        return self


class MealCreate(MealBase):
    pass


class MealUpdate(BaseModel):
    clientMealId: str | None = Field(default=None, max_length=256)
    deviceId: str | None = Field(default=None, min_length=1, max_length=512)
    eatenAt: datetime | None = None
    timezone: str | None = Field(default=None, min_length=1, max_length=128)
    mealType: MealType | None = None
    title: str | None = Field(default=None, min_length=1, max_length=512)
    notes: str | None = Field(default=None, max_length=4000)
    caloriesKcal: float | None = Field(default=None, ge=0, le=10000)
    proteinG: float | None = Field(default=None, ge=0, le=2000)
    carbsG: float | None = Field(default=None, ge=0, le=2000)
    fatG: float | None = Field(default=None, ge=0, le=2000)
    fiberG: float | None = Field(default=None, ge=0, le=500)
    sugarG: float | None = Field(default=None, ge=0, le=1000)
    sodiumMg: float | None = Field(default=None, ge=0, le=100000)


class MealOut(BaseModel):
    id: int
    clientMealId: str | None = None
    deviceId: str
    eatenAt: datetime
    localDate: date
    nutritionDate: date
    timezone: str
    mealType: str
    title: str
    notes: str | None = None
    caloriesKcal: float
    proteinG: float
    carbsG: float
    fatG: float
    fiberG: float | None = None
    sugarG: float | None = None
    sodiumMg: float | None = None
    createdAt: datetime
    updatedAt: datetime


class MealListResponse(BaseModel):
    meals: list[MealOut]


class MealTypeSummary(BaseModel):
    mealType: str
    mealCount: int
    caloriesKcal: float
    proteinG: float
    carbsG: float
    fatG: float
    fiberG: float
    sugarG: float
    sodiumMg: float


class NutritionSummaryResponse(BaseModel):
    fromDate: date
    toDate: date
    mealCount: int
    caloriesKcal: float
    proteinG: float
    carbsG: float
    fatG: float
    fiberG: float
    sugarG: float
    sodiumMg: float
    byMealType: list[MealTypeSummary]


class OrmModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)



class LoginRequest(BaseModel):
    username: str
    password: str


class PasswordChangeRequest(BaseModel):
    currentPassword: str
    newPassword: str


class NutritionGoals(BaseModel):
    caloriesKcal: int = Field(ge=0, le=10000)
    proteinG: int = Field(ge=0, le=2000)
    carbsG: int = Field(ge=0, le=2000)
    fatG: int = Field(ge=0, le=2000)
    fiberG: int = Field(ge=0, le=500)


class UserPreferencesUpdate(BaseModel):
    nutritionResetHour: int | None = Field(default=None, ge=0, le=23)
    nutritionGoals: NutritionGoals | None = None

    @model_validator(mode="after")
    def require_preference(self) -> "UserPreferencesUpdate":
        if self.nutritionResetHour is None and self.nutritionGoals is None:
            raise ValueError("at least one preference is required")
        return self


class UserOut(BaseModel):
    id: int
    username: str
    displayName: str
    isAdmin: bool
    isActive: bool
    mustChangePassword: bool
    nutritionResetHour: int
    nutritionGoals: NutritionGoals
    serverTimezone: str
    currentNutritionDate: date


class AdminUserCreate(BaseModel):
    username: str
    displayName: str = Field(min_length=1, max_length=100)


class AdminUserUpdate(BaseModel):
    displayName: str | None = Field(default=None, min_length=1, max_length=100)
    isActive: bool | None = None


class AdminUserCreated(BaseModel):
    user: UserOut
    temporaryPassword: str


class PasswordResetResponse(BaseModel):
    user: UserOut
    temporaryPassword: str


class IngestTokenStatus(BaseModel):
    active: bool
    prefix: str | None = None
    createdAt: datetime | None = None


class IngestTokenCreated(IngestTokenStatus):
    token: str


class MacroValues(BaseModel):
    caloriesKcal: float
    proteinG: float
    carbsG: float
    fatG: float


class NutritionTrendPoint(BaseModel):
    date: date
    hasMeals: bool
    isIncomplete: bool
    daily: MacroValues
    average7: MacroValues
    average14: MacroValues
    average30: MacroValues


class NutritionTrendsResponse(BaseModel):
    fromDate: date
    toDate: date
    serverTimezone: str
    nutritionResetHour: int
    points: list[NutritionTrendPoint]
