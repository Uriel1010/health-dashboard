from datetime import date, datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import Boolean, CheckConstraint, Date, DateTime, ForeignKey, Index, Integer, Numeric, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        UniqueConstraint("username_normalized", name="uq_users_username_normalized"),
        CheckConstraint("nutrition_reset_hour >= 0 AND nutrition_reset_hour <= 23", name="ck_users_reset_hour"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), nullable=False)
    username_normalized: Mapped[str] = mapped_column(String(64), nullable=False)
    display_name: Mapped[str] = mapped_column(String(100), nullable=False)
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)
    is_admin: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    must_change_password: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    nutrition_reset_hour: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    nutrition_goal_calories_kcal: Mapped[int] = mapped_column(Integer, nullable=False, default=2200)
    nutrition_goal_protein_g: Mapped[int] = mapped_column(Integer, nullable=False, default=140)
    nutrition_goal_carbs_g: Mapped[int] = mapped_column(Integer, nullable=False, default=250)
    nutrition_goal_fat_g: Mapped[int] = mapped_column(Integer, nullable=False, default=75)
    nutrition_goal_fiber_g: Mapped[int] = mapped_column(Integer, nullable=False, default=30)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())

    devices: Mapped[list["Device"]] = relationship(back_populates="user")
    meals: Mapped[list["Meal"]] = relationship(back_populates="user")
    sessions: Mapped[list["BrowserSession"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    ingest_tokens: Mapped[list["IngestToken"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class BrowserSession(Base):
    __tablename__ = "browser_sessions"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    last_used_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    user: Mapped[User] = relationship(back_populates="sessions")


class IngestToken(Base):
    __tablename__ = "ingest_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    token_prefix: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)

    user: Mapped[User] = relationship(back_populates="ingest_tokens")


class Device(Base):
    __tablename__ = "devices"
    __table_args__ = (UniqueConstraint("user_id", "device_id", name="uq_devices_user_device_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"), nullable=False, index=True)
    device_id: Mapped[str] = mapped_column(Text, nullable=False, index=True)
    device_name: Mapped[str | None] = mapped_column(Text)
    first_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    snapshots: Mapped[list["HealthSnapshot"]] = relationship(back_populates="device")
    user: Mapped[User] = relationship(back_populates="devices")


class HealthSnapshot(Base):
    __tablename__ = "health_snapshots"
    __table_args__ = (
        Index("ix_health_snapshots_device_captured", "device_fk", "captured_at"),
        Index("ix_health_snapshots_device_received", "device_fk", "received_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    device_fk: Mapped[int] = mapped_column(ForeignKey("devices.id"), nullable=False)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now(), index=True)
    timezone: Mapped[str | None] = mapped_column(String(128))

    steps_today: Mapped[int | None] = mapped_column(Integer)
    distance_km_today: Mapped[Decimal | None] = mapped_column(Numeric(10, 3))
    active_calories_kcal_today: Mapped[int | None] = mapped_column(Integer)
    total_calories_kcal_today: Mapped[int | None] = mapped_column(Integer)
    latest_heart_rate_bpm: Mapped[int | None] = mapped_column(Integer)
    resting_heart_rate_bpm: Mapped[int | None] = mapped_column(Integer)
    sleep_minutes_last_night: Mapped[int | None] = mapped_column(Integer)
    latest_weight_kg: Mapped[Decimal | None] = mapped_column(Numeric(10, 3))

    recent_exercises_json: Mapped[Any | None] = mapped_column(JSONB)
    permissions_json: Mapped[Any | None] = mapped_column(JSONB)
    errors_json: Mapped[Any | None] = mapped_column(JSONB)
    raw_payload_json: Mapped[Any] = mapped_column(JSONB, nullable=False)

    device: Mapped[Device] = relationship(back_populates="snapshots")


class Meal(Base):
    __tablename__ = "meals"
    __table_args__ = (
        UniqueConstraint("user_id", "source_device_id", "client_meal_id", name="uq_meals_user_source_client_meal"),
        Index("ix_meals_local_date", "local_date"),
        Index("ix_meals_source_device_local_date", "source_device_id", "local_date"),
        Index("ix_meals_eaten_at", "eaten_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"), nullable=False, index=True)
    client_meal_id: Mapped[str | None] = mapped_column(Text)
    source_device_id: Mapped[str] = mapped_column(Text, nullable=False)
    eaten_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    local_date: Mapped[date] = mapped_column(Date, nullable=False)
    timezone: Mapped[str] = mapped_column(String(128), nullable=False, default="Asia/Jerusalem")
    meal_type: Mapped[str] = mapped_column(String(32), nullable=False)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    notes: Mapped[str | None] = mapped_column(Text)

    calories_kcal: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, default=0)
    protein_g: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, default=0)
    carbs_g: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, default=0)
    fat_g: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, default=0)
    fiber_g: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    sugar_g: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    sodium_mg: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    user: Mapped[User] = relationship(back_populates="meals")
