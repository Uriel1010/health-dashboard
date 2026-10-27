from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import delete, desc, func, select, text
from sqlalchemy.orm import Session, joinedload

from app.models import Device, HealthSnapshot


HISTORY_METRICS = {
    "steps_today": HealthSnapshot.steps_today,
    "distance_km_today": HealthSnapshot.distance_km_today,
    "active_calories_kcal_today": HealthSnapshot.active_calories_kcal_today,
    "total_calories_kcal_today": HealthSnapshot.total_calories_kcal_today,
    "latest_heart_rate_bpm": HealthSnapshot.latest_heart_rate_bpm,
    "resting_heart_rate_bpm": HealthSnapshot.resting_heart_rate_bpm,
    "sleep_minutes_last_night": HealthSnapshot.sleep_minutes_last_night,
    "latest_weight_kg": HealthSnapshot.latest_weight_kg,
}


def check_database(db: Session) -> None:
    db.execute(text("select 1"))


def upsert_device(db: Session, user_id: int, device_id: str, device_name: str | None, seen_at: datetime) -> Device:
    device = db.scalar(select(Device).where(Device.user_id == user_id, Device.device_id == device_id))
    now = datetime.now(timezone.utc)
    if device is None:
        device = Device(user_id=user_id, device_id=device_id, device_name=device_name, first_seen_at=now, last_seen_at=seen_at)
        db.add(device)
        db.flush()
        return device

    device.device_name = device_name or device.device_name
    device.last_seen_at = seen_at
    return device


def create_snapshot(db: Session, user_id: int, normalized: dict[str, Any]) -> HealthSnapshot:
    device = upsert_device(
        db,
        user_id=user_id,
        device_id=normalized["device_id"],
        device_name=normalized.get("device_name"),
        seen_at=normalized["captured_at"],
    )
    snapshot = HealthSnapshot(
        device_fk=device.id,
        captured_at=normalized["captured_at"],
        received_at=datetime.now(timezone.utc),
        timezone=normalized.get("timezone"),
        steps_today=normalized.get("steps_today"),
        distance_km_today=normalized.get("distance_km_today"),
        active_calories_kcal_today=normalized.get("active_calories_kcal_today"),
        total_calories_kcal_today=normalized.get("total_calories_kcal_today"),
        latest_heart_rate_bpm=normalized.get("latest_heart_rate_bpm"),
        resting_heart_rate_bpm=normalized.get("resting_heart_rate_bpm"),
        sleep_minutes_last_night=normalized.get("sleep_minutes_last_night"),
        latest_weight_kg=normalized.get("latest_weight_kg"),
        recent_exercises_json=normalized.get("recent_exercises_json"),
        permissions_json=normalized.get("permissions_json"),
        errors_json=normalized.get("errors_json"),
        raw_payload_json=normalized["raw_payload_json"],
    )
    db.add(snapshot)
    db.commit()
    db.refresh(snapshot)
    return snapshot


def list_devices(db: Session, user_id: int) -> list[Device]:
    return list(db.scalars(select(Device).where(Device.user_id == user_id).order_by(desc(Device.last_seen_at))).all())


def get_device(db: Session, user_id: int, device_id: str | None = None) -> Device | None:
    if device_id:
        return db.scalar(select(Device).where(Device.user_id == user_id, Device.device_id == device_id))
    return db.scalar(select(Device).where(Device.user_id == user_id).order_by(desc(Device.last_seen_at)).limit(1))


def get_latest_snapshot(db: Session, device: Device) -> HealthSnapshot | None:
    return db.scalar(
        select(HealthSnapshot)
        .where(HealthSnapshot.device_fk == device.id)
        .order_by(desc(HealthSnapshot.captured_at), desc(HealthSnapshot.received_at))
        .limit(1)
    )


def get_history(
    db: Session,
    device: Device,
    metric: str,
    from_dt: datetime | None,
    to_dt: datetime | None,
) -> list[tuple[datetime, datetime, Any]]:
    metric_column = HISTORY_METRICS[metric]
    query = select(HealthSnapshot.captured_at, HealthSnapshot.received_at, metric_column).where(
        HealthSnapshot.device_fk == device.id
    )
    if from_dt:
        query = query.where(HealthSnapshot.captured_at >= from_dt)
    if to_dt:
        query = query.where(HealthSnapshot.captured_at <= to_dt)
    query = query.order_by(HealthSnapshot.captured_at)
    return list(db.execute(query).all())

def get_recent_snapshots(
    db: Session,
    device: Device,
    limit: int,
    from_dt: datetime | None,
    to_dt: datetime | None,
) -> list[HealthSnapshot]:
    query = select(HealthSnapshot).where(HealthSnapshot.device_fk == device.id)
    if from_dt:
        query = query.where(HealthSnapshot.captured_at >= from_dt)
    if to_dt:
        query = query.where(HealthSnapshot.captured_at <= to_dt)
    query = query.order_by(desc(HealthSnapshot.captured_at), desc(HealthSnapshot.received_at)).limit(limit)
    return list(db.scalars(query).all())


def get_snapshot_by_id(db: Session, user_id: int, snapshot_id: int) -> HealthSnapshot | None:
    return db.scalar(
        select(HealthSnapshot)
        .join(Device, HealthSnapshot.device_fk == Device.id)
        .options(joinedload(HealthSnapshot.device))
        .where(HealthSnapshot.id == snapshot_id, Device.user_id == user_id)
    )



@dataclass(frozen=True)
class AdminStats:
    device_count: int
    snapshot_count: int
    oldest_snapshot_at: datetime | None
    newest_snapshot_at: datetime | None
    devices: list[dict[str, Any]]


@dataclass(frozen=True)
class CleanupResult:
    criteria: dict[str, Any]
    matched_devices: int
    matched_snapshots: int
    deleted_devices: int
    deleted_snapshots: int


def admin_stats(db: Session) -> AdminStats:
    device_count = int(db.scalar(select(func.count(Device.id))) or 0)
    snapshot_count = int(db.scalar(select(func.count(HealthSnapshot.id))) or 0)
    oldest_snapshot_at = db.scalar(select(func.min(HealthSnapshot.captured_at)))
    newest_snapshot_at = db.scalar(select(func.max(HealthSnapshot.captured_at)))
    rows = db.execute(
        select(
            Device.device_id,
            Device.device_name,
            func.count(HealthSnapshot.id),
            func.min(HealthSnapshot.captured_at),
            func.max(HealthSnapshot.captured_at),
        )
        .outerjoin(HealthSnapshot, HealthSnapshot.device_fk == Device.id)
        .group_by(Device.id, Device.device_id, Device.device_name)
        .order_by(desc(func.max(HealthSnapshot.captured_at)).nullslast(), Device.device_id)
    ).all()
    return AdminStats(
        device_count=device_count,
        snapshot_count=snapshot_count,
        oldest_snapshot_at=oldest_snapshot_at,
        newest_snapshot_at=newest_snapshot_at,
        devices=[
            {
                "deviceId": device_id,
                "deviceName": device_name,
                "snapshotCount": int(snapshot_count or 0),
                "firstSnapshotAt": first_snapshot_at,
                "lastSnapshotAt": last_snapshot_at,
            }
            for device_id, device_name, snapshot_count, first_snapshot_at, last_snapshot_at in rows
        ],
    )


def delete_snapshots(
    db: Session,
    device_id: str | None,
    from_dt: datetime | None,
    to_dt: datetime | None,
    dry_run: bool,
) -> CleanupResult:
    criteria = _cleanup_criteria(device_id, from_dt, to_dt)
    matching_snapshot_ids = _matching_snapshot_ids(db, device_id=device_id, from_dt=from_dt, to_dt=to_dt)
    matched_snapshots = len(matching_snapshot_ids)
    matched_devices = _matching_device_count_for_snapshots(db, matching_snapshot_ids)
    deleted_snapshots = 0

    if not dry_run and matching_snapshot_ids:
        deleted_snapshots = int(
            db.execute(delete(HealthSnapshot).where(HealthSnapshot.id.in_(matching_snapshot_ids))).rowcount or 0
        )
        db.commit()

    return CleanupResult(
        criteria=criteria,
        matched_devices=matched_devices,
        matched_snapshots=matched_snapshots,
        deleted_devices=0,
        deleted_snapshots=deleted_snapshots,
    )


def delete_test_data(db: Session, device_id: str, dry_run: bool) -> CleanupResult:
    criteria = {"deviceIdOrName": device_id}
    matching_devices = list(
        db.scalars(select(Device).where((Device.device_id == device_id) | (Device.device_name == device_id))).all()
    )
    device_pks = [device.id for device in matching_devices]
    matching_snapshot_ids = []
    if device_pks:
        matching_snapshot_ids = list(
            db.scalars(select(HealthSnapshot.id).where(HealthSnapshot.device_fk.in_(device_pks))).all()
        )

    deleted_snapshots = 0
    deleted_devices = 0
    if not dry_run and device_pks:
        if matching_snapshot_ids:
            deleted_snapshots = int(
                db.execute(delete(HealthSnapshot).where(HealthSnapshot.id.in_(matching_snapshot_ids))).rowcount or 0
            )
        deleted_devices = int(db.execute(delete(Device).where(Device.id.in_(device_pks))).rowcount or 0)
        db.commit()

    return CleanupResult(
        criteria=criteria,
        matched_devices=len(device_pks),
        matched_snapshots=len(matching_snapshot_ids),
        deleted_devices=deleted_devices,
        deleted_snapshots=deleted_snapshots,
    )


def _cleanup_criteria(device_id: str | None, from_dt: datetime | None, to_dt: datetime | None) -> dict[str, Any]:
    return {
        "deviceId": device_id,
        "from": from_dt.isoformat() if from_dt else None,
        "to": to_dt.isoformat() if to_dt else None,
    }


def _matching_snapshot_ids(
    db: Session,
    device_id: str | None,
    from_dt: datetime | None,
    to_dt: datetime | None,
) -> list[int]:
    query = select(HealthSnapshot.id)
    if device_id:
        query = query.join(Device, HealthSnapshot.device_fk == Device.id).where(Device.device_id == device_id)
    if from_dt:
        query = query.where(HealthSnapshot.captured_at >= from_dt)
    if to_dt:
        query = query.where(HealthSnapshot.captured_at <= to_dt)
    return list(db.scalars(query).all())


def _matching_device_count_for_snapshots(db: Session, snapshot_ids: list[int]) -> int:
    if not snapshot_ids:
        return 0
    return int(
        db.scalar(
            select(func.count(func.distinct(HealthSnapshot.device_fk))).where(HealthSnapshot.id.in_(snapshot_ids))
        )
        or 0
    )
