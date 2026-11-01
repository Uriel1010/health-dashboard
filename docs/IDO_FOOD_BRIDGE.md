# Ido food journal bridge

The existing private Sites journal uses a separate PS256 public-key grant.
Personal ingest tokens are neither read nor transferred. The private key is a
Sites runtime secret; the server stores only the public verification key and
the approved account binding. No secret belongs in source control.

The signed bridge exposes a connection status check, meal creation/retry,
and correction/deletion of meals created by this journal. It does not expose
other applications' meals, account administration or health snapshots.
Signatures bind issuer, owner, HTTP method, destination, exact body and expiry.
A database-backed unique nonce rejects replay across server workers.

Configuration names:

- Sites: `BRIDGE_PRIVATE_KEY` (secret), `BRIDGE_KEY_ID`, `SYNC_OWNER_ID`.
- API: `FOOD_BRIDGE_GRANT_FILE` points to a protected JSON grant containing
  `enabled`, `key_id`, `sites_owner_id`, `user_id`, `username_normalized`, and
  `public_key_pem`. Verify account and owner independently before enabling.

The nullable `users.nutrition_timezone` overrides the server timezone only for
the selected account. NULL preserves the existing server timezone. The approved
Ido preference is 04:00 in Asia/Jerusalem. Other users are not updated.

Migration `20261101_03` adds the timezone column and nonce table; it does not
rewrite or import meals. Back up the database before applying it. Keep the
existing deployment's Compose files and add the private bridge override:

```sh
docker compose -p health-dashboard -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.ido-food-bridge.yml up -d --no-deps --no-build api
```

The override, public-key grant, backups, build logs and host paths are local
deployment state excluded from commits. Preserve the third Compose file during
future updates. Rebuilding requires the same reviewed backend source and an
explicit image update in the local override. Docker's restart policy preserves
the current container configuration across ordinary host restarts.

Revoke by atomically setting the server grant's `enabled` to false; each request
re-reads it. Remove the Sites private-key secret and republish if retiring the
connection. Personal ingest tokens need no rotation. For API rollback use the
previous image recorded in the protected backup metadata, leaving the additive
migration in place. Restore an account preference only with explicit intent;
do not restore a full database over subsequent real meals.

Verification: `journal_status` must return `configured: true`,
`connection.authenticated: true`, username `ido`, timezone `Asia/Jerusalem`,
and `dayStartsAt: 04:00`. This checks the real Sites-to-API connection without
creating a meal. Its replay nonce is an expected authentication write. Verify
an unauthenticated bridge request is rejected. Await a real user meal to verify
production meal creation; never create sample meals or import history.

Isolated validation:

```sh
cd backend
python -m unittest discover -s tests
```

The matching Site has bridge, journal and credential tests and a full Worker
build. Tests use ephemeral keys, temporary SQLite and mocked networking.
