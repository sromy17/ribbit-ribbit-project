import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type AnalyticsRow = Record<string, any>;
export interface AnalyticsSelection { from: string; to: string; timezone: string; instrumentId?: number; side?: string; segment?: string; }
export interface AnalyticsResponse {
  datasetId: string; metricVersion: string; definitionStatus: string;
  period: { from: string; to: string; timezone: string; startInclusive: string; endExclusive: string };
  filters: AnalyticsRow; sourceCutoff: string; publishedAt: string; stale: boolean;
  completeness: string; sourceCoverage: AnalyticsRow; warnings: string[]; data: AnalyticsRow;
}
export interface IssuedReport { reportId: string; version: number; amendsReportId: string | null; amendmentReason: string | null; issuedAt: string; snapshot: AnalyticsResponse & AnalyticsRow; }
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  read(kind: string, selection: AnalyticsSelection, page = 0, sort = 'fills', direction = 'desc'): Observable<AnalyticsResponse> {
    let params = new HttpParams({ fromObject: { ...selection, page, size: 25, sort, direction } });
    return this.http.get<AnalyticsResponse>(`/api/analytics/${kind}`, { params });
  }
  issue(selection: AnalyticsSelection, key: string, amendsReportId?: string, amendmentReason?: string) {
    return this.http.post<IssuedReport>('/api/analytics/reports', { ...selection, idempotencyKey: key, amendsReportId, amendmentReason });
  }
  report(id: string) { return this.http.get<IssuedReport>(`/api/analytics/reports/${encodeURIComponent(id)}`); }
}
