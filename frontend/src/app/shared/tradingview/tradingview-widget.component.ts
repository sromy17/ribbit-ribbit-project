import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { inject } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component,
  ElementRef, Inject, Input, NgZone, OnChanges, OnDestroy, PLATFORM_ID, ViewChild
} from '@angular/core';

interface TradingViewOptions {
  autosize: boolean;
  symbol: string;
  interval: string;
  timezone: string;
  theme: string;
  style: string;
  locale: string;
  toolbar_bg: string;
  enable_publishing: boolean;
  allow_symbol_change: boolean;
  container_id: string;
}
interface TradingViewInstance { id: string; remove(): void }
interface TradingViewApi { widget: new (options: TradingViewOptions) => TradingViewInstance }

let scriptRequest: Promise<TradingViewApi> | undefined;
let nextContainerId = 0;

function loadTradingView(): Promise<TradingViewApi> {
  const browser = window as Window & { TradingView?: TradingViewApi };
  if (browser.TradingView) return Promise.resolve(browser.TradingView);
  if (scriptRequest) return scriptRequest;
  scriptRequest = new Promise<TradingViewApi>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://s3.tradingview.com/tv.js';
    script.async = true;
    const timer = window.setTimeout(() => fail(), 15000);
    const fail = () => {
      window.clearTimeout(timer);
      script.onload = script.onerror = null;
      script.remove();
      reject(new Error('TradingView script could not be loaded.'));
    };
    script.onerror = fail;
    script.onload = () => {
      if (!browser.TradingView) { fail(); return; }
      window.clearTimeout(timer);
      script.onload = script.onerror = null;
      resolve(browser.TradingView);
    };
    document.head.appendChild(script);
  }).catch(error => {
    scriptRequest = undefined;
    throw error;
  });
  return scriptRequest;
}

@Component({
  selector: 'app-tradingview-widget',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #container class="chart" [hidden]="!!internationalSymbol" [id]="containerId" aria-label="TradingView stock chart"></div>
    <section *ngIf="internationalSymbol && history.length" class="native-chart" aria-label="International stock price history">
      <header><strong>{{ internationalSymbol }}</strong><span>1 month · daily closes · {{ historyCurrency }}</span></header>
      <div class="history-value" [style.color]="historyColor">{{ history.at(-1)?.price | number:'1.2-3' }} {{ historyCurrency }}</div>
      <svg viewBox="0 0 600 260" preserveAspectRatio="none" role="img" [attr.aria-label]="internationalSymbol + ' one-month closing prices in ' + historyCurrency">
        <defs><linearGradient [id]="containerId + '-history-fill'" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" [attr.stop-color]="historyColor" stop-opacity=".25" />
          <stop offset="100%" [attr.stop-color]="historyColor" stop-opacity="0" />
        </linearGradient></defs>
        <polygon [attr.points]="'0,260 ' + historyPoints + ' 600,260'" [attr.fill]="'url(#' + containerId + '-history-fill)'" />
        <polyline [attr.points]="historyPoints" fill="none" [attr.stroke]="historyColor" stroke-width="2.5" vector-effect="non-scaling-stroke" />
      </svg>
      <footer><span>{{ history[0].atUtc | date:'mediumDate' }}</span><span>{{ history.at(-1)?.atUtc | date:'mediumDate' }}</span></footer>
      <small>Indicative historical prices · may be delayed</small>
    </section>
    <div *ngIf="loading" class="status" role="status">{{ internationalSymbol ? 'Loading price history...' : 'Loading TradingView chart...' }}</div>
    <div *ngIf="error" class="status" role="alert">
      {{ error }}
      <button type="button" (click)="render()">Retry</button>
    </div>
  `,
  styles: [`
    :host { display: flex; position: relative; flex: 1; width: 100%; height: 100%; min-width: 0; min-height: 0; }
    .chart { flex: 1; width: 100%; height: 100%; min-width: 0; min-height: 0; }
    .status { position: absolute; inset: 0; display: grid; place-content: center; gap: 12px;
      padding: 16px; background: var(--ui-panel); color: var(--ui-ink); text-align: center; white-space: normal; }
    .native-chart { display: flex; flex: 1; flex-direction: column; gap: 12px; min-width: 0; padding: 20px; color: var(--ui-ink); }
    .native-chart header, .native-chart footer { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; }
    .native-chart header span, .native-chart footer, .native-chart small { color: var(--ui-muted); }
    .native-chart svg { flex: 1; width: 100%; min-height: 150px; overflow: visible; }
    .history-value { font-size: 24px; font-variant-numeric: tabular-nums; }
    button { padding: 8px; border: 1px solid var(--ui-line); border-radius: 6px; background: var(--ui-subtle);
      color: var(--ui-ink); cursor: pointer; }
  `]
})
export class TradingviewWidgetComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() symbol = 'NASDAQ:AAPL';
  private readonly http = inject(HttpClient, { optional: true });
  history: Array<{ atUtc: string; price: number }> = [];
  historyCurrency = '';
  get internationalSymbol(): string | null {
    const match = /^(LSE|NSE):([A-Z][A-Z0-9.-]*)$/.exec(this.symbol);
    return match ? `${match[2]}.${match[1] === 'LSE' ? 'L' : 'NS'}` : null;
  }
  get historyColor(): string {
    return (this.history.at(-1)?.price ?? 0) >= (this.history[0]?.price ?? 0) ? 'var(--ui-success)' : 'var(--ui-danger)';
  }
  get historyPoints(): string {
    if (this.history.length < 2) return '';
    const prices = this.history.map(point => point.price);
    const low = Math.min(...prices), high = Math.max(...prices);
    const span = high - low || Math.max(high * .01, 1);
    const start = Date.parse(this.history[0].atUtc), end = Date.parse(this.history.at(-1)!.atUtc);
    return this.history.map(point => `${(Date.parse(point.atUtc) - start) / Math.max(1, end - start) * 600},${240 - (point.price - low) / span * 220}`).join(' ');
  }
  @ViewChild('container', { static: true }) container!: ElementRef<HTMLDivElement>;
  readonly containerId = `tradingview-chart-${++nextContainerId}`;
  loading = true;
  error = '';
  private ready = false;
  private destroyed = false;
  private revision = 0;
  private widget?: TradingViewInstance;
  private themeObserver?: MutationObserver;

  constructor(
    @Inject(PLATFORM_ID) private readonly platformId: object,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngAfterViewInit(): void {
    this.ready = isPlatformBrowser(this.platformId);
    if (this.ready) {
      this.themeObserver = new MutationObserver(() => this.render());
      this.themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class'] });
    }
    this.render();
  }

  ngOnChanges(): void { this.render(); }

  render(): void {
    if (!this.ready || this.destroyed) return;
    const revision = ++this.revision;
    this.loading = true;
    this.error = '';
    this.zone.runOutsideAngular(async () => {
      try {
        this.removeWidget();
        this.container.nativeElement.replaceChildren();
        if (this.internationalSymbol) {
          if (!this.http) throw new Error('Price-history client unavailable');
          const data = await firstValueFrom(this.http.get<{ currency: string; points: Array<{ atUtc: string; price: number }> }>(
            '/api/market/history/' + encodeURIComponent(this.internationalSymbol)).pipe(timeout(8000)));
          if (this.destroyed || revision !== this.revision) return;
          const points = data.points.filter(point => Number.isFinite(point.price) && point.price > 0 && Number.isFinite(Date.parse(point.atUtc)));
          if (points.length < 2) throw new Error('Insufficient price history');
          this.zone.run(() => {
            this.history = points;
            this.historyCurrency = data.currency;
            this.loading = false;
            this.cdr.markForCheck();
          });
          return;
        }
        this.history = [];
        const api = await loadTradingView();
        if (this.destroyed || revision !== this.revision) return;
        this.widget = new api.widget({
          autosize: true, symbol: this.symbol, interval: 'D', timezone: 'Etc/UTC',
          theme: document.body.classList.contains('theme-dark') ? 'dark' : 'light', style: '1', locale: 'en', toolbar_bg: getComputedStyle(document.body).getPropertyValue('--ui-panel').trim(),
          enable_publishing: false, allow_symbol_change: true, container_id: this.containerId
        });
        this.zone.run(() => { this.loading = false; this.cdr.markForCheck(); });
      } catch (error) {
        if (this.destroyed || revision !== this.revision) return;
        console.error('TradingView initialization failed:', error);
        this.zone.run(() => {
          this.loading = false;
          this.error = this.internationalSymbol ? 'Price history is temporarily unavailable.' : 'TradingView is unavailable. Check your connection or content blocker.';
          this.cdr.markForCheck();
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.themeObserver?.disconnect();
    this.destroyed = true;
    ++this.revision;
    this.removeWidget();
  }

  private removeWidget(): void {
    // Angular may detach the iframe before destruction; tv.js assumes it is still in the document.
    if (this.widget && document.getElementById(this.widget.id)) this.widget.remove();
    this.widget = undefined;
  }
}
