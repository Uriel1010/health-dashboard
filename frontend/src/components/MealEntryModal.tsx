import { useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";

import type { ManualMealPayload } from "../apiV2";

type MealEntryModalProps = {
  nutritionDate: string;
  currentNutritionDate: string;
  resetHour: number;
  timezone: string;
  onClose: () => void;
  onSave: (payload: ManualMealPayload, nutritionDate: string) => Promise<void>;
};

const inputClass = "mt-2 w-full rounded-xl border border-app-border bg-app-elevated px-3 py-2.5 text-app-text outline-none transition focus:border-app-accent focus:ring-2 focus:ring-app-accent/20";
const mealTypes = ["breakfast", "lunch", "dinner", "snack", "other"] as const;

export function MealEntryModal({
  nutritionDate,
  currentNutritionDate,
  resetHour,
  timezone,
  onClose,
  onSave,
}: MealEntryModalProps) {
  const [date, setDate] = useState(nutritionDate);
  const [mealTime, setMealTime] = useState(() => nutritionDate === currentNutritionDate ? timeInZone(timezone) : "12:00");
  const [mealType, setMealType] = useState<(typeof mealTypes)[number]>("lunch");
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");
  const [calories, setCalories] = useState("");
  const [protein, setProtein] = useState("");
  const [carbs, setCarbs] = useState("");
  const [fat, setFat] = useState("");
  const [fiber, setFiber] = useState("");
  const [sugar, setSugar] = useState("");
  const [sodium, setSodium] = useState("");
  const [advanced, setAdvanced] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape" && !busy) onClose();
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [busy, onClose]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const payload: ManualMealPayload = {
        clientMealId: `dashboard-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`,
        deviceId: "dashboard-manual",
        eatenAt: timestampForNutritionDate(date, mealTime, resetHour),
        timezone,
        mealType,
        title: title.trim(),
        caloriesKcal: requiredNumber(calories),
        proteinG: requiredNumber(protein),
        carbsG: requiredNumber(carbs),
        fatG: requiredNumber(fat),
      };
      if (notes.trim()) payload.notes = notes.trim();
      if (fiber !== "") payload.fiberG = requiredNumber(fiber);
      if (sugar !== "") payload.sugarG = requiredNumber(sugar);
      if (sodium !== "") payload.sodiumMg = requiredNumber(sodium);
      await onSave(payload, date);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save this meal.");
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid items-end bg-slate-950/60 p-0 backdrop-blur-sm sm:place-items-center sm:p-5" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !busy) onClose();
    }}>
      <section role="dialog" aria-modal="true" aria-labelledby="meal-entry-title"
        className="max-h-[94vh] w-full overflow-y-auto rounded-t-[2rem] border border-app-border bg-app-surface shadow-[0_30px_100px_rgba(0,0,0,.35)] sm:max-w-2xl sm:rounded-[2rem]">
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-app-border bg-app-surface/95 px-5 py-5 backdrop-blur sm:px-7">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-app-accent">Quick log</p>
            <h2 id="meal-entry-title" className="mt-1 text-2xl font-semibold tracking-tight">Add a meal</h2>
            <p className="mt-1 text-sm text-app-muted">Macros are added to your selected nutrition day.</p>
          </div>
          <button type="button" onClick={onClose} disabled={busy} aria-label="Close meal form"
            className="grid h-10 w-10 shrink-0 place-items-center rounded-full border border-app-border text-xl text-app-muted transition hover:border-app-accent hover:text-app-text disabled:opacity-50">×</button>
        </div>

        <form onSubmit={submit} className="space-y-5 px-5 py-6 sm:px-7">
          <div className="grid gap-4 sm:grid-cols-[1fr_180px]">
            <MealField label="Meal name">
              <input required autoFocus maxLength={512} value={title} onChange={(event) => setTitle(event.target.value)}
                placeholder="Chicken, rice and salad" className={inputClass} />
            </MealField>
            <MealField label="Meal type">
              <select value={mealType} onChange={(event) => setMealType(event.target.value as typeof mealType)} className={inputClass}>
                {mealTypes.map((type) => <option key={type} value={type}>{type[0].toUpperCase() + type.slice(1)}</option>)}
              </select>
            </MealField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <MealField label="Nutrition date">
              <input required type="date" value={date} onChange={(event) => setDate(event.target.value)} className={inputClass} />
            </MealField>
            <MealField label="Time">
              <input required type="time" value={mealTime} onChange={(event) => setMealTime(event.target.value)} className={inputClass} />
            </MealField>
          </div>
          <p className="-mt-2 text-xs leading-5 text-app-muted">
            Your nutrition day starts at {String(resetHour).padStart(2, "0")}:00 in {timezone}. Early-morning entries are placed on the following calendar date automatically.
          </p>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <NumberField label="Calories" unit="kcal" value={calories} onChange={setCalories} max={10000} />
            <NumberField label="Protein" unit="g" value={protein} onChange={setProtein} max={2000} />
            <NumberField label="Carbs" unit="g" value={carbs} onChange={setCarbs} max={2000} />
            <NumberField label="Fat" unit="g" value={fat} onChange={setFat} max={2000} />
          </div>

          <button type="button" onClick={() => setAdvanced((value) => !value)}
            className="flex min-h-11 w-full items-center justify-between rounded-xl border border-app-border bg-app-elevated px-4 py-3 text-left text-sm font-semibold">
            More nutrition details <span className="text-app-muted">{advanced ? "−" : "+"}</span>
          </button>

          {advanced ? <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <NumberField label="Fiber" unit="g" value={fiber} onChange={setFiber} max={500} optional />
            <NumberField label="Sugar" unit="g" value={sugar} onChange={setSugar} max={1000} optional />
            <NumberField label="Sodium" unit="mg" value={sodium} onChange={setSodium} max={100000} optional />
          </div> : null}

          <MealField label="Notes (optional)">
            <textarea rows={3} maxLength={4000} value={notes} onChange={(event) => setNotes(event.target.value)}
              placeholder="Portion, ingredients, or anything worth remembering" className={inputClass + " resize-y"} />
          </MealField>

          {error ? <p role="alert" className="rounded-xl border border-rose-400/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">{error}</p> : null}

          <div className="flex flex-col-reverse gap-3 border-t border-app-border pt-5 sm:flex-row sm:justify-end">
            <button type="button" onClick={onClose} disabled={busy}
              className="min-h-11 rounded-xl border border-app-border px-5 py-2.5 font-semibold transition hover:border-app-accent disabled:opacity-50">Cancel</button>
            <button disabled={busy}
              className="min-h-11 rounded-xl bg-app-accent px-6 py-2.5 font-semibold text-white shadow-lg shadow-blue-500/20 transition hover:brightness-110 disabled:cursor-wait disabled:opacity-60">
              {busy ? "Saving meal…" : "Add to daily fuel"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function MealField({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block text-sm font-semibold text-app-muted">{label}{children}</label>;
}

function NumberField({ label, unit, value, onChange, max, optional = false }: {
  label: string;
  unit: string;
  value: string;
  onChange: (value: string) => void;
  max: number;
  optional?: boolean;
}) {
  return <MealField label={label}>
    <div className="relative">
      <input required={!optional} type="number" min="0" max={max} step="0.1" inputMode="decimal" value={value}
        onChange={(event) => onChange(event.target.value)} placeholder={optional ? "—" : "0"}
        className={inputClass + " pr-12"} />
      <span className="pointer-events-none absolute bottom-2.5 right-3 text-xs font-bold text-app-muted">{unit}</span>
    </div>
  </MealField>;
}

function requiredNumber(value: string) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error("Nutrition values must be zero or greater.");
  return parsed;
}

function timestampForNutritionDate(nutritionDate: string, mealTime: string, resetHour: number) {
  const hour = Number(mealTime.split(":")[0]);
  const calendarDate = hour < resetHour ? addDays(nutritionDate, 1) : nutritionDate;
  return `${calendarDate}T${mealTime}:00`;
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function timeInZone(timezone: string) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());
  const hour = parts.find((part) => part.type === "hour")?.value ?? "12";
  const minute = parts.find((part) => part.type === "minute")?.value ?? "00";
  return `${hour}:${minute}`;
}
