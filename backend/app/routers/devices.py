from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.crud import list_devices
from app.database import get_db
from app.auth import require_data_user
from app.models import Device, HealthSnapshot, User
from app.schemas import DeviceOut

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])


@router.get("", response_model=list[DeviceOut])
def devices(db: Session = Depends(get_db), user: User = Depends(require_data_user)) -> list[DeviceOut]:
    return [
        DeviceOut(
            deviceId=device.device_id,
            deviceName=device.device_name,
            firstSeenAt=device.first_seen_at,
            lastSeenAt=device.last_seen_at,
        )
        for device in list_devices(db, user.id)
    ]


@router.delete("/{device_id}")
def delete_device(
    device_id: str,
    db: Session = Depends(get_db),
    user: User = Depends(require_data_user),
) -> dict[str, bool | str | int]:
    device = db.scalar(select(Device).where(Device.user_id == user.id, Device.device_id == device_id))
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    deleted_snapshots = int(db.execute(delete(HealthSnapshot).where(HealthSnapshot.device_fk == device.id)).rowcount or 0)
    db.delete(device)
    db.commit()
    return {"ok": True, "deviceId": device_id, "deletedSnapshots": deleted_snapshots}
