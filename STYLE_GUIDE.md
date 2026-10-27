# Health Dashboard Style Guide

## Layout System
- Page max width: `max-w-7xl`.
- Page padding: `px-4 sm:px-6 lg:px-8`; vertical rhythm uses `gap-6`.
- Sections are full-width dashboard bands/cards with `rounded-lg`, `border`, `bg-app-surface` or `bg-app-elevated`, and `shadow-soft`.
- Mobile is single column; tablet uses two-column controls/cards; desktop uses dense grids and a sync-history plus device sidebar layout.
- Wide tables sit in an `overflow-x-auto` region with a clear accessible label.

## Typography
- UI text stays English.
- Dates use `DD/MM/YYYY HH:mm` via `en-GB` format parts and `dir="ltr"` where displayed inline.
- Section headings use compact dashboard scale: `text-lg font-semibold`; the app title uses `text-3xl font-semibold`.
- Muted explanatory text uses `text-sm leading-6 text-app-muted`.

## Tokens
- Background: `app.bg` light `#f5f7fb`, dark `#0c121c`.
- Surface: `app.surface` light `#ffffff`, dark `#141d2b`.
- Elevated surface: `app.elevated` light `#fafcff`, dark `#182334`.
- Border: `app.border` light `#dbe2eb`, dark `#334155`.
- Text: `app.text` light `#0f172a`, dark `#ebf1f8`.
- Muted text: `app.muted` light `#5b697d`, dark `#9daabd`.
- Primary accent: blue `#2563eb`; success emerald; warning amber; error rose.

## Card Anatomy
- Metric cards include label, value, optional unit, metric accent dot, status badge, time range pill, explanation, and captured timestamp.
- Cards never show a bare dash. Missing states use `No data`, `Permission missing`, `Error`, or `Not synced yet`.
- Recent exercises is treated like a metric card with session count and latest snapshot timestamp.

## Status Badge Variants
- Fresh and OK: emerald tinted background/border with text.
- Delayed and Permission missing: amber tinted background/border with text.
- Stale and Error: rose tinted background/border with text.
- No data, Not synced yet, No sync: neutral elevated background with muted text.
- Badges must always include visible text; color is supplemental only.

## Chart States
- Trend cards use lightweight SVG line charts; no chart library required.
- Metric toggles are native checkboxes in rounded labels.
- Each chart shows a short accessible summary such as “37 usable points in this range”.
- Hover/focus points reveal a small tooltip with value and captured time.
- Empty chart state says: `Need at least 2 syncs for trend`.

## Table Expansion Behavior
- Recent sync table columns: captured, received, freshness, steps, distance, calories, status, details.
- Rows expand with a native button and `aria-expanded`.
- Expanded content shows permissions summary, errors summary, recent exercises preview, and a View raw snapshot action.
- Table remains horizontally scrollable on small screens and has a screen-reader caption.

## Modal Styling
- Raw snapshot modal uses a dim overlay, centered panel, visible Close button, Escape close, basic focus trap, and focus restoration.
- Warning copy must be prominent: raw snapshots may contain sensitive health data and the dashboard should stay private/LAN-only unless authentication or Cloudflare Access is added.
- JSON is shown in a scrollable dark code block with compact monospace text.

## Focus Styles
- All buttons, selects, checkboxes, table scroll regions, and chart points need visible focus indication.
- Use `focus-visible:ring-2 focus-visible:ring-app-accent/40` or equivalent.
- Do not remove outlines without replacing them.

## Empty, Error, And Loading States
- Loading/refresh state is announced with `aria-live`/`role="status"`.
- API errors are visible in rose/amber text and not hidden behind color only.
- No-device/no-snapshot empty state gives a short explanation and safe curl example.
- Missing metrics use explicit words instead of symbols.
