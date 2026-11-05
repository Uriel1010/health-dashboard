Fix meal timestamps displaying in the server timestamp offset instead of the phone's local timezone.

- Meal details, meal rows and the registration date/time picker now convert the saved instant to the phone timezone before display.
- For example, 18:51 UTC on 5 November displays as 20:51 in Asia/Jerusalem. Summer daylight saving and date changes across midnight use timezone rules rather than a fixed offset.
- Stored meal timestamps, server nutrition-day boundaries and notification delivery remain unchanged.

Regression coverage includes winter/summer offsets, already-local timestamps, midnight date rollover, another phone timezone, and an actual authenticated meal-detail display check.
