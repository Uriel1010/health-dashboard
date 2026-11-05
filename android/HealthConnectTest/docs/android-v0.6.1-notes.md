Clearer, localized meal notifications matching the approved notification concept.

- Correct Health Companion app branding and a heart-and-pulse notification glyph.
- Meal names are the notification titles, without the repetitive New meal prefix.
- Actual calories and protein replace the unhelpful Other label. Hebrew notification text follows a Hebrew device language; English remains the fallback. Mixed-direction titles are isolated for readable RTL/LTR text.
- A silent group summary counts the remaining meal notifications. Children still alert separately; removing or opening a meal updates the summary without adding another alert.
- View meal opens that specific meal in a themed, authenticated detail screen, with full title, nutrition, time and an All meals action. Deleted/unavailable meals and account changes have explicit error states.
- Preserved notification channel ID, permissions, server push and existing duplicate suppression. Private notification visibility respects lock-screen privacy settings.

Android controls the native card shape, typography and wallpaper transparency; Samsung and other launchers may render the same notification differently. No backend update is required beyond the existing 0.3.1 push endpoints.

Verified with debug/unit builds and four emulator checks for English/Hebrew content, grouping/counts, duplicate suppression, exact-meal navigation and account isolation. Production packaging retains the established signing identity.
