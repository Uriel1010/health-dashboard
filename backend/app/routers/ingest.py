import logging

from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.crud import create_snapshot
from app.database import get_db
from app.normalizer import parse_snapshot_payload
from app.models import User
from app.schemas import IngestResponse
from app.auth import require_ingest_user

router = APIRouter(prefix="/api/v1/ingest", tags=["ingest"])
logger = logging.getLogger(__name__)


@router.post("/health-snapshot", response_model=IngestResponse)
async def ingest_health_snapshot(request: Request, db: Session = Depends(get_db), user: User = Depends(require_ingest_user)) -> IngestResponse:
    payload = await request.json()
    normalized = parse_snapshot_payload(payload)
    snapshot = create_snapshot(db, user.id, normalized)
    client_host = request.client.host if request.client else "unknown"
    logger.info("stored health snapshot id=%s device_id=%s client=%s", snapshot.id, normalized["device_id"], client_host)
    return IngestResponse(ok=True, snapshotId=snapshot.id, receivedAt=snapshot.received_at)
