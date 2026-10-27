export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

export type Device = {
  deviceId: string;
  deviceName: string | null;
  firstSeenAt?: string | null;
  lastSeenAt?: string | null;
};

export type NormalizedMetrics = {
  stepsToday: number | null;
  distanceKmToday: number | null;
  activeCaloriesKcalToday: number | null;
  totalCaloriesKcalToday: number | null;
  latestHeartRateBpm: number | null;
  restingHeartRateBpm: number | null;
  sleepMinutesLastNight: number | null;
  latestWeightKg: number | null;
};

export type Snapshot = NormalizedMetrics & {
  id: number;
  capturedAt: string;
  receivedAt: string;
  timezone: string | null;
  recentExercises?: JsonValue;
  permissions?: JsonValue;
  errors?: JsonValue;
};

export type RecentSnapshot = Snapshot;

export type LatestResponse = {
  device: Device;
  snapshot: Snapshot | null;
};

export type RecentSnapshotsResponse = {
  device: Device;
  snapshots: RecentSnapshot[];
};

export type SnapshotDetailResponse = {
  device: Device;
  snapshot: Snapshot;
  rawPayload: JsonValue;
};

export type HistoryPoint = {
  capturedAt: string;
  receivedAt: string;
  value: number | null;
};

export type HistoryResponse = {
  deviceId: string;
  metric: string;
  points: HistoryPoint[];
};


export type Meal = {
  id: number;
  clientMealId: string | null;
  deviceId: string;
  eatenAt: string;
  localDate: string;
  nutritionDate?: string;
  timezone: string;
  mealType: string;
  title: string;
  notes: string | null;
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  fiberG: number | null;
  sugarG: number | null;
  sodiumMg: number | null;
  createdAt: string;
  updatedAt: string;
};

export type MealListResponse = {
  meals: Meal[];
};

export type MealTypeSummary = {
  mealType: string;
  mealCount: number;
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
};

export type NutritionSummaryResponse = {
  fromDate: string;
  toDate: string;
  mealCount: number;
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
  byMealType: MealTypeSummary[];
};
