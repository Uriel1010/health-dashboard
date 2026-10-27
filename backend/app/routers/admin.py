import logging
import re
from datetime import date, datetime, time, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.crud import (
    AdminStats,
    CleanupResult,
    admin_stats,
    delete_snapshots,
    delete_test_data,
)
from app.database import get_db
from app.schemas import AdminCleanupResponse, AdminStatsResponse
from app.security import require_admin_token

router = APIRouter(prefix="/api/admin", tags=["admin"], dependencies=[Depends(require_admin_token)])
logger = logging.getLogger(__name__)

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_ALL_SNAPSHOTS_CONFIRMATION = "DELETE_ALL_SNAPSHOTS"


@router.get("/stats", response_model=AdminStatsResponse)
def get_admin_stats(db: Session = Depends(get_db)) -> AdminStatsResponse:
    return _stats_response(admin_stats(db))


@router.delete("/devices/{device_id}/snapshots", response_model=AdminCleanupResponse)
def delete_device_snapshots(
    device_id: str,
    dryRun: bool = Query(default=True),
    db: Session = Depends(get_db),
) -> AdminCleanupResponse:
    device_id = _clean_device_id(device_id)
    before = admin_stats(db)
    result = delete_snapshots(db, device_id=device_id, from_dt=None, to_dt=None, dry_run=dryRun)
    if result.matched_snapshots == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No matching snapshots found")
    after = before if dryRun else admin_stats(db)
    logger.info("admin cleanup action=delete_device_snapshots device_id=%s dry_run=%s matched_snapshots=%s deleted_snapshots=%s", device_id, dryRun, result.matched_snapshots, result.deleted_snapshots)
    return _cleanup_response("delete_device_snapshots", dryRun, result, before, after)


@router.delete("/snapshots", response_model=AdminCleanupResponse)
def delete_snapshots_by_filter(
    deviceId: str | None = None,
    from_: str | None = Query(default=None, alias="from"),
    to: str | None = None,
    dryRun: bool = Query(default=True),
    confirm: str | None = None,
    db: Session = Depends(get_db),
) -> AdminCleanupResponse:
    device_id = _clean_optional_device_id(deviceId)
    from_dt = _parse_date_start(from_, "from") if from_ else None
    to_dt = _parse_date_end(to, "to") if to else None
    _validate_range(from_dt, to_dt)

    if not device_id and not from_dt and not to_dt and confirm != _ALL_SNAPSHOTS_CONFIRMATION:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Refusing full snapshot cleanup without confirm={_ALL_SNAPSHOTS_CONFIRMATION}",
        )

    before = admin_stats(db)
    result = delete_snapshots(db, device_id=device_id, from_dt=from_dt, to_dt=to_dt, dry_run=dryRun)
    if result.matched_snapshots == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No matching snapshots found")
    after = before if dryRun else admin_stats(db)
    logger.info("admin cleanup action=delete_snapshots device_id=%s from=%s to=%s dry_run=%s matched_snapshots=%s deleted_snapshots=%s", device_id, from_, to, dryRun, result.matched_snapshots, result.deleted_snapshots)
    return _cleanup_response("delete_snapshots", dryRun, result, before, after)


@router.delete("/test-data", response_model=AdminCleanupResponse)
def delete_test_device_data(
    deviceId: str = Query(default="pixel-test"),
    dryRun: bool = Query(default=True),
    db: Session = Depends(get_db),
) -> AdminCleanupResponse:
    device_id = _clean_device_id(deviceId)
    before = admin_stats(db)
    result = delete_test_data(db, device_id=device_id, dry_run=dryRun)
    if result.matched_devices == 0 and result.matched_snapshots == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No matching test data found")
    after = before if dryRun else admin_stats(db)
    logger.info("admin cleanup action=delete_test_data device_id=%s dry_run=%s matched_devices=%s matched_snapshots=%s deleted_devices=%s deleted_snapshots=%s", device_id, dryRun, result.matched_devices, result.matched_snapshots, result.deleted_devices, result.deleted_snapshots)
    return _cleanup_response("delete_test_data", dryRun, result, before, after)


def _clean_device_id(device_id: str) -> str:
    cleaned = device_id.strip()
    if not cleaned:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="deviceId is required")
    return cleaned


def _clean_optional_device_id(device_id: str | None) -> str | None:
    if device_id is None:
        return None
    return _clean_device_id(device_id)


def _parse_date_start(value: str, field_name: str) -> datetime:
    return datetime.combine(_parse_date(value, field_name), time.min, tzinfo=timezone.utc)


def _parse_date_end(value: str, field_name: str) -> datetime:
    return datetime.combine(_parse_date(value, field_name), time.max, tzinfo=timezone.utc)


def _parse_date(value: str, field_name: str) -> date:
    if not _DATE_RE.match(value):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{field_name} must use YYYY-MM-DD")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"{field_name} must be a valid date") from exc


def _validate_range(from_dt: datetime | None, to_dt: datetime | None) -> None:
    if from_dt and to_dt and from_dt > to_dt:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be before or equal to to")


def _stats_response(stats: AdminStats) -> AdminStatsResponse:
    return AdminStatsResponse(
        deviceCount=stats.device_count,
        snapshotCount=stats.snapshot_count,
        oldestSnapshotAt=stats.oldest_snapshot_at,
        newestSnapshotAt=stats.newest_snapshot_at,
        devices=stats.devices,
    )


def _cleanup_response(action: str, dry_run: bool, result: CleanupResult, before: AdminStats, after: AdminStats) -> AdminCleanupResponse:
    return AdminCleanupResponse(
        action=action,
        dryRun=dry_run,
        criteria=result.criteria,
        matchedDevices=result.matched_devices,
        matchedSnapshots=result.matched_snapshots,
        deletedDevices=result.deleted_devices,
        deletedSnapshots=result.deleted_snapshots,
        before=_stats_response(before),
        after=_stats_response(after),
    )
