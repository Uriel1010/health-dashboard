"""Persist per-user nutrition goals."""

from alembic import op
import sqlalchemy as sa

revision = "20260821_02"
down_revision = "20260820_01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    columns = (
        ("nutrition_goal_calories_kcal", 2200),
        ("nutrition_goal_protein_g", 140),
        ("nutrition_goal_carbs_g", 250),
        ("nutrition_goal_fat_g", 75),
        ("nutrition_goal_fiber_g", 30),
    )
    for name, default in columns:
        op.add_column("users", sa.Column(name, sa.Integer(), nullable=False, server_default=str(default)))
        op.alter_column("users", name, server_default=None)


def downgrade() -> None:
    for name in (
        "nutrition_goal_fiber_g", "nutrition_goal_fat_g", "nutrition_goal_carbs_g",
        "nutrition_goal_protein_g", "nutrition_goal_calories_kcal",
    ):
        op.drop_column("users", name)
