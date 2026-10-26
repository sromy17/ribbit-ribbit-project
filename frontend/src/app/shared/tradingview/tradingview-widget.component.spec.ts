import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TradingviewWidgetComponent } from './tradingview-widget.component';

describe('TradingView widget lifecycle', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    Reflect.deleteProperty(window, 'TradingView');
  });

  it('creates an autosized dark chart, updates symbols, and cleans up', async () => {
    const remove = vi.fn();
    const widget = vi.fn(function (options: { container_id: string }) {
      return { id: options.container_id, remove };
    });
    Object.defineProperty(window, 'TradingView', { configurable: true, value: { widget } });
    const fixture = TestBed.createComponent(TradingviewWidgetComponent);
    fixture.componentRef.setInput('symbol', 'NYSE:MMM');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(widget).toHaveBeenCalledWith(expect.objectContaining({
      symbol: 'NYSE:MMM', autosize: true, theme: 'dark', interval: 'D',
      container_id: fixture.componentInstance.containerId
    }));
    fixture.componentRef.setInput('symbol', 'NASDAQ:AAPL');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(remove).toHaveBeenCalledTimes(1);
    expect(widget).toHaveBeenLastCalledWith(expect.objectContaining({ symbol: 'NASDAQ:AAPL' }));
    fixture.destroy();
    expect(remove).toHaveBeenCalledTimes(2);
  });

  it('does not create a widget after the view is destroyed while awaiting initialization', async () => {
    const widget = vi.fn();
    Object.defineProperty(window, 'TradingView', { configurable: true, value: { widget } });
    const fixture = TestBed.createComponent(TradingviewWidgetComponent);
    fixture.detectChanges();
    fixture.destroy();
    await Promise.resolve();
    expect(widget).not.toHaveBeenCalled();
  });

  it('does not call the vendor removal API for an already detached iframe', async () => {
    const remove = vi.fn(() => { throw new Error('Detached iframe'); });
    const widget = vi.fn(function () { return { id: 'already-detached-frame', remove }; });
    Object.defineProperty(window, 'TradingView', { configurable: true, value: { widget } });
    const fixture = TestBed.createComponent(TradingviewWidgetComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(() => fixture.destroy()).not.toThrow();
    expect(remove).not.toHaveBeenCalled();
  });
});
