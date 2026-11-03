"""Account-bound push registrations and transactional meal notification outbox."""
from alembic import op
import sqlalchemy as sa

revision = "20261104_03"
down_revision = "20260821_02"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("push_installations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("secret_hash", sa.String(64), nullable=False),
        sa.Column("user_id", sa.Integer, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("session_id", sa.Integer, sa.ForeignKey("browser_sessions.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token", sa.Text, nullable=False),
        sa.Column("token_hash", sa.String(64), nullable=False, unique=True),
        sa.Column("binding", sa.String(64), nullable=False),
        sa.Column("enabled", sa.Boolean, nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_push_installations_user_id", "push_installations", ["user_id"])
    op.create_table("meal_push_deliveries",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column("installation_id", sa.String(36), sa.ForeignKey("push_installations.id", ondelete="CASCADE"), nullable=False),
        sa.Column("meal_id", sa.Integer, sa.ForeignKey("meals.id", ondelete="CASCADE"), nullable=False),
        sa.Column("binding", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("attempts", sa.Integer, nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.UniqueConstraint("installation_id", "meal_id", name="uq_push_installation_meal"))
    op.create_index("ix_meal_push_deliveries_next_attempt_at", "meal_push_deliveries", ["next_attempt_at"])
    op.create_index("ix_meal_push_deliveries_completed_at", "meal_push_deliveries", ["completed_at"])


def downgrade():
    op.drop_table("meal_push_deliveries")
    op.drop_table("push_installations")
