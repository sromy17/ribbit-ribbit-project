import { Order, TradeDisputeSummary } from '../../../core/services/api.service';

export type HistorySortKey = 'orderId' | 'date' | 'symbol' | 'side' | 'status' | 'quantity' | 'price' | 'executedNotional' | 'disputes';
export type HistorySortDirection = 'asc' | 'desc';
const textOrder = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

/** Sort a copy of all loaded account orders. Missing evidence stays last in either direction. */
export function sortTradeHistory(orders: readonly Order[], key: HistorySortKey, direction: HistorySortDirection,
  summaries: Record<string, TradeDisputeSummary> | null): Order[] {
  const numeric = (value: unknown): number | null => {
    if (value == null || value === '') return null;
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  };
  const value = (order: Order): string | number | null => {
    switch (key) {
      case 'date': {
        const timestamp = order.createdAtUtc || order.dateRequested;
        return timestamp ? numeric(Date.parse(timestamp)) : null;
      }
      case 'quantity': case 'price': case 'executedNotional': return numeric(order[key]);
      case 'disputes': {
        if (!summaries) return null;
        const status = summaries[order.orderId]?.status || 'NONE';
        return status === 'UNDER_REVIEW' ? 'Under review' : status === 'RESOLVED' ? 'Resolved' : 'No disputes';
      }
      default: return order[key] || null;
    }
  };
  return [...orders].sort((a, b) => {
    const av = value(a), bv = value(b);
    if (av === null && bv !== null) return 1;
    if (bv === null && av !== null) return -1;
    const compared = av === null || bv === null ? 0 : typeof av === 'number' && typeof bv === 'number'
      ? av - bv : textOrder.compare(String(av), String(bv));
    return compared ? compared * (direction === 'asc' ? 1 : -1) : textOrder.compare(String(a.orderId), String(b.orderId));
  });
}
