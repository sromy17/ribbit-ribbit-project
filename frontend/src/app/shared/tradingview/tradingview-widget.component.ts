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
    <div #container class="chart" [id]="containerId" aria-label="TradingView stock chart"></div>
    <div *ngIf="loading" class="status" role="status">Loading TradingView chart...</div>
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
    button { padding: 8px; border: 1px solid var(--ui-line); border-radius: 6px; background: var(--ui-subtle);
      color: var(--ui-ink); cursor: pointer; }
  `]
})
export class TradingviewWidgetComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() symbol = 'NASDAQ:AAPL';
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
          this.error = 'TradingView is unavailable. Check your connection or content blocker.';
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
