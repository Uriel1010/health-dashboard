# Daily nutrition goals

Goals belong to the signed-in dashboard account, not to a particular phone. Both clients use the same `nutritionGoals` fields in `/api/v1/account/preferences`.

## Web dashboard

Open **Daily fuel > Daily goals** and edit Calories, Protein, Carbs, Fat or Fiber. The existing dashboard saves changes to the account. Refresh another client to retrieve those changes.

## Android

Open **Settings > Daily nutrition goals**, or **Today > Edit goals**. The editor loads the account's current goals. Enter whole-number daily targets and tap **Save goals**. **Cancel** restores the last loaded/saved values. Failed saves preserve edits. Authentication uses the dashboard session, not the ingest token.

The server accepts calories from 0 to 10,000 kcal, protein/carbs/fat from 0 to 2,000 g, and fiber from 0 to 500 g. These are API validation limits, not recommended nutritional targets.

Successful Android saves update local progress and widgets immediately, then refresh server meals. Edits from the dashboard reach Android on refresh or scheduled sync. Widgets continue to show their calorie/protein/fiber subset; Today also shows carbs and fat. Nutrition-day reset hour and timezone are unchanged when saving goals.

No database migration is required. Each account's goals remain private to that account.
