import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { ReportNumberItem } from './report-number-item';
import { ReportSummary } from './report-summary';
import { ReportSearchApiResponse, ReportSearchRequest } from './report-search-request';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly baseUrl = '/api/reports';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /** 番号とPDF作成日時を条件に帳票一覧を検索します。 */
  searchReports(number: string | undefined, criteria: ReportSearchRequest): Observable<ReportSummary[]> {
    const params = number ? new HttpParams().set('number', number) : undefined;
    const numberSearch$ = this.http.get<ReportSummary[]>(this.baseUrl, {
      headers: this.authHeaders(),
      params
    });
    if (!this.hasDateTimeCondition(criteria)) {
      return numberSearch$;
    }

    const dateTimeSearch$ = this.http.post<ReportSearchApiResponse>(
      `${this.baseUrl}/search`,
      criteria,
      { headers: this.authHeaders() }
    );
    return forkJoin({ numberResults: numberSearch$, dateTimeResult: dateTimeSearch$ }).pipe(
      map(({ numberResults, dateTimeResult }) => {
        const matchedIds = new Set(dateTimeResult.reports.map((report) => report.reportId));
        return numberResults.filter((report) => matchedIds.has(report.id));
      })
    );
  }

  /** 番号検索候補（帳票番号・PDF名の一覧）を取得します。 */
  fetchNumberOptions(): Observable<ReportNumberItem[]> {
    return this.http.get<ReportNumberItem[]>(`${this.baseUrl}/numbers`, {
      headers: this.authHeaders()
    });
  }

  fetchPdf(reportId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${encodeURIComponent(reportId)}/pdf`, {
      headers: this.authHeaders(),
      responseType: 'blob'
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  private hasDateTimeCondition(criteria: ReportSearchRequest): boolean {
    return Boolean(criteria.startDate || criteria.startTime || criteria.endDate || criteria.endTime);
  }
}
