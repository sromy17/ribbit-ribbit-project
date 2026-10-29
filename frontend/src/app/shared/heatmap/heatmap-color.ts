export const HEATMAP_MIN_CHANGE = -5;
export const HEATMAP_MAX_CHANGE = 5;

export function clampChange(change: number): number {
  return Math.min(HEATMAP_MAX_CHANGE, Math.max(HEATMAP_MIN_CHANGE, change));
}

export function getHeatmapColor(dailyChange: number): string {
  const change = Number.isFinite(dailyChange) ? dailyChange : 0;
  if (change >= 5) return 'var(--heatmap-up-3)';
  if (change >= 2) return 'var(--heatmap-up-2)';
  if (change > 0) return 'var(--heatmap-up-1)';
  if (change === 0) return 'var(--ui-subtle)';
  if (change > -2) return 'var(--heatmap-down-1)';
  if (change > -5) return 'var(--heatmap-down-2)';
  return 'var(--heatmap-down-3)';
}
