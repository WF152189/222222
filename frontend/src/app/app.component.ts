import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthenticatedUser } from './auth.model';
import { AuthService } from './auth.service';
import { ReportService } from './report.service';
import { ReportNumberItem } from './report-number-item';
import { ReportSummary } from './report-summary';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
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
  /** 番号検索入力欄の入力値 */
  numberQuery = '';
  /** 初期表示時に取得した番号・PDF名の全候補 */
  numberOptions: ReportNumberItem[] = [];
  /** 候補一覧の表示状態 */
  showNumberOptions = false;
  /** blur 遅延クローズ用のタイマー ID（`window.setTimeout` はブラウザ API なので必ず `number`）。 */
  private closeOptionsTimeout?: number;

  constructor(
    private readonly reportService: ReportService,
    private readonly authService: AuthService
  ) {
    this.currentUser = this.authService.getCurrentUser();
    if (this.currentUser) {
      this.loadNumberOptions();
    }
  }

  /** 入力値との前方一致で絞り込んだ候補一覧（空入力なら全候補）。 */
  get filteredNumberOptions(): ReportNumberItem[] {
    const query = this.numberQuery.trim();
    if (!query) {
      return this.numberOptions;
    }
    return this.numberOptions.filter((item) => item.number.startsWith(query));
  }

  login(): void {
    this.loading = true;
    this.message = 'ログイン中です。';
    this.authService.login({ username: this.loginUsername, password: this.loginPassword }).subscribe({
      next: (response) => {
        this.currentUser = response.user;
        this.loading = false;
        this.message = `${response.user.userName} としてログインしました。`;
        this.loadNumberOptions();
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
    this.numberQuery = '';
    this.numberOptions = [];
    this.closeNumberOptions();
    this.revokeObjectUrl();
    this.message = 'ログアウトしました。';
  }

  /** PDF管理画面の初期表示時に番号・PDF名の一覧を取得します。 */
  loadNumberOptions(): void {
    this.reportService.fetchNumberOptions().subscribe({
      next: (options) => {
        this.numberOptions = options;
      },
      error: () => {
        this.numberOptions = [];
        this.message = '番号検索候補の取得に失敗しました。';
      }
    });
  }

  openNumberOptions(): void {
    if (this.closeOptionsTimeout) {
      window.clearTimeout(this.closeOptionsTimeout);
      this.closeOptionsTimeout = undefined;
    }
    this.showNumberOptions = true;
  }

  closeNumberOptions(): void {
    this.showNumberOptions = false;
  }

  /** フォーカスが外れたとき、候補クリックの mousedown が先に処理されるよう僅か遅らせて閉じます。 */
  onNumberInputBlur(): void {
    this.closeOptionsTimeout = window.setTimeout(() => this.closeNumberOptions(), 120);
  }

  /** 候補を選択したとき、その番号を検索入力欄に設定します。 */
  selectNumberOption(item: ReportNumberItem): void {
    this.numberQuery = item.number;
    this.closeNumberOptions();
    this.message = `番号 ${item.number}（${item.pdfName}）を検索条件に設定しました。`;
  }

  search(): void {
    this.loading = true;
    this.message = '帳票一覧を取得中です。';
    const numberCondition = this.numberQuery.trim() || undefined;
    this.reportService.searchReports(numberCondition).subscribe({
      next: (reports: ReportSummary[]) => {
        this.reports = reports;
        this.selectedReport = undefined;
        this.loading = false;
        this.message = reports.length > 0
          ? `${reports.length} 件の帳票を取得しました。`
          : '検索条件に一致する帳票がありませんでした。';
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
    if (this.closeOptionsTimeout) {
      window.clearTimeout(this.closeOptionsTimeout);
    }
    this.revokeObjectUrl();
  }

  private revokeObjectUrl(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = undefined;
    }
  }
}
