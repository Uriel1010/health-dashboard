import type { JsonValue, Snapshot } from "./types";

export type DataStatus = "OK" | "No data" | "Permission missing" | "Error" | "Not synced yet";
export type FreshnessStatus = "Fresh" | "Delayed" | "Stale" | "No sync";

export const metricAliases: Record<string, string[]> = {
  stepsToday: ["stepsToday", "steps_today", "steps", "Steps"],
  distanceKmToday: ["distanceKmToday", "distance_km_today", "distance", "distanceKm", "Distance"],
  activeCaloriesKcalToday: ["activeCaloriesKcalToday", "active_calories_kcal_today", "activeCalories", "ActiveCalories"],
  totalCaloriesKcalToday: ["totalCaloriesKcalToday", "total_calories_kcal_today", "totalCalories", "TotalCalories"],
  latestHeartRateBpm: ["latestHeartRateBpm", "latest_heart_rate_bpm", "heartRate", "HeartRate"],
  restingHeartRateBpm: ["restingHeartRateBpm", "resting_heart_rate_bpm", "restingHeartRate", "RestingHeartRate"],
  sleepMinutesLastNight: ["sleepMinutesLastNight", "sleep_minutes_last_night", "sleep", "Sleep"],
  latestWeightKg: ["latestWeightKg", "latest_weight_kg", "weight", "Weight"],
  recentExercises: ["recentExercises", "recent_exercises", "exercise", "Exercise"],
};

export function getFreshness(receivedAt?: string | null): FreshnessStatus {
  if (!receivedAt) return "No sync";
  const ageMinutes = (Date.now() - new Date(receivedAt).getTime()) / 60000;
  if (!Number.isFinite(ageMinutes)) return "No sync";
  if (ageMinutes < 20) return "Fresh";
  if (ageMinutes <= 60) return "Delayed";
  return "Stale";
}

export function getMetricStatus(snapshot: Snapshot | null, key: string, hasValue: boolean): DataStatus {
  if (!snapshot) return "Not synced yet";
  if (hasValue) return "OK";
  const aliases = metricAliases[key] ?? [key];
  if (hasMetricError(snapshot.errors, aliases)) return "Error";
  if (hasMissingPermission(snapshot.permissions, aliases)) return "Permission missing";
  return "No data";
}

export function hasMissingPermission(value: JsonValue | undefined, aliases: string[]): boolean {
  if (value === null || value === undefined) return false;
  if (typeof value === "boolean") return !value;
  if (typeof value === "string") {
    const normalized = value.toLowerCase();
    return aliases.some((alias) => normalized.includes(alias.toLowerCase())) && /missing|denied|false|not granted/.test(normalized);
  }
  if (Array.isArray(value)) return value.some((item) => hasMissingPermission(item, aliases));
  if (typeof value === "object") {
    return Object.entries(value).some(([entryKey, entryValue]) => {
      const keyMatches = aliases.some((alias) => entryKey.toLowerCase().includes(alias.toLowerCase()));
      if (keyMatches && (entryValue === false || entryValue === "missing" || entryValue === "denied")) return true;
      return hasMissingPermission(entryValue, aliases);
    });
  }
  return false;
}

export function hasMetricError(value: JsonValue | undefined, aliases: string[]): boolean {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") {
    const normalized = value.toLowerCase();
    return aliases.some((alias) => normalized.includes(alias.toLowerCase()));
  }
  if (Array.isArray(value)) return value.some((item) => hasMetricError(item, aliases));
  if (typeof value === "object") {
    return Object.entries(value).some(([entryKey, entryValue]) => {
      const keyMatches = aliases.some((alias) => entryKey.toLowerCase().includes(alias.toLowerCase()));
      return keyMatches || hasMetricError(entryValue, aliases);
    });
  }
  return false;
}

export function jsonItemCount(value: JsonValue | undefined): number {
  return Array.isArray(value) ? value.length : 0;
}
