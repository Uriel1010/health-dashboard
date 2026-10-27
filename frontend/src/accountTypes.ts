import type { Device, HistoryResponse, LatestResponse, MealListResponse, NutritionSummaryResponse, RecentSnapshotsResponse, SnapshotDetailResponse } from "./types";

export type CurrentUser = {
  id: number;
  username: string;
  displayName: string;
  isAdmin: boolean;
  isActive: boolean;
  mustChangePassword: boolean;
  nutritionResetHour: number;
  nutritionGoals: FoodGoals;
  serverTimezone: string;
  currentNutritionDate: string;
};

export type FoodGoals = {
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  fiberG: number;
};

export type AdminUserCreated = {
  user: CurrentUser;
  temporaryPassword: string;
};

export type IngestTokenStatus = {
  active: boolean;
  prefix: string | null;
  createdAt: string | null;
};

export type IngestTokenCreated = IngestTokenStatus & { token: string };

export type MacroValues = {
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
};

export type NutritionTrendPoint = {
  date: string;
  hasMeals: boolean;
  isIncomplete: boolean;
  daily: MacroValues;
  average7: MacroValues;
  average14: MacroValues;
  average30: MacroValues;
};

export type NutritionTrendsResponse = {
  fromDate: string;
  toDate: string;
  serverTimezone: string;
  nutritionResetHour: number;
  points: NutritionTrendPoint[];
};

export type {
  Device,
  HistoryResponse,
  LatestResponse,
  MealListResponse,
  NutritionSummaryResponse,
  RecentSnapshotsResponse,
  SnapshotDetailResponse,
};
