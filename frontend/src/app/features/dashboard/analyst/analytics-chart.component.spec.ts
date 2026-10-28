import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { AnalyticsChartComponent } from './analytics-chart.component';

describe('Analyst chart scales and accessible values', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('plots negative net deposits below zero and supplies exact accessible table guidance', () => {
    const fixture = TestBed.createComponent(AnalyticsChartComponent);
    fixture.componentRef.setInput('title', 'Weekly funding');
    fixture.componentRef.setInput('unit', 'USD');
    fixture.componentRef.setInput('series', [{ label: 'Deposits', kind: 'bar' }, { label: 'Net deposits', kind: 'line' }]);
    fixture.componentRef.setInput('points', [
      { label: '2026-10-19', values: [1000, 1000] }, { label: '2026-10-26', values: [0, -500] }
    ]);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.dots[1].y).toBeGreaterThan(component.baseline);
    expect(component.dots[0].y).toBeLessThan(component.baseline);
    expect(component.bars.every(bar => bar.height >= 0)).toBe(true);
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toContain('accompanying table');
    expect(fixture.nativeElement.textContent).toContain('-500 USD');
  });

  it('leaves undefined rates unplotted rather than inventing zero and handles empty data', () => {
    const fixture = TestBed.createComponent(AnalyticsChartComponent);
    fixture.componentRef.setInput('series', [{ label: 'Participation', kind: 'line' }]);
    fixture.componentRef.setInput('points', [{ label: 'One', values: [null] }, { label: 'Two', values: [0] }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.dots).toHaveLength(1);
    expect(fixture.componentInstance.dots[0].title).toContain('Two');
    fixture.componentRef.setInput('points', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No observations');
  });
});
