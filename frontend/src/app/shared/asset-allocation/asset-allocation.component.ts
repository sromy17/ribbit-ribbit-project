import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { PortfolioAccountState } from '../../core/services/portfolio-state.service';

interface AllocationSlice {
  symbol: string;
  value: number;
  percentage: number;
  color: string;
  offset: number;
}

@Component({
  selector: 'app-asset-allocation',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Asset Allocation</h2>
    <p *ngIf="!state">Loading account allocation...</p>
    <p *ngIf="state && total <= 0">No assets to allocate yet.</p>
    <div class="allocation" *ngIf="state && total > 0">
      <div class="donut">
        <svg viewBox="0 0 220 220" role="img" aria-label="Asset allocation including cash">
          <circle cx="110" cy="110" r="82" fill="none" stroke="var(--ui-line)" stroke-width="25" />
          <circle *ngFor="let slice of slices" cx="110" cy="110" r="82"
            fill="none" [attr.stroke]="slice.color" stroke-width="25" pathLength="100"
            [attr.stroke-dasharray]="slice.percentage + ' ' + (100 - slice.percentage)"
            [attr.stroke-dashoffset]="-slice.offset" transform="rotate(-90 110 110)">
            <title>{{ slice.symbol }}: {{ slice.value | number:'1.2-2' }} {{ state?.currency || 'UNKNOWN' }} ({{ slice.percentage | number:'1.2-2' }}%)</title>
          </circle>
        </svg>
        <div class="center"><small>Total assets</small><strong>{{ total | number:'1.0-0' }} {{ state?.currency || 'UNKNOWN' }}</strong></div>
      </div>
      <ul aria-label="Allocation legend">
        <li *ngFor="let slice of slices">
          <span class="swatch" [style.background]="slice.color"></span>
          <strong>{{ slice.symbol }}</strong>
          <span>{{ slice.percentage | number:'1.2-2' }}%</span>
          <small>{{ slice.value | number:'1.2-2' }} {{ state?.currency || 'UNKNOWN' }}</small>
        </li>
      </ul>
    </div>
    <small *ngIf="state?.valuationWarning" class="notice">Estimated allocation: some positions use cost basis.</small>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    h2 { margin: 0 0 16px; font-size: 1.15rem; }
    .allocation { display: flex; align-items: center; flex-wrap: wrap; gap: 24px; }
    .donut { position: relative; flex: 0 1 240px; width: 240px; max-width: 100%; }
    svg { display: block; width: 100%; height: auto; }
    .center { position: absolute; inset: 32% 20%; display: grid; align-content: center;
      gap: 6px; text-align: center; pointer-events: none; }
    .center strong { font-size: clamp(.85rem, 1.5vw, 1.15rem); overflow-wrap: anywhere; }
    small, p { color: var(--md-on-surface-variant); }
    ul { flex: 1 1 240px; min-width: 0; margin: 0; padding: 0; list-style: none; }
    li { display: grid; grid-template-columns: 10px minmax(0, 1fr) auto;
      align-items: center; gap: 6px 10px; padding: 9px 0;
      border-bottom: 1px solid var(--md-outline-variant); font-size: .8rem; font-variant-numeric: tabular-nums; }
    li small { grid-column: 2 / -1; }
    .swatch { width: 9px; height: 9px; border-radius: 3px; }
    .notice { display: block; margin-top: 12px; }
  `]
})
export class AssetAllocationComponent implements OnChanges {
  @Input() state: PortfolioAccountState | null = null;
  slices: AllocationSlice[] = [];
  total = 0;

  ngOnChanges(): void {
    const positions = this.state?.holdings.map(h => ({
      symbol: h.symbol, value: h.shares * h.currentPrice
    })) ?? [];
    positions.push({ symbol: 'Cash', value: this.state?.cash ?? 0 });
    this.total = positions.reduce((sum, position) => sum + position.value, 0);
    let offset = 0;
    this.slices = this.total > 0 ? positions.filter(p => p.value > 0).map((position, index) => {
      const percentage = position.value / this.total * 100;
      const slice = {
        ...position, percentage, offset,
        color: position.symbol === 'Cash' ? 'var(--ui-muted)' : `var(--ui-chart-${index % 6 + 1})`
      };
      offset += percentage;
      return slice;
    }) : [];
  }
}
