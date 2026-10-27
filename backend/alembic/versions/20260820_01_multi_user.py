"""Add isolated users, sessions, ingest tokens, and ownership."""

import hashlib
import os

from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect

from app.database import Base
from app import models

revision = "20260820_01"
down_revision = None
branch_labels = None
depends_on = None

_BOOTSTRAP_USERNAME = "__bootstrap_admin__"


def upgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    existing = set(inspector.get_table_names())

    if "devices" not in existing:
        Base.metadata.create_all(bind=bind)
    else:
        models.User.__table__.create(bind=bind, checkfirst=True)
        models.BrowserSession.__table__.create(bind=bind, checkfirst=True)
        models.IngestToken.__table__.create(bind=bind, checkfirst=True)

    admin_id = bind.execute(
        sa.text(
            """
            INSERT INTO users
                (username, username_normalized, display_name, password_hash, is_admin, is_active,
                 must_change_password, nutrition_reset_hour, created_at, updated_at)
            VALUES
                (:username, :username, 'Administrator', '!bootstrap-required', true, true, true, 0, now(), now())
            ON CONFLICT (username_normalized) DO UPDATE SET is_admin = true
            RETURNING id
            """
        ),
        {"username": _BOOTSTRAP_USERNAME},
    ).scalar_one()

    if "devices" in existing:
        device_columns = {column["name"] for column in inspect(bind).get_columns("devices")}
        if "user_id" not in device_columns:
            op.add_column("devices", sa.Column("user_id", sa.Integer(), nullable=True))
            bind.execute(sa.text("UPDATE devices SET user_id = :user_id"), {"user_id": admin_id})
            op.create_foreign_key("fk_devices_user_id", "devices", "users", ["user_id"], ["id"], ondelete="RESTRICT")
            op.create_index("ix_devices_user_id", "devices", ["user_id"])
            op.alter_column("devices", "user_id", nullable=False)
            constraints = {item["name"] for item in inspect(bind).get_unique_constraints("devices")}
            if "uq_devices_device_id" in constraints:
                op.drop_constraint("uq_devices_device_id", "devices", type_="unique")
            op.create_unique_constraint("uq_devices_user_device_id", "devices", ["user_id", "device_id"])

    if "meals" in existing:
        meal_columns = {column["name"] for column in inspect(bind).get_columns("meals")}
        if "user_id" not in meal_columns:
            op.add_column("meals", sa.Column("user_id", sa.Integer(), nullable=True))
            bind.execute(sa.text("UPDATE meals SET user_id = :user_id"), {"user_id": admin_id})
            op.create_foreign_key("fk_meals_user_id", "meals", "users", ["user_id"], ["id"], ondelete="RESTRICT")
            op.create_index("ix_meals_user_id", "meals", ["user_id"])
            op.alter_column("meals", "user_id", nullable=False)
            constraints = {item["name"] for item in inspect(bind).get_unique_constraints("meals")}
            if "uq_meals_source_client_meal" in constraints:
                op.drop_constraint("uq_meals_source_client_meal", "meals", type_="unique")
            op.create_unique_constraint(
                "uq_meals_user_source_client_meal",
                "meals",
                ["user_id", "source_device_id", "client_meal_id"],
            )

    bind.execute(sa.text("CREATE UNIQUE INDEX IF NOT EXISTS uq_users_single_admin ON users (is_admin) WHERE is_admin"))
    legacy_token = os.getenv("INGEST_API_TOKEN", "").strip()
    if legacy_token:
        digest = hashlib.sha256(legacy_token.encode("utf-8")).hexdigest()
        bind.execute(
            sa.text(
                """
                INSERT INTO ingest_tokens (user_id, token_hash, token_prefix, created_at)
                VALUES (:user_id, :token_hash, :prefix, now())
                ON CONFLICT (token_hash) DO NOTHING
                """
            ),
            {"user_id": admin_id, "token_hash": digest, "prefix": legacy_token[:15]},
        )


def downgrade() -> None:
    bind = op.get_bind()
    tables = set(inspect(bind).get_table_names())
    if "meals" in tables and "user_id" in {column["name"] for column in inspect(bind).get_columns("meals")}:
        op.drop_constraint("uq_meals_user_source_client_meal", "meals", type_="unique")
        op.create_unique_constraint("uq_meals_source_client_meal", "meals", ["source_device_id", "client_meal_id"])
        op.drop_index("ix_meals_user_id", table_name="meals")
        op.drop_constraint("fk_meals_user_id", "meals", type_="foreignkey")
        op.drop_column("meals", "user_id")
    if "devices" in tables and "user_id" in {column["name"] for column in inspect(bind).get_columns("devices")}:
        op.drop_constraint("uq_devices_user_device_id", "devices", type_="unique")
        op.create_unique_constraint("uq_devices_device_id", "devices", ["device_id"])
        op.drop_index("ix_devices_user_id", table_name="devices")
        op.drop_constraint("fk_devices_user_id", "devices", type_="foreignkey")
        op.drop_column("devices", "user_id")
    op.execute("DROP INDEX IF EXISTS uq_users_single_admin")
    for table in ("browser_sessions", "ingest_tokens", "users"):
        if table in set(inspect(bind).get_table_names()):
            op.drop_table(table)
