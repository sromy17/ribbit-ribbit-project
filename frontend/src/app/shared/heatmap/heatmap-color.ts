export const HEATMAP_MIN_CHANGE = -5;
export const HEATMAP_MAX_CHANGE = 5;

export function clampChange(change: number): number {
  return Math.min(HEATMAP_MAX_CHANGE, Math.max(HEATMAP_MIN_CHANGE, change));
}

export function getHeatmapColor(dailyChange: number): string {
  const change = Number.isFinite(dailyChange) ? dailyChange : 0;
  if (change >= 5) return '#0E4429';
  if (change >= 2) return '#006D32';
  if (change > 0) return '#1b3320';
  if (change === 0) return '#21262d';
  if (change > -2) return '#3d1c1c';
  if (change > -5) return '#A61C1C';
  return '#8A0000';
}
