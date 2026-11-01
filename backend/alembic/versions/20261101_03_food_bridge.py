"""Persist one-use signed nutrition requests."""
from alembic import op
import sqlalchemy as sa

revision = "20261101_03"
down_revision = "20260821_02"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("users", sa.Column("nutrition_timezone", sa.String(128), nullable=True))
    op.create_table("food_bridge_nonces", sa.Column("nonce", sa.String(36), primary_key=True), sa.Column("expires_at", sa.BigInteger(), nullable=False))
    op.create_index("ix_food_bridge_nonces_expires_at", "food_bridge_nonces", ["expires_at"])


def downgrade():
    op.drop_table("food_bridge_nonces")
    op.drop_column("users", "nutrition_timezone")
