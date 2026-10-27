import { describe, expect, it } from 'vitest';
import { Order } from '../../../core/services/api.service';
import { sortTradeHistory } from './trade-history-sort';
const order = (id: string, patch: Partial<Order> = {}): Order => ({orderId: id, accountId: '1', symbol: 'AAPL', side: 'BUY', status: 'EXECUTED', quantity: 1, ...patch});
const ids = (rows: Order[]) => rows.map(row => row.orderId);
describe('Trade history sorting', () => {
  it('uses natural ID order and does not mutate the API response', () => {
    const rows = [order('10'), order('2')];
    expect(ids(sortTradeHistory(rows, 'orderId', 'asc', null))).toEqual(['2', '10']);
    expect(ids(sortTradeHistory(rows, 'orderId', 'desc', null))).toEqual(['10', '2']);
    expect(ids(rows)).toEqual(['10', '2']);
  });
  it('sorts monetary amounts numerically and keeps missing values last in both directions', () => {
    const rows = [order('1', {price: 100}), order('2', {price: 9}), order('3')];
    expect(ids(sortTradeHistory(rows, 'price', 'asc', null))).toEqual(['2', '1', '3']);
    expect(ids(sortTradeHistory(rows, 'price', 'desc', null))).toEqual(['1', '2', '3']);
  });
  it('compares instants across timezones, supports date-only history and handles invalid dates', () => {
    const rows = [order('1', {createdAtUtc:'2026-01-02T01:00:00+02:00'}), order('2', {dateRequested:'2026-01-02'}), order('3', {createdAtUtc:'invalid'})];
    expect(ids(sortTradeHistory(rows, 'date', 'asc', null))).toEqual(['1', '2', '3']);
    expect(ids(sortTradeHistory(rows, 'date', 'desc', null))).toEqual(['2', '1', '3']);
  });
  it('sorts every other category with deterministic ties and zero execution values', () => {
    const rows = [order('2', {symbol:'MSFT',side:'SELL',status:'REJECTED',quantity:10,executedNotional:0}), order('1',{quantity:2,executedNotional:20})];
    for (const key of ['symbol','side','status','quantity'] as const) expect(ids(sortTradeHistory(rows,key,'asc',null))).toEqual(['1','2']);
    expect(ids(sortTradeHistory(rows,'executedNotional','asc',null))).toEqual(['2','1']);
    expect(ids(sortTradeHistory(rows,'disputes','asc',null))).toEqual(['1','2']);
    const summaries = {'1':{total:2,active:2,resolved:0,status:'UNDER_REVIEW' as const},'2':{total:1,active:0,resolved:1,status:'RESOLVED' as const}};
    expect(ids(sortTradeHistory(rows,'disputes','asc',summaries))).toEqual(['2','1']);
    expect(ids(sortTradeHistory(rows,'disputes','desc',summaries))).toEqual(['1','2']);
  });
});
