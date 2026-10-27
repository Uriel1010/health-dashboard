import logging
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request as UrlRequest, urlopen

from fastapi import Depends, FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from sqlalchemy.orm import Session

from app.config import get_settings
from app.crud import check_database
from app.database import get_db
from app.routers import account, admin, devices, health, ingest, nutrition_user, users
from app.schemas import HealthResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("health-dashboard")

settings = get_settings()
app = FastAPI(title="Health Dashboard API", version="0.3.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
)


@app.middleware("http")
async def request_logging(request: Request, call_next):
    started = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - started) * 1000
    client_host = request.client.host if request.client else "unknown"
    logger.info(
        "request method=%s path=%s status=%s duration_ms=%.1f client=%s",
        request.method,
        request.url.path,
        response.status_code,
        elapsed_ms,
        client_host,
    )
    return response


@app.get("/health", response_model=HealthResponse, tags=["service"])
def service_health(db: Session = Depends(get_db)) -> HealthResponse:
    check_database(db)
    return HealthResponse(status="ok", database="ok", timestamp=datetime.now(timezone.utc))


app.include_router(account.router)
app.include_router(ingest.router)
app.include_router(nutrition_user.router)
app.include_router(health.router)
app.include_router(devices.router)
app.include_router(admin.router)
app.include_router(users.router)


@app.get("/{frontend_path:path}", include_in_schema=False)
def frontend_proxy(frontend_path: str, request: Request) -> Response:
    """Serve the frontend when the public tunnel terminates at the API port."""
    target = f"http://frontend:3000/{frontend_path}"
    if request.url.query:
        target = f"{target}?{request.url.query}"
    upstream_request = UrlRequest(
        target,
        headers={"Accept": request.headers.get("accept", "*/*")},
    )
    try:
        with urlopen(upstream_request, timeout=10) as upstream:
            body = upstream.read()
            status = upstream.status
            upstream_headers = upstream.headers
    except HTTPError as error:
        body = error.read()
        status = error.code
        upstream_headers = error.headers
    except URLError:
        return Response("Frontend unavailable", status_code=503, media_type="text/plain")

    headers = {}
    for name in ("Content-Type", "Cache-Control", "ETag", "Last-Modified"):
        value = upstream_headers.get(name)
        if value:
            headers[name] = value
    return Response(content=body, status_code=status, headers=headers)
