# Google Stitch Design Brief

Paste this prompt into Google Stitch:

```text
Design a dark, modern, responsive health dashboard web app for a self-hosted Android Health Connect snapshot server. The product name is Health Dashboard. The app is used by one person on a trusted LAN to inspect health metrics synced from an Android phone every 15 minutes. This is not a marketing page; the first screen is the working dashboard.

Create a polished Google/Fitbit-inspired dashboard with a calm operational feel: clear hierarchy, dark slate surfaces, crisp borders, subtle shadows, 8px rounded cards, high contrast text, and restrained metric accent colors. Keep all UI text in English. All dates must use numeric DD/MM/YYYY HH:mm formatting and avoid RTL/Hebrew date ordering.

Required layout:
- Top sync status header with Health Dashboard title, selected device name and id, latest sync age, captured time, received time, timezone, and freshness badge. Freshness states: Fresh under 20 minutes, Delayed 20-60 minutes, Stale over 60 minutes. Badges must include text, not color alone.
- Controls section with device selector, date range selector, manual Refresh button, and auto-refresh toggle. Date ranges: Today, Last 24 hours, Last 7 days, Last 30 days.
- Metric card grid for Steps, Distance, Active calories, Total calories, Latest heart rate, Resting heart rate, Sleep, Weight, and Recent exercises. Each card includes value, unit, time range, status badge, short explanation, and captured timestamp. Metric statuses: OK, No data, Permission missing, Error, Not synced yet.
- Trend charts section with checkbox toggles for steps, distance, active calories, and total calories. Charts are lightweight line charts with hover/focus tooltips. If fewer than two usable points exist, show “Need at least 2 syncs for trend”.
- Recent sync history table with columns for captured, received, freshness, steps, distance, calories, status, and details. Rows can expand to show permissions summary, errors summary, recent exercises preview, and a View raw snapshot action.
- Raw snapshot/debug modal with pretty JSON and a prominent warning: “Raw snapshot may contain sensitive health data. Keep this dashboard private/LAN-only unless authentication or Cloudflare Access is added.”
- Devices/debug side area that supports multiple Android devices and makes the selected device obvious.

Accessibility requirements:
- Semantic headings and landmarks.
- Native select, button, and checkbox controls.
- Visible focus states on all interactive elements.
- aria-live/status messaging for loading, refresh, and errors.
- Modal closes with Escape, has a visible Close button, and keeps focus inside while open.
- Table has a caption and remains usable with horizontal scrolling on small screens.
- Charts have accessible summaries and keyboard-focusable points or equivalent labels.
- Do not rely on color alone for status; every status badge includes text.

Responsive behavior:
- Mobile: single-column layout, compact controls, readable cards, horizontally scrollable table, full-width modal.
- Tablet: two-column cards and controls.
- Desktop: broad status header, dense card grid, two-column chart area, recent syncs plus devices/debug sidebar.

Avoid decorative blobs, heavy gradients, marketing hero sections, and clutter. Prioritize scanability, trustworthy data presentation, and privacy-aware debugging.
```
