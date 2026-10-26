import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { FinnhubNewsArticle, FinnhubNewsService } from '../../core/services/finnhub-news.service';
import { getHeatmapColor } from './heatmap-color';
import { getCompanyLogoUrl } from './company-logo';
import { TradingviewWidgetComponent } from '../tradingview/tradingview-widget.component';
import { PortfolioStateService } from '../../core/services/portfolio-state.service';

export interface HeatmapStock {
  ticker: string;
  companyName: string;
  currentPrice: number;
  marketCap?: number;
  sizeValue?: number;
  dailyChange: number;
  volume?: number;
  high?: number;
  low?: number;
  sector?: string;
  exchange?: string;
}

interface HeatmapTile extends HeatmapStock { color: string; }

@Component({
  selector: 'app-stock-heatmap',
  standalone: true,
  imports: [CommonModule, FormsModule, TradingviewWidgetComponent],
  templateUrl: './stock-heatmap.component.html',
  styleUrl: './stock-heatmap.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StockHeatmapComponent implements OnChanges, OnDestroy {
  @ViewChild('detailsDialog', { static: true }) detailsDialog!: ElementRef<HTMLDialogElement>;
  @ViewChild('tradeDialog', { static: true }) tradeDialog!: ElementRef<HTMLDialogElement>;
  @Input() accountNumber = '';
  private companyNewsSubscription?: Subscription;
  private tradeSubscription?: Subscription;
  private toastTimer?: ReturnType<typeof setTimeout>;
  @Input() stocks: HeatmapStock[] = [];
  @Input() loading = false;

  tiles: HeatmapTile[] = [];
  selectedStock: HeatmapStock | null = null;
  readonly getHeatmapColor = getHeatmapColor;
  readonly getCompanyLogoUrl = getCompanyLogoUrl;
  readonly hiddenLogos = new Set<string>();
  readonly logoUrls = new Map<string, string | null>();
  readonly skeletonBlocks = Array.from({ length: 50 });
  companyNews: FinnhubNewsArticle[] = [];
  loadingCompanyNews = false;
  companyNewsError = '';
  tradeSide: 'BUY' | 'SELL' = 'BUY';
  tradeQuantity = 1;
  tradePrice = 0;
  placingTrade = false;
  tradeLocked = false;
  tradeError = '';
  tradeNotice = '';
  tradeWarning = '';

  constructor(
    private readonly finnhubNews: FinnhubNewsService,
    private readonly portfolioState: PortfolioStateService,
    private readonly ngZone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['accountNumber'] && !changes['accountNumber'].firstChange) {
      this.tradeSubscription?.unsubscribe();
      this.placingTrade = false;
      this.closeDetails();
    }
    if (changes['stocks']) {
      this.ngZone.runOutsideAngular(() => {
        const tiles = this.createTiles(this.stocks);
        this.ngZone.run(() => {
          this.tiles = tiles;
          this.cdr.markForCheck();
        });
      });
    }
  }

  trackByTicker(_: number, stock: HeatmapTile): string {
    return stock.ticker;
  }

  formatChange(change: number): string {
    return `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;
  }

  selectStock(stock: HeatmapStock): void {
    this.ngZone.run(() => {
      this.companyNewsSubscription?.unsubscribe();
      this.selectedStock = stock;
      this.tradeNotice = '';
      this.tradeWarning = '';
      this.companyNews = [];
      this.companyNewsError = '';
      this.loadingCompanyNews = true;
      this.cdr.detectChanges();
      if (!this.detailsDialog.nativeElement.open) this.detailsDialog.nativeElement.showModal();
      const today = new Date();
      const from = new Date(today);
      from.setDate(today.getDate() - 7);
      this.companyNewsSubscription = this.finnhubNews.getCompanyNews(stock.ticker, this.toDate(from), this.toDate(today)).subscribe({
        next: (articles) => {
          this.companyNews = articles.slice(0, 5);
          this.companyNewsError = this.finnhubNews.availability$?.value === 'unavailable'
            ? 'Company news is temporarily unavailable.' : '';
          this.loadingCompanyNews = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error(`Error loading company news for ${stock.ticker}:`, error);
          this.companyNewsError = error instanceof Error ? error.message : 'Company news is temporarily unavailable.';
          this.loadingCompanyNews = false;
          this.cdr.markForCheck();
        }
      });
    });
  }

  hideLogo(ticker: string): void {
    this.hiddenLogos.add(ticker);
  }

  getLogoUrl(ticker: string): string | null {
    if (!this.logoUrls.has(ticker)) {
      this.logoUrls.set(ticker, getCompanyLogoUrl(ticker));
    }
    return this.logoUrls.get(ticker) ?? null;
  }

  getInitials(ticker: string): string {
    return ticker.trim().slice(0, 2).toUpperCase() || '?';
  }

  getCompanyNewsTime(timestamp: number): string {
    const minutes = Math.max(0, Math.floor((Date.now() - timestamp * 1000) / 60000));
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    return hours < 24 ? `${hours}h ago` : `${Math.floor(hours / 24)}d ago`;
  }

  closeDetails(): void {
    if (this.placingTrade) return;
    this.tradeDialog.nativeElement.close();
    this.companyNewsSubscription?.unsubscribe();
    this.detailsDialog.nativeElement.close();
    this.selectedStock = null;
  }

  tradeSelectedStock(): void {
    if (!this.selectedStock || this.placingTrade) return;
    this.tradeSide = 'BUY';
    this.tradeQuantity = 1;
    this.tradePrice = this.selectedStock.currentPrice;
    this.tradeError = '';
    this.tradeNotice = '';
    this.tradeWarning = '';
    this.tradeLocked = false;
    this.cdr.detectChanges();
    this.tradeDialog.nativeElement.showModal();
  }

  get tradeSubtotal(): number {
    return this.tradeQuantity * this.tradePrice;
  }

  get tradeFee(): number {
    return PortfolioStateService.estimateFee(this.tradeSubtotal);
  }

  get tradeTotal(): number {
    return this.tradeSubtotal + (this.tradeSide === 'BUY' ? this.tradeFee : -this.tradeFee);
  }

  get validTrade(): boolean {
    return !!this.accountNumber && Number.isSafeInteger(this.tradeQuantity) && this.tradeQuantity > 0 &&
      Number.isFinite(this.tradePrice) && this.tradePrice > 0 && Number.isFinite(this.tradeSubtotal);
  }

  closeTrade(event?: Event): void {
    event?.preventDefault();
    if (!this.placingTrade) this.tradeDialog.nativeElement.close();
  }

  cancelDetails(event: Event): void {
    event.preventDefault();
    this.closeDetails();
  }

  placeQuickOrder(): void {
    if (this.placingTrade || this.tradeLocked || !this.selectedStock) return;
    if (!this.validTrade) {
      this.tradeError = 'Select an account and enter a positive whole-number quantity with a valid market price.';
      return;
    }
    const ticker = this.selectedStock.ticker;
    const side = this.tradeSide;
    this.placingTrade = true;
    this.tradeError = '';
    this.tradeSubscription = this.portfolioState.executeTrade(this.accountNumber, {
      ticker, side, shares: this.tradeQuantity, price: this.tradePrice
    }).subscribe({
      next: ({ order, syncWarning }) => {
        this.placingTrade = false;
        this.tradeWarning = syncWarning ?? '';
        if (order.status === 'EXECUTED' || order.status === 'FILLED') {
          const quantity = order.executedQuantity;
          const price = order.executionPrice;
          this.tradeNotice = quantity !== undefined && price !== undefined
            ? `Order Executed: ${side === 'BUY' ? 'Bought' : 'Sold'} ${quantity} ${quantity === 1 ? 'share' : 'shares'} of ${ticker} @ $${price.toFixed(2)}`
            : `Order ${order.orderId} executed for ${ticker}. See History for execution details.`;
          this.tradeDialog.nativeElement.close();
          clearTimeout(this.toastTimer);
          this.toastTimer = setTimeout(() => {
            this.tradeNotice = '';
            this.cdr.markForCheck();
          }, 8000);
        } else {
          this.tradeLocked = order.status !== 'REJECTED' && order.status !== 'CANCELLED';
          this.tradeError = this.tradeLocked
            ? `Order ${order.orderId} has status ${order.status}. Check History before submitting another order.`
            : `Order ${order.status.toLowerCase()}. Check available cash, holdings, and trading rules.`;
        }
        this.cdr.markForCheck();
      },
      error: error => {
        console.error('Quick trade could not be confirmed:', error);
        this.placingTrade = false;
        this.tradeLocked = true;
        this.tradeError = 'Order could not be confirmed. Check History before retrying to avoid a duplicate order.';
        this.cdr.markForCheck();
      }
    });
  }

  closeOnBackdrop(event: MouseEvent): void {
    if (event.target === this.detailsDialog.nativeElement) this.closeDetails();
  }

  ngOnDestroy(): void {
    this.companyNewsSubscription?.unsubscribe();
    this.tradeSubscription?.unsubscribe();
    clearTimeout(this.toastTimer);
  }

  getChartSymbol(stock: HeatmapStock): string {
    const exchange = stock.exchange?.trim().toUpperCase();
    const prefix = exchange === 'XNAS' ? 'NASDAQ' : exchange === 'XNYS' ? 'NYSE' : exchange;
    return prefix === 'NASDAQ' || prefix === 'NYSE' || prefix === 'AMEX'
      ? `${prefix}:${stock.ticker}` : stock.ticker;
  }

  private createTiles(stocks: HeatmapStock[]): HeatmapTile[] {
    return stocks.filter(stock => Number.isFinite(stock.currentPrice) && stock.currentPrice > 0)
      .slice(0, 50).map(stock => ({ ...stock, color: getHeatmapColor(stock.dailyChange) }));
  }

  private toDate(date: Date): string {
    return date.toISOString().slice(0, 10);
  }

}
