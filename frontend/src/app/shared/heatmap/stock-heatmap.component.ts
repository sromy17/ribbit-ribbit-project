import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { hierarchy, treemap } from 'd3';
import { FinnhubNewsArticle, FinnhubNewsService } from '../../core/services/finnhub-news.service';
import { clampChange, getHeatmapColor } from './heatmap-color';
import { getCompanyLogoUrl } from './company-logo';

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
}

interface HeatmapTile extends HeatmapStock {
  color: string;
  weight: number;
  textMode: 'large' | 'medium' | 'small' | 'micro';
  left: number;
  top: number;
  width: number;
  height: number;
}

interface TreemapData {
  stock?: HeatmapStock;
  weight?: number;
  children?: TreemapData[];
}

@Component({
  selector: 'app-stock-heatmap',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stock-heatmap.component.html',
  styleUrl: './stock-heatmap.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StockHeatmapComponent implements OnChanges {
  readonly layoutWidth = 1000;
  readonly layoutHeight = 600;
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

  constructor(private readonly finnhubNews: FinnhubNewsService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['stocks']) {
      this.tiles = this.createTiles(this.stocks);
    }
  }

  trackByTicker(_: number, stock: HeatmapTile): string {
    return stock.ticker;
  }

  formatChange(change: number): string {
    return `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;
  }

  selectStock(stock: HeatmapStock): void {
    this.selectedStock = stock;
    this.companyNews = [];
    this.companyNewsError = '';
    this.loadingCompanyNews = true;
    const today = new Date();
    const from = new Date(today);
    from.setDate(today.getDate() - 7);
    this.finnhubNews.getCompanyNews(stock.ticker, this.toDate(from), this.toDate(today)).subscribe({
      next: (articles) => {
        this.companyNews = articles.slice(0, 5);
        this.loadingCompanyNews = false;
      },
      error: (error) => {
        console.error(`Error loading company news for ${stock.ticker}:`, error);
        this.companyNewsError = 'Company news is temporarily unavailable.';
        this.loadingCompanyNews = false;
      }
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
    this.selectedStock = null;
  }

  private createTiles(stocks: HeatmapStock[]): HeatmapTile[] {
    const ranked = stocks
      .filter((stock) => Number.isFinite(stock.currentPrice) && stock.currentPrice > 0)
      .sort((left, right) => this.getWeight(right) - this.getWeight(left))
      .slice(0, 50);

    const root = hierarchy<TreemapData>({
      children: ranked.map((stock, index) => ({
        stock,
        weight: this.getSyntheticWeight(index)
      }))
    })
      .sum((node) => node.weight ?? 0)
      .sort((left, right) => (right.value ?? 0) - (left.value ?? 0));

    const layout = treemap<TreemapData>()
      .size([this.layoutWidth, this.layoutHeight])
      .paddingInner(2)
      .paddingOuter(2)
      .round(true);

    return layout(root).leaves()
      .filter((node): node is typeof node & { data: { stock: HeatmapStock } } => !!node.data.stock)
      .map((node) => this.toTile(
        node.data.stock,
        node.data.weight ?? 0,
        node.x0,
        node.y0,
        node.x1 - node.x0,
        node.y1 - node.y0
      ));
  }

  private toTile(
    stock: HeatmapStock,
    weight: number,
    left: number,
    top: number,
    width: number,
    height: number
  ): HeatmapTile {
    const dailyChange = clampChange(Number.isFinite(stock.dailyChange) ? stock.dailyChange : 0);
    return {
      ...stock,
      dailyChange,
      color: getHeatmapColor(dailyChange),
      weight,
      left,
      top,
      width,
      height,
      textMode: this.getTextMode(width, height)
    };
  }

  private getSyntheticWeight(rank: number): number {
    return Math.max(10, Math.pow(51 - rank, 1.2));
  }

  private getTextMode(width: number, height: number): HeatmapTile['textMode'] {
    if (width > 110 && height > 90) return 'large';
    if (width > 70 && height > 50) return 'medium';
    if (width > 45 && height > 35) return 'small';
    return 'micro';
  }

  private toDate(date: Date): string {
    return date.toISOString().slice(0, 10);
  }

  private getWeight(stock: HeatmapStock): number {
    const weight = stock.marketCap ?? stock.sizeValue;
    return Number.isFinite(weight) && (weight ?? 0) > 0 ? weight as number : 0;
  }
}
