import type { Device, HistoryResponse, LatestResponse, MealListResponse, NutritionSummaryResponse, RecentSnapshotsResponse, SnapshotDetailResponse } from "./types";

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const browserApiBaseUrl = `${window.location.protocol}//${window.location.hostname}:8015`;
const API_BASE_URL = configuredApiBaseUrl || browserApiBaseUrl;

type RecentParams = {
  deviceId: string;
  limit?: number;
  from?: string;
  to?: string;
};

type HistoryParams = {
  from?: string;
  to?: string;
};

type MealsParams = {
  date?: string;
  from?: string;
  to?: string;
};

type RequestOptions = {
  readToken?: string;
};

type WriteOptions = {
  ingestToken?: string;
};

export class ApiError extends Error {
  status: number;
  detail: string;

  constructor(status: number, statusText: string, detail: string) {
    super(detail || `${status} ${statusText}`);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
  }
}

async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers();
  const token = options.readToken?.trim();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { headers });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const payload = await response.json();
      if (typeof payload?.detail === "string") detail = payload.detail;
    } catch {
      // Keep the HTTP status text when the API returns a non-JSON error.
    }
    throw new ApiError(response.status, response.statusText, detail);
  }
  return response.json() as Promise<T>;
}

async function requestWrite(path: string, method: string, options: WriteOptions = {}): Promise<void> {
  const headers = new Headers();
  const token = options.ingestToken?.trim();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { method, headers });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const payload = await response.json();
      if (typeof payload?.detail === "string") detail = payload.detail;
    } catch {
      // Keep the HTTP status text when the API returns a non-JSON error.
    }
    throw new ApiError(response.status, response.statusText, detail);
  }
}

function queryString(params: Record<string, string | number | undefined>): string {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      searchParams.set(key, String(value));
    }
  }
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

export function getLatest(deviceId?: string, options: RequestOptions = {}): Promise<LatestResponse> {
  return requestJson<LatestResponse>(`/api/v1/health/latest${queryString({ deviceId })}`, options);
}

export function getDevices(options: RequestOptions = {}): Promise<Device[]> {
  return requestJson<Device[]>("/api/v1/devices", options);
}

export function getHistory(deviceId: string, metric: string, params: HistoryParams = {}, options: RequestOptions = {}): Promise<HistoryResponse> {
  return requestJson<HistoryResponse>(
    `/api/v1/health/history${queryString({ deviceId, metric, from: params.from, to: params.to })}`,
    options,
  );
}

export function getRecent(params: RecentParams, options: RequestOptions = {}): Promise<RecentSnapshotsResponse> {
  return requestJson<RecentSnapshotsResponse>(
    `/api/v1/health/recent${queryString({
      deviceId: params.deviceId,
      limit: params.limit,
      from: params.from,
      to: params.to,
    })}`,
    options,
  );
}

export function getSnapshot(id: number | string, options: RequestOptions = {}): Promise<SnapshotDetailResponse> {
  return requestJson<SnapshotDetailResponse>(`/api/v1/health/snapshot/${encodeURIComponent(String(id))}`, options);
}


export function getMeals(params: MealsParams, options: RequestOptions = {}): Promise<MealListResponse> {
  return requestJson<MealListResponse>(
    `/api/v1/nutrition/meals${queryString({ date: params.date, from: params.from, to: params.to })}`,
    options,
  );
}

export function deleteMeal(id: number, options: WriteOptions = {}): Promise<void> {
  return requestWrite(`/api/v1/nutrition/meals/${id}`, "DELETE", options);
}

export function getDailyNutritionSummary(date: string, options: RequestOptions = {}): Promise<NutritionSummaryResponse> {
  return requestJson<NutritionSummaryResponse>(`/api/v1/nutrition/summary/daily${queryString({ date })}`, options);
}

export function getRangeNutritionSummary(from: string, to: string, options: RequestOptions = {}): Promise<NutritionSummaryResponse> {
  return requestJson<NutritionSummaryResponse>(`/api/v1/nutrition/summary/range${queryString({ from, to })}`, options);
}
