# Security and privacy

Never commit `.env`, tokens, session cookies, database dumps, health records, screenshots of private data, Android signing keys, or machine-specific SDK configuration.

This public repository starts from a sanitized source snapshot. Private deployment history, backups and live data are excluded. Automated secret scanning reduces risk but does not prove the absence of every possible secret.

Deploy behind HTTPS, set `SESSION_COOKIE_SECURE=true`, restrict allowed origins and network access, use unique strong credentials, and keep dependencies updated. Do not expose database ports. Rotate any credential that was ever published.

Do not include health records or credentials in public issues. Use GitHub private vulnerability reporting when enabled, or contact the repository owner privately.

Development APK releases use Android debug signing. Production distributors must create and protect their own release signing key.
