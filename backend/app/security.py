import secrets

from fastapi import Depends, HTTPException, Request, status

from app.config import Settings, get_settings


def require_admin_token(request: Request, settings: Settings = Depends(get_settings)) -> None:
    if not settings.admin_token:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Admin API is disabled")

    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing admin bearer token")

    if not secrets.compare_digest(token, settings.admin_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid admin bearer token")
