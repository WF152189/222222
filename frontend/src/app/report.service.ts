import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { ReportSummary } from './report-summary';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly baseUrl = '/api/reports';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  searchReports(): Observable<ReportSummary[]> {
    return this.http.get<ReportSummary[]>(this.baseUrl, {
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
