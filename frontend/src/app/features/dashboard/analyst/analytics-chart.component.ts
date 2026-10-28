import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { line, scaleLinear } from 'd3';

export interface ChartPoint { label: string; values: (number | null)[] }
export interface ChartSeries { label: string; kind: 'bar' | 'line' }

@Component({
  selector: 'app-analytics-chart',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure>
      <figcaption>{{ title }} <span>({{ unit }})</span></figcaption>
      <p *ngIf="!points.length">No observations in this period.</p>
      <ng-container *ngIf="points.length">
        <ul class="legend"><li *ngFor="let item of series; let i = index">
          <span [style.background]="colors[i % colors.length]"></span>{{ item.label }}
          {{ item.kind === 'line' ? '(line)' : '(bars)' }}
        </li></ul>
        <div class="chart-scroll" tabindex="0" [attr.aria-label]="title + ' graph; scroll horizontally on small screens'">
          <svg viewBox="0 0 900 310" role="img" [attr.aria-label]="title + '. Exact values are in the accompanying table.'">
            <svg:title>{{ title }}</svg:title>
            <g *ngFor="let tick of ticks">
              <line x1="85" x2="880" [attr.y1]="tick.y" [attr.y2]="tick.y" class="grid"/>
              <text x="77" [attr.y]="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
            </g>
            <line x1="85" x2="880" [attr.y1]="baseline" [attr.y2]="baseline" class="axis"/>
            <rect *ngFor="let bar of bars" [attr.x]="bar.x" [attr.y]="bar.y"
              [attr.width]="bar.width" [attr.height]="bar.height" [attr.fill]="bar.color"><svg:title>{{ bar.title }}</svg:title></rect>
            <path *ngFor="let path of paths" [attr.d]="path.d" [attr.stroke]="path.color" fill="none" stroke-width="2.5"/>
            <circle *ngFor="let dot of dots" [attr.cx]="dot.x" [attr.cy]="dot.y" r="3" [attr.fill]="dot.color">
              <svg:title>{{ dot.title }}</svg:title>
            </circle>
            <text *ngFor="let label of labels" [attr.x]="label.x" y="286" text-anchor="middle">{{ label.text }}</text>
          </svg>
        </div>
      </ng-container>
    </figure>`,
  styles: [`
    :host { display:block; min-width:0; }
    figure { margin:20px 0; }
    figcaption { font-weight:600; }
    figcaption span { font-weight:400; }
    .chart-scroll { overflow-x:auto; }
    svg { display:block; width:100%; min-width:660px; }
    text { fill:var(--md-on-surface); font-size:12px; }
    .grid { stroke:var(--md-outline-variant); stroke-dasharray:3 3; }
    .axis { stroke:var(--md-on-surface); }
    .legend { display:flex; flex-wrap:wrap; gap:16px; padding:0; list-style:none; font-size:.9rem; }
    .legend span { display:inline-block; width:12px; height:12px; margin-right:6px; border-radius:2px; }
  `]
})
export class AnalyticsChartComponent implements OnChanges {
  @Input({ required: true }) title = '';
  @Input() unit = 'count';
  @Input() points: ChartPoint[] = [];
  @Input() series: ChartSeries[] = [];
  readonly colors = ['#1976d2', '#e67700', '#8e44ad'];
  ticks: { y: number; label: string }[] = [];
  labels: { x: number; text: string }[] = [];
  bars: { x: number; y: number; width: number; height: number; color: string; title: string }[] = [];
  paths: { d: string; color: string }[] = [];
  dots: { x: number; y: number; color: string; title: string }[] = [];
  baseline = 260;

  ngOnChanges(): void {
    const values = this.points.flatMap(point => point.values.filter((value): value is number => value !== null));
    const y = scaleLinear().domain([Math.min(0, ...values), Math.max(1, ...values)]).nice().range([260, 20]);
    const step = 795 / Math.max(1, this.points.length);
    const x = (index: number) => 85 + (index + 0.5) * step;
    const format = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 });
    this.baseline = y(0);
    this.ticks = y.ticks(5).map(value => ({ y: y(value), label: format.format(value) }));
    this.labels = this.points.flatMap((point, index) =>
      index % Math.max(1, Math.ceil(this.points.length / 6)) === 0 ? [{ x: x(index), text: point.label }] : []);
    this.bars = [];
    this.paths = [];
    this.dots = [];
    const barSeries = this.series.filter(series => series.kind === 'bar');
    let barIndex = 0;
    this.series.forEach((series, seriesIndex) => {
      const color = this.colors[seriesIndex % this.colors.length];
      const width = Math.min(42, step * 0.8 / Math.max(1, barSeries.length));
      if (series.kind === 'line') {
        const path = line<ChartPoint>().defined(point => point.values[seriesIndex] != null)
          .x((_, index) => x(index)).y(point => y(point.values[seriesIndex]!))(this.points);
        if (path) this.paths.push({ d: path, color });
      }
      this.points.forEach((point, index) => {
        const value = point.values[seriesIndex];
        if (value == null) return;
        const title = `${point.label}: ${series.label} ${value.toLocaleString('en')} ${this.unit}`;
        if (series.kind === 'bar') {
          this.bars.push({ x: x(index) - width * barSeries.length / 2 + barIndex * width,
            y: Math.min(y(0), y(value)), width, height: Math.abs(y(value) - y(0)), color, title });
        } else {
          this.dots.push({ x: x(index), y: y(value), color, title });
        }
      });
      if (series.kind === 'bar') barIndex++;
    });
  }
}
