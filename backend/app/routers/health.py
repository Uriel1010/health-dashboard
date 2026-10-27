from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.crud import (
    HISTORY_METRICS,
    get_device,
    get_history,
    get_latest_snapshot,
    get_recent_snapshots,
    get_snapshot_by_id,
)
from app.database import get_db
from app.models import Device, HealthSnapshot, User
from app.auth import require_data_user
from app.schemas import (
    DeviceOut,
    HistoryPoint,
    HistoryResponse,
    LatestSnapshotResponse,
    RecentSnapshotsResponse,
    SnapshotDetailResponse,
    SnapshotOut,
)

router = APIRouter(prefix="/api/v1/health", tags=["health"])


@router.get("/latest", response_model=LatestSnapshotResponse)
def latest_health(deviceId: str | None = None, db: Session = Depends(get_db), user: User = Depends(require_data_user)) -> LatestSnapshotResponse:
    device = get_device(db, user.id, deviceId)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    return LatestSnapshotResponse(device=_device_out(device), snapshot=_snapshot_out(get_latest_snapshot(db, device)))


@router.get("/history", response_model=HistoryResponse)
def health_history(
    deviceId: str,
    metric: str,
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
) -> HistoryResponse:
    if metric not in HISTORY_METRICS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Unsupported metric")
    device = get_device(db, user.id, deviceId)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    points = [
        HistoryPoint(capturedAt=captured_at, receivedAt=received_at, value=float(value) if value is not None else None)
        for captured_at, received_at, value in get_history(db, device, metric, from_, to)
    ]
    return HistoryResponse(deviceId=device.device_id, metric=metric, points=points)


@router.get("/recent", response_model=RecentSnapshotsResponse)
def recent_snapshots(
    deviceId: str,
    limit: int = Query(default=20, ge=1, le=100),
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
) -> RecentSnapshotsResponse:
    device = get_device(db, user.id, deviceId)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    snapshots = get_recent_snapshots(db, device, limit, from_, to)
    return RecentSnapshotsResponse(device=_device_out(device), snapshots=[_snapshot_out(snapshot) for snapshot in snapshots])


@router.get("/snapshot/{snapshot_id}", response_model=SnapshotDetailResponse)
def snapshot_detail(snapshot_id: int, db: Session = Depends(get_db), user: User = Depends(require_data_user)) -> SnapshotDetailResponse:
    snapshot = get_snapshot_by_id(db, user.id, snapshot_id)
    if snapshot is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Snapshot not found")
    return SnapshotDetailResponse(
        device=_device_out(snapshot.device),
        snapshot=_snapshot_out(snapshot),
        rawPayload=snapshot.raw_payload_json,
    )


def _device_out(device: Device) -> DeviceOut:
    return DeviceOut(
        deviceId=device.device_id,
        deviceName=device.device_name,
        firstSeenAt=device.first_seen_at,
        lastSeenAt=device.last_seen_at,
    )


def _snapshot_out(snapshot: HealthSnapshot | None) -> SnapshotOut | None:
    if snapshot is None:
        return None
    return SnapshotOut(
        id=snapshot.id,
        capturedAt=snapshot.captured_at,
        receivedAt=snapshot.received_at,
        timezone=snapshot.timezone,
        stepsToday=snapshot.steps_today,
        distanceKmToday=float(snapshot.distance_km_today) if snapshot.distance_km_today is not None else None,
        activeCaloriesKcalToday=snapshot.active_calories_kcal_today,
        totalCaloriesKcalToday=snapshot.total_calories_kcal_today,
        latestHeartRateBpm=snapshot.latest_heart_rate_bpm,
        restingHeartRateBpm=snapshot.resting_heart_rate_bpm,
        sleepMinutesLastNight=snapshot.sleep_minutes_last_night,
        latestWeightKg=float(snapshot.latest_weight_kg) if snapshot.latest_weight_kg is not None else None,
        recentExercises=_recent_exercises(snapshot),
        permissions=snapshot.permissions_json,
        errors=snapshot.errors_json,
    )

def _recent_exercises(snapshot: HealthSnapshot):
    if snapshot.recent_exercises_json is not None:
        return snapshot.recent_exercises_json
    raw_payload = snapshot.raw_payload_json if isinstance(snapshot.raw_payload_json, dict) else {}
    metrics = raw_payload.get("metrics") if isinstance(raw_payload.get("metrics"), dict) else {}
    return metrics.get("recentExercises") or metrics.get("recent_exercises")
