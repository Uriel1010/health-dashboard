Account-synced daily nutrition goals.

- Edit calories, protein, carbs, fat and fiber from Settings > Daily nutrition goals.
- Open the editor directly from Today's Edit goals action.
- Load and save goals using the same account preferences API as the web dashboard.
- Explicit Save/Cancel controls, whole-number/range validation, loading and error feedback.
- Preserve unsaved edits on failed saves; refresh Today and widgets after successful saves.
- Show carbs and fat progress in Today alongside protein and fiber.

The web dashboard already supports these same goals under Daily goals. No database migration is required. Goals edited in either client are fetched by the other on refresh/sync. Android widgets display their existing calorie/protein/fiber subset with the updated goals.
