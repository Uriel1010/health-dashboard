export type NutritionMetric = "caloriesKcal" | "proteinG" | "carbsG" | "fatG" | "fiberG";
export type NutritionValues = Record<NutritionMetric, number | null>;
export interface NutritionWindow {
  days: number; fromDate: string; toDate: string; loggedDays: number;
  average: NutritionValues; coverage: Record<NutritionMetric, number>;
}
export interface NutritionStatistics {
  fromDate: string; toDate: string; currentNutritionDate: string;
  serverTimezone: string; nutritionResetHour: number;
  points: { date: string; hasMeals: boolean; totals: NutritionValues }[];
  windows: NutritionWindow[];
}
