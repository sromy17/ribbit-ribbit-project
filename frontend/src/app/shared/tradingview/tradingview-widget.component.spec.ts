import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TradingviewWidgetComponent } from './tradingview-widget.component';

describe('TradingView widget lifecycle', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    Reflect.deleteProperty(window, 'TradingView');
    document.body.classList.remove('theme-dark');
  });

  it('renders international closing prices without loading a blocked vendor widget', async () => {
    const widget = vi.fn();
    const get = vi.fn(() => of({ currency: 'GBP', points: [
      { atUtc: '2026-10-01T15:00:00Z', price: 12 },
      { atUtc: '2026-10-28T15:00:00Z', price: 13 }
    ] }));
    TestBed.configureTestingModule({ providers: [{ provide: HttpClient, useValue: { get } }] });
    Object.defineProperty(window, 'TradingView', { configurable: true, value: { widget } });
    const fixture = TestBed.createComponent(TradingviewWidgetComponent);
    fixture.componentRef.setInput('symbol', 'LSE:HSBA');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(get).toHaveBeenCalledWith('/api/market/history/HSBA.L');
    expect(widget).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.native-chart').textContent).toContain('GBP');
    expect(fixture.nativeElement.querySelector('polyline').getAttribute('points')).toContain('600,20');
    expect(fixture.nativeElement.querySelector('polyline').getAttribute('stroke')).toBe('var(--ui-success)');
  });

  it('creates an autosized dark chart, updates symbols, and cleans up', async () => {
    document.body.classList.add('theme-dark');
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

  it('updates an open chart when the shared theme changes', async () => {
    const widget = vi.fn(function (options: { container_id: string }) {
      return { id: options.container_id, remove: vi.fn() };
    });
    Object.defineProperty(window, 'TradingView', { configurable: true, value: { widget } });
    document.body.classList.remove('theme-dark');
    const fixture = TestBed.createComponent(TradingviewWidgetComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(widget).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'light' }));
    document.body.classList.add('theme-dark');
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    expect(widget).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'dark' }));
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
