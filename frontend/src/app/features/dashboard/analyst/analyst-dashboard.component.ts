import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { UI_COMPONENTS } from '../../../shared/ui';
import { AuthService } from '../../../core/services/auth.service';
import { AnalyticsService, AnalyticsResponse, AnalyticsRow, AnalyticsSelection, IssuedReport } from '../../../core/services/analytics.service';

@Component({ selector: 'app-analyst-dashboard', standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './analyst-dashboard.component.html', styleUrls: ['./analyst-dashboard.component.css'] })
export class AnalystDashboardComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService); private router = inject(Router);
  private api = inject(AnalyticsService); private cdr = inject(ChangeDetectorRef);
  currentUser = this.auth.getUser();
  month = new Date().toISOString().slice(0, 7); timezone = 'UTC'; instrument = ''; side = ''; segment = '';
  tab: 'overview' | 'activity' | 'instruments' | 'client-segments' | 'report' | 'heatmap' = 'overview';
  loading = false; issuing = false; error = ''; reportError = ''; reportId = ''; amendmentReason = '';
  meta: AnalyticsResponse | null = null; overview: AnalyticsRow = {};
  activity: AnalyticsRow = { items: [], total: 0 }; instruments: AnalyticsRow = { items: [], total: 0 };
  segments: AnalyticsRow = { items: [], total: 0 }; saved: IssuedReport | null = null;
  private request?: Subscription; private reportRequest?: Subscription; private selection?: AnalyticsSelection;
  private issueKey = ''; private issueSignature = '';
  ngOnInit() { this.load(); }
  ngOnDestroy() { this.request?.unsubscribe(); this.reportRequest?.unsubscribe(); }
  select(): AnalyticsSelection {
    if (!/^\d{4}-\d{2}$/.test(this.month)) throw new Error('Choose a valid month.');
    const [year, month] = this.month.split('-').map(Number);
    if (month < 1 || month > 12 || year < 1900 || year > 9998) throw new Error('Choose a valid month.');
    const days = new Date(Date.UTC(year, month, 0)).getUTCDate();
    const selection: AnalyticsSelection = { from: `${this.month}-01`, to: `${this.month}-${days}`, timezone: this.timezone };
    if (this.instrument) { if (!/^\d+$/.test(this.instrument) || Number(this.instrument) < 1) throw new Error('Use a positive instrument ID.'); selection.instrumentId = Number(this.instrument); }
    if (this.side) selection.side = this.side;
    if (this.segment.trim()) selection.segment = this.segment.trim();
    return selection;
  }
  load() {
    this.request?.unsubscribe(); this.error = ''; this.saved = null; this.meta = null;
    let selection: AnalyticsSelection;
    try { selection = this.select(); } catch (e) { this.error = (e as Error).message; return; }
    this.loading = true;
    this.request = forkJoin({ overview: this.api.read('overview', selection), activity: this.api.read('activity', selection, 0, 'period', 'asc'),
      instruments: this.api.read('instruments', selection), segments: this.api.read('client-segments', selection) }).subscribe({
      next: r => {
        if (new Set([r.overview.datasetId, r.activity.datasetId, r.instruments.datasetId, r.segments.datasetId]).size !== 1) {
          this.error = 'The dataset changed during refresh. Reload to view a consistent version.';
        } else {
          this.selection = selection; this.meta = r.overview; this.overview = r.overview.data;
          this.activity = r.activity.data; this.instruments = r.instruments.data; this.segments = r.segments.data;
        }
        this.loading = false; this.cdr.detectChanges();
      }, error: e => { this.error = e.error?.message || 'Analytics query failed. Retry when reporting is available.'; this.loading = false; this.cdr.detectChanges(); }
    });
  }
  page(kind: 'activity' | 'instruments' | 'client-segments', delta: number) {
    if (!this.selection || this.loading) return;
    const current = kind === 'activity' ? this.activity : kind === 'instruments' ? this.instruments : this.segments;
    const page = current['page'] + delta; this.loading = true; this.error = '';
    this.request = this.api.read(kind, this.selection, page, kind === 'activity' ? 'period' : 'fills', kind === 'activity' ? 'asc' : 'desc').subscribe({
      next: r => {
        if (r.datasetId !== this.meta?.datasetId) { this.error = 'Dataset changed. Reload all views before continuing.'; this.meta = null; }
        else if (kind === 'activity') this.activity = r.data;
        else if (kind === 'instruments') this.instruments = r.data;
        else this.segments = r.data;
        this.loading = false; this.cdr.detectChanges();
      }, error: e => { this.error = e.error?.message || 'Page query failed.'; this.loading = false; this.cdr.detectChanges(); }
    });
  }
  issue(amend = false) {
    if (this.issuing || !this.meta || (!this.selection && !this.saved)) return;
    let selection = this.selection!;
    if (amend && this.saved) {
      const s = this.saved.snapshot;
      selection = { from: s.period.from, to: s.period.to, timezone: s.period.timezone, ...Object.fromEntries(Object.entries(s.filters).filter(([,v]) => v !== null)) };
    }
    const parent = amend ? this.saved?.reportId : undefined;
    const signature = JSON.stringify([selection, parent, this.amendmentReason]);
    if (signature !== this.issueSignature) { this.issueKey = crypto.randomUUID(); this.issueSignature = signature; }
    this.issuing = true; this.reportError = '';
    this.reportRequest = this.api.issue(selection, this.issueKey, parent, amend ? this.amendmentReason : undefined).subscribe({
      next: r => { this.showReport(r); this.issuing = false; this.cdr.detectChanges(); },
      error: e => { this.reportError = e.error?.message || 'Report could not be issued. Retry uses the same request key.'; this.issuing = false; this.cdr.detectChanges(); }
    });
  }
  openReport() {
    this.reportError = ''; this.issuing = true;
    this.reportRequest = this.api.report(this.reportId.trim()).subscribe({ next: r => { this.showReport(r); this.issuing = false; this.cdr.detectChanges(); },
      error: e => { this.reportError = e.error?.message || 'Report could not be opened.'; this.issuing = false; this.cdr.detectChanges(); } });
  }
  private showReport(r: IssuedReport) {
    this.saved = r; this.reportId = r.reportId; this.meta = r.snapshot;
    this.overview = r.snapshot['overview']; this.activity = r.snapshot['activity']; this.instruments = r.snapshot['instruments']; this.segments = r.snapshot['client-segments']; this.tab = 'report';
  }
  get rows(): AnalyticsRow[] { return this.table['items'] || []; }
  get table(): AnalyticsRow { return this.tab === 'activity' ? this.activity : this.tab === 'client-segments' ? this.segments : this.instruments; }
  get maxFills(): number { return Math.max(1, ...this.rows.map(r => Number(r['fill_count']))); }
  label(r: AnalyticsRow) { return this.tab === 'activity' ? r['period'] : this.tab === 'client-segments' ? r['segment_code'] : `${r['ticker'] || 'Unknown'} · #${r['instrumentid']}`; }
  logout() { this.auth.logout(); this.router.navigate(['/login']); }
}
