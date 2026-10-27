import getpass
import re

from sqlalchemy import select

from app.auth import hash_password, token_hash
from app.config import get_settings
from app.database import SessionLocal
from app.models import IngestToken, User

_BOOTSTRAP_USERNAME = "__bootstrap_admin__"
_USERNAME_RE = re.compile(r"^[A-Za-z0-9._-]{3,64}$")


def bootstrap_admin() -> None:
    settings = get_settings()
    with SessionLocal() as db:
        admin = db.scalar(select(User).where(User.is_admin.is_(True)))
        if admin is None:
            raise SystemExit("Migration did not create the bootstrap administrator.")
        if admin.username_normalized != _BOOTSTRAP_USERNAME:
            raise SystemExit(f"Administrator already bootstrapped as {admin.username!r}.")

        username = input("Administrator username: ").strip()
        if not _USERNAME_RE.fullmatch(username):
            raise SystemExit("Username must be 3-64 characters using letters, numbers, dot, underscore, or hyphen.")
        display_name = input("Display name: ").strip()
        if not display_name:
            raise SystemExit("Display name is required.")
        password = getpass.getpass("Password (minimum 12 characters): ")
        confirmation = getpass.getpass("Confirm password: ")
        if password != confirmation:
            raise SystemExit("Passwords do not match.")
        if len(password) < 12:
            raise SystemExit("Password must be at least 12 characters.")

        admin.username = username
        admin.username_normalized = username.casefold()
        admin.display_name = display_name
        admin.password_hash = hash_password(password)
        admin.must_change_password = False

        legacy = settings.ingest_api_token.strip()
        if legacy:
            digest = token_hash(legacy)
            existing = db.scalar(select(IngestToken).where(IngestToken.token_hash == digest))
            if existing is None:
                db.add(IngestToken(user_id=admin.id, token_hash=digest, token_prefix=legacy[:15]))
        db.commit()
        print(f"Administrator {username!r} is ready. Existing health data remains assigned to this account.")


if __name__ == "__main__":
    bootstrap_admin()
