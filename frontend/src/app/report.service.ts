import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { ReportNumberItem } from './report-number-item';
import { ReportSummary } from './report-summary';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly baseUrl = '/api/reports';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /** 帳票一覧を検索します。number を指定した場合は番号の前方一致で絞り込みます。 */
  searchReports(number?: string): Observable<ReportSummary[]> {
    const params = number ? new HttpParams().set('number', number) : undefined;
    return this.http.get<ReportSummary[]>(this.baseUrl, {
      headers: this.authHeaders(),
      params
    });
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
}
