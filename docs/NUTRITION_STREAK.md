# Uncapped nutrition streak

The Home Assistant sensor previously counted only the 30 visible trend points.
The API now returns `currentStreakDays` separately, computed from the current
user's complete active meal history. The 30-point chart and rolling averages
are unchanged. Personal timezone and reset-hour preferences determine day
boundaries; an unfinished current day may inherit yesterday's streak. Missing
days break the streak; duplicate meals, deleted meals, future nutrition days
and meals belonging to other users cannot inflate it.

The sensor prefers the authoritative server value and retains the old chart
fallback for older servers. A server-provided zero is a valid value.

Validated with 26 backend tests and four sensor-function tests. Tests cover a
90-day streak, duplicate meals, missing days, an unfinished current day,
deleted meals, user isolation, 04:00 Israel boundaries, and preservation of
the 30-point response. The existing signed-journal tests also passed.

Deployed on 2026-11-01. HealthConnect's response reports 79 days for Ido.
The live Home Assistant recorder subsequently stored 79 for
`sensor.healthconnect_nutrition_streak`. The API/database health checks and
the signed journal connection passed after deployment. No meal was changed.

Deployment uses the existing private bridge Compose override with a new API
image. Backend source and the old image reference were backed up before the
update. The integration sensor was backed up separately inside the authorized
Home Assistant project. Its configuration check passed before restarting only
the instance at 192.168.31.140. No database migration was required.

The integration patch is local deployment code; no upstream PR was created.
Preserve/reapply this change when updating the integration through HACS until
an upstream release includes the authoritative streak field.
