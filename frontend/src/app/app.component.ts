import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthenticatedUser } from './auth.model';
import { AuthService } from './auth.service';
import { ReportService } from './report.service';
import { ReportSummary } from './report-summary';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnDestroy {
  reports: ReportSummary[] = [];
  currentUser: AuthenticatedUser | null;
  loginUsername = 'zhangsan';
  loginPassword = 'password';
  selectedReport?: ReportSummary;
  private objectUrl?: string;
  loading = false;
  message = '検索ボタンを押して帳票一覧を取得してください。';

  constructor(
    private readonly reportService: ReportService,
    private readonly authService: AuthService
  ) {
    this.currentUser = this.authService.getCurrentUser();
  }

  login(): void {
    this.loading = true;
    this.message = 'ログイン中です。';
    this.authService.login({ username: this.loginUsername, password: this.loginPassword }).subscribe({
      next: (response) => {
        this.currentUser = response.user;
        this.loading = false;
        this.message = `${response.user.userName} としてログインしました。`;
      },
      error: () => {
        this.loading = false;
        this.message = 'ログインに失敗しました。ユーザー名またはパスワードを確認してください。';
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.currentUser = null;
    this.reports = [];
    this.selectedReport = undefined;
    this.revokeObjectUrl();
    this.message = 'ログアウトしました。';
  }

  search(): void {
    this.loading = true;
    this.message = '帳票一覧を取得中です。';
    this.reportService.searchReports().subscribe({
      next: (reports: ReportSummary[]) => {
        this.reports = reports;
        this.loading = false;
        this.message = `${reports.length} 件の帳票を取得しました。`;
      },
      error: () => {
        this.loading = false;
        this.message = '帳票一覧の取得に失敗しました。';
      }
    });
  }

  select(report: ReportSummary): void {
    this.selectedReport = report;
    this.message = `${report.name} を選択しました。`;
  }

  showPdf(): void {
    if (!this.selectedReport) {
      this.message = 'PDF表示する帳票を選択してください。';
      return;
    }
    this.loading = true;
    this.message = 'SVF Cloud mock にPDF変換を依頼しています。';
    this.reportService.fetchPdf(this.selectedReport.id).subscribe({
      next: (blob: Blob) => {
        this.revokeObjectUrl();
        this.objectUrl = URL.createObjectURL(blob);
        window.open(this.objectUrl, '_blank');
        this.reports = this.reports.map((report) =>
          report.id === this.selectedReport?.id ? { ...report, pdfConverted: true } : report
        );
        this.loading = false;
        this.message = `PDF変換が完了しました。新しいタブで表示しています。実行ユーザー: ${this.currentUser?.userName ?? ''}`;
      },
      error: () => {
        this.loading = false;
        this.message = 'PDF変換に失敗しました。';
      }
    });
  }

  ngOnDestroy(): void {
    this.revokeObjectUrl();
  }

  private revokeObjectUrl(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = undefined;
    }
  }
}
