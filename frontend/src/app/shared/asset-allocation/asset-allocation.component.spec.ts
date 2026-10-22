import { describe, expect, it } from 'vitest';
import { AssetAllocationComponent } from './asset-allocation.component';

describe('Asset allocation', () => {
  it('includes cash and values positions at current price', () => {
    const component = new AssetAllocationComponent();
    component.state = {
      accountNumber: '1', cash: 500, orders: [], history: [],
      holdings: [
        { symbol: 'AAPL', shares: 2, avgPrice: 80, currentPrice: 100 },
        { symbol: 'MSFT', shares: 1, avgPrice: 200, currentPrice: 300 }
      ]
    };
    component.ngOnChanges();
    expect(component.total).toBe(1000);
    expect(component.slices.map(s => [s.symbol, s.percentage, s.offset]))
      .toEqual([['AAPL', 20, 0], ['MSFT', 30, 20], ['Cash', 50, 50]]);
    component.state = { accountNumber: '2', cash: 200, orders: [], history: [], holdings: [] };
    component.ngOnChanges();
    expect(component.total).toBe(200);
    expect(component.slices.map(s => [s.symbol, s.percentage])).toEqual([['Cash', 100]]);
    component.state = { ...component.state, cash: 0 };
    component.ngOnChanges();
    expect(component.slices).toEqual([]);
  });
});
