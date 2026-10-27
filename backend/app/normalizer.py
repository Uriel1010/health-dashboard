from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Any

from fastapi import HTTPException, status


METRIC_ALIASES = {
    "steps_today": ("stepsToday", "steps_today", "steps"),
    "distance_km_today": ("distanceKmToday", "distance_km_today", "distanceKm"),
    "active_calories_kcal_today": ("activeCaloriesKcalToday", "active_calories_kcal_today", "activeCaloriesKcal"),
    "total_calories_kcal_today": ("totalCaloriesKcalToday", "total_calories_kcal_today", "totalCaloriesKcal"),
    "latest_heart_rate_bpm": ("latestHeartRateBpm", "latest_heart_rate_bpm", "heartRateBpm"),
    "resting_heart_rate_bpm": ("restingHeartRateBpm", "resting_heart_rate_bpm"),
    "sleep_minutes_last_night": ("sleepMinutesLastNight", "sleep_minutes_last_night", "sleepMinutes"),
    "latest_weight_kg": ("latestWeightKg", "latest_weight_kg", "weightKg"),
}


def parse_snapshot_payload(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Payload must be a JSON object")

    device_id = _first_string(payload, ("deviceId", "device_id"))
    if not device_id:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="deviceId is required")

    captured_raw = payload.get("capturedAt") or payload.get("captured_at") or payload.get("timestamp")
    captured_at = _parse_datetime(captured_raw, "capturedAt")

    metrics_source = payload.get("metrics") if isinstance(payload.get("metrics"), dict) else {}
    normalized = {
        "device_id": device_id,
        "device_name": _first_string(payload, ("deviceName", "device_name")),
        "captured_at": captured_at,
        "timezone": _first_string(payload, ("timezone", "timeZone")),
        "recent_exercises_json": payload.get("recentExercises") or payload.get("recent_exercises") or metrics_source.get("recentExercises") or metrics_source.get("recent_exercises"),
        "permissions_json": payload.get("permissions"),
        "errors_json": payload.get("errors"),
        "raw_payload_json": payload,
    }

    for column, aliases in METRIC_ALIASES.items():
        raw_value = _first_present(metrics_source, aliases)
        if raw_value is None:
            raw_value = _first_present(payload, aliases)
        normalized[column] = _coerce_metric(column, raw_value)

    return normalized


def _first_string(source: dict[str, Any], keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = source.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _first_present(source: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        if key in source and source[key] is not None:
            return source[key]
    return None


def _parse_datetime(value: Any, field_name: str) -> datetime:
    if not isinstance(value, str) or not value.strip():
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{field_name} is required")

    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{field_name} must be an ISO datetime") from exc

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def _coerce_metric(column: str, value: Any) -> int | Decimal | None:
    if value in (None, ""):
        return None
    if column in {"distance_km_today", "latest_weight_kg"}:
        try:
            return Decimal(str(value))
        except (InvalidOperation, ValueError) as exc:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{column} must be numeric") from exc
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{column} must be an integer") from exc
