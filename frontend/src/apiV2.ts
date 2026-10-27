import type {
  AdminUserCreated,
  CurrentUser,
  FoodGoals,
  Device,
  HistoryResponse,
  IngestTokenCreated,
  IngestTokenStatus,
  LatestResponse,
  MealListResponse,
  NutritionSummaryResponse,
  NutritionTrendsResponse,
  RecentSnapshotsResponse,
  SnapshotDetailResponse,
} from "./accountTypes";
import type { Meal } from "./types";

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const defaultApiBaseUrl = window.location.port === "3016"
  ? `${window.location.protocol}//${window.location.hostname}:8015`
  : window.location.origin;
export const API_BASE_URL = (configuredApiBaseUrl || defaultApiBaseUrl).replace(/\/$/, "");
const configuredPublicApiBaseUrl = import.meta.env.VITE_PUBLIC_API_BASE_URL?.trim();
export const PUBLIC_API_BASE_URL = (configuredPublicApiBaseUrl || "https://health.example.com").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(public status: number, public detail: string) {
    super(detail);
    this.name = "ApiError";
  }
}

type JsonBody = Record<string, unknown>;

export type ManualMealPayload = JsonBody & {
  clientMealId: string;
  deviceId: string;
  eatenAt: string;
  timezone: string;
  mealType: "breakfast" | "lunch" | "dinner" | "snack" | "other";
  title: string;
  notes?: string;
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  fiberG?: number;
  sugarG?: number;
  sodiumMg?: number;
};

async function request<T>(path: string, method = "GET", body?: JsonBody): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    credentials: "include",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const payload = await response.json();
      if (typeof payload?.detail === "string") detail = payload.detail;
    } catch {
      // Preserve HTTP status text.
    }
    throw new ApiError(response.status, detail);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  const encoded = search.toString();
  return encoded ? `?${encoded}` : "";
}

export const authApi = {
  me: () => request<CurrentUser>("/api/v1/auth/me"),
  login: (username: string, password: string) => request<CurrentUser>("/api/v1/auth/login", "POST", { username, password }),
  logout: () => request<void>("/api/v1/auth/logout", "POST"),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<CurrentUser>("/api/v1/auth/change-password", "POST", { currentPassword, newPassword }),
  updateResetHour: (nutritionResetHour: number) =>
    request<CurrentUser>("/api/v1/account/preferences", "PATCH", { nutritionResetHour }),
  updateNutritionGoals: (nutritionGoals: FoodGoals) =>
    request<CurrentUser>("/api/v1/account/preferences", "PATCH", { nutritionGoals }),
};

export const accountApi = {
  tokenStatus: () => request<IngestTokenStatus>("/api/v1/account/ingest-token"),
  rotateToken: () => request<IngestTokenCreated>("/api/v1/account/ingest-token/rotate", "POST"),
  revokeToken: () => request<void>("/api/v1/account/ingest-token", "DELETE"),
};

export const adminUsersApi = {
  list: () => request<CurrentUser[]>("/api/v1/admin/users"),
  create: (username: string, displayName: string) =>
    request<AdminUserCreated>("/api/v1/admin/users", "POST", { username, displayName }),
  setActive: (id: number, isActive: boolean) =>
    request<CurrentUser>(`/api/v1/admin/users/${id}`, "PATCH", { isActive }),
  resetPassword: (id: number) =>
    request<AdminUserCreated>(`/api/v1/admin/users/${id}/reset-password`, "POST"),
};

export const dashboardApi = {
  devices: () => request<Device[]>("/api/v1/devices"),
  deleteDevice: (deviceId: string) => request<void>(`/api/v1/devices/${encodeURIComponent(deviceId)}`, "DELETE"),
  latest: (deviceId: string) => request<LatestResponse>(`/api/v1/health/latest${query({ deviceId })}`),
  recent: (deviceId: string, from?: string, to?: string) =>
    request<RecentSnapshotsResponse>(`/api/v1/health/recent${query({ deviceId, limit: 20, from, to })}`),
  history: (deviceId: string, metric: string, from?: string, to?: string) =>
    request<HistoryResponse>(`/api/v1/health/history${query({ deviceId, metric, from, to })}`),
  snapshot: (id: number) => request<SnapshotDetailResponse>(`/api/v1/health/snapshot/${id}`),
  meals: (date: string) => request<MealListResponse>(`/api/v1/nutrition/meals${query({ date })}`),
  createMeal: (payload: ManualMealPayload) => request<Meal>("/api/v1/nutrition/meals", "POST", payload),
  deleteMeal: (id: number) => request<void>(`/api/v1/nutrition/meals/${id}`, "DELETE"),
  dailyNutrition: (date: string) =>
    request<NutritionSummaryResponse>(`/api/v1/nutrition/summary/daily${query({ date })}`),
  nutritionTrends: (to: string) =>
    request<NutritionTrendsResponse>(`/api/v1/nutrition/trends${query({ to })}`),
};
