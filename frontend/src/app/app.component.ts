import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthenticatedUser } from './auth.model';
import { AuthService } from './auth.service';
import { ReportService } from './report.service';
import { ReportNumberItem } from './report-number-item';
import { ReportSummary } from './report-summary';
import { ReportSearchRequest } from './report-search-request';

type DateTimeField = 'startDate' | 'startTime' | 'endDate' | 'endTime' | 'dateRange';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnDestroy {
  @ViewChild('resultBody') private resultBody?: ElementRef<HTMLTableSectionElement>;

  reports: ReportSummary[] = [];
  hasSearched = false;
  resultScrollbarWidth = 0;
  currentUser: AuthenticatedUser | null;
  loginUsername = 'zhangsan';
  loginPassword = 'password';
  selectedReport?: ReportSummary;
  loading = false;
  message = '検索ボタンを押して帳票一覧を取得してください。';
  /** 番号検索入力欄の入力値 */
  numberQuery = '';
  startDate = '';
  startTime = '';
  endDate = '';
  endTime = '';
  dateTimeErrors: Partial<Record<DateTimeField, string>> = {};
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
    this.hasSearched = false;
    this.selectedReport = undefined;
    this.numberQuery = '';
    this.startDate = '';
    this.startTime = '';
    this.endDate = '';
    this.endTime = '';
    this.dateTimeErrors = {};
    this.numberOptions = [];
    this.closeNumberOptions();
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
    if (!this.validateDateTimeCriteria()) {
      this.message = '入力内容を確認してください。';
      return;
    }
    this.loading = true;
    this.message = '帳票一覧を取得中です。';
    const numberCondition = this.numberQuery.trim() || undefined;
    this.reportService.searchReports(numberCondition, this.createSearchRequest()).subscribe({
      next: (reports: ReportSummary[]) => {
        this.reports = reports;
        this.hasSearched = true;
        this.selectedReport = undefined;
        this.loading = false;
        this.updateResultScrollbarWidth();
        this.message = reports.length > 0
          ? `${reports.length} 件の帳票を取得しました。`
          : '検索条件に一致する帳票がありませんでした。';
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.applyBackendValidationErrors(error);
      }
    });
  }

  /** 入力中および検索実行時に、PDF作成日時の形式・実在性・相関を検証します。 */
  validateDateTimeCriteria(): boolean {
    const errors: Partial<Record<DateTimeField, string>> = {};
    const startDate = this.validateDate(this.startDate, '開始年月日', 'startDate', errors);
    const endDate = this.validateDate(this.endDate, '終了年月日', 'endDate', errors);
    const startTime = this.validateTime(this.startTime, '開始時刻', 'startTime', errors);
    const endTime = this.validateTime(this.endTime, '終了時刻', 'endTime', errors);

    if (this.startTime.trim() && !this.startDate.trim()) {
      errors.startTime = '開始時刻を指定する場合は、開始年月日も入力してください。';
    }
    if (this.endTime.trim() && !this.endDate.trim()) {
      errors.endTime = '終了時刻を指定する場合は、終了年月日も入力してください。';
    }
    if (startDate && endDate && (!this.startTime.trim() || startTime) && (!this.endTime.trim() || endTime)) {
      const start = this.toComparableDateTime(startDate, startTime, false);
      const end = this.toComparableDateTime(endDate, endTime, true);
      if (start > end) {
        errors.dateRange = '開始日時は終了日時以前となるように指定してください。';
      }
    }
    this.dateTimeErrors = errors;
    return Object.keys(errors).length === 0;
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
    const report = this.selectedReport;
    this.loading = true;
    this.message = 'PDFを生成しています。';

    this.reportService.fetchPdf(report.id).subscribe({
      next: (pdfBlob) => {
        this.loading = false;
        if (this.openPdfViewer(pdfBlob, report.name, report.reportNumber)) {
          this.message = `${report.name}を新しいタブで表示しました。`;
        } else {
          this.message = 'PDF表示用のタブを開けませんでした。ブラウザーのポップアップ設定を確認してください。';
        }
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        void this.resolvePdfErrorMessage(error).then((message) => {
          this.message = message;
        });
      }
    });
  }

  /** 生成済みPDFをHTMLページ内のiframeに設定し、新しいタブで表示します。 */
  private openPdfViewer(pdfBlob: Blob, reportName: string, reportNumber: string): boolean {
    const viewerWindow = window.open('', '_blank');
    if (!viewerWindow) {
      return false;
    }

    viewerWindow.opener = null;
    const objectUrl = URL.createObjectURL(pdfBlob);
    const title = reportNumber ? `${reportName} ${reportNumber}` : reportName;

    viewerWindow.document.title = title;
    viewerWindow.document.documentElement.lang = 'ja';
    viewerWindow.document.body.style.margin = '0';
    viewerWindow.document.body.style.overflow = 'hidden';

    const frame = viewerWindow.document.createElement('iframe');
    frame.src = objectUrl;
    frame.title = 'PDFプレビュー';

    const sourceIcon = document.head.querySelector<HTMLLinkElement>('link[rel~="icon"]');

    if (sourceIcon) {
      const viewerIcon = viewerWindow.document.createElement('link');
      viewerIcon.rel = sourceIcon.rel;
      viewerIcon.type = sourceIcon.type;
      viewerIcon.href = sourceIcon.href;

      viewerWindow.document.head.appendChild(viewerIcon);
    }
    frame.style.border = '0';
    frame.style.display = 'block';
    frame.style.height = '100vh';
    frame.style.width = '100%';
    viewerWindow.document.body.appendChild(frame);

    viewerWindow.addEventListener('beforeunload', () => {
      URL.revokeObjectURL(objectUrl);
    }, { once: true });
    return true;
  }

  /** responseType=blob のエラーレスポンスから画面表示用メッセージを取得します。 */
  private async resolvePdfErrorMessage(error: HttpErrorResponse): Promise<string> {
    if (error.error instanceof Blob) {
      try {
        const body = JSON.parse(await error.error.text()) as { message?: unknown };
        if (typeof body.message === 'string' && body.message.trim()) {
          return body.message;
        }
      } catch {
        // JSON形式でない場合は、HTTPステータスに応じた共通メッセージを使用する。
      }
    } else if (typeof error.error?.message === 'string' && error.error.message.trim()) {
      return error.error.message;
    }

    if (error.status === 404) {
      return '指定された帳票が見つかりません。';
    }
    if (error.status === 502 || error.status === 503 || error.status === 504) {
      return 'PDFを生成できませんでした。時間をおいて再度実行してください。';
    }
    return 'PDFの生成に失敗しました。';
  }

  ngOnDestroy(): void {
    if (this.closeOptionsTimeout) {
      window.clearTimeout(this.closeOptionsTimeout);
    }
  }

  /** 明細スクロールバーの実幅をヘッダーの列幅調整に使用します。 */
  private updateResultScrollbarWidth(): void {
    window.requestAnimationFrame(() => {
      const body = this.resultBody?.nativeElement;
      this.resultScrollbarWidth = body ? body.offsetWidth - body.clientWidth : 0;
    });
  }

  private createSearchRequest(): ReportSearchRequest {
    return {
      startDate: this.startDate.trim() || undefined,
      startTime: this.startTime.trim() || undefined,
      endDate: this.endDate.trim() || undefined,
      endTime: this.endTime.trim() || undefined
    };
  }

  private validateDate(
    value: string,
    label: string,
    field: 'startDate' | 'endDate',
    errors: Partial<Record<DateTimeField, string>>
  ): { year: number; month: number; day: number } | undefined {
    const normalized = value.trim();
    if (!normalized) {
      return undefined;
    }
    if (!/^[0-9]{8}$/.test(normalized)) {
      errors[field] = `${label}は8桁の半角数字で入力してください。`;
      return undefined;
    }
    const parts = {
      year: Number(normalized.slice(0, 4)),
      month: Number(normalized.slice(4, 6)),
      day: Number(normalized.slice(6, 8))
    };
    const date = new Date(parts.year, parts.month - 1, parts.day);
    if (date.getFullYear() !== parts.year || date.getMonth() !== parts.month - 1 || date.getDate() !== parts.day) {
      errors[field] = `${label}に有効な日付を入力してください。`;
      return undefined;
    }
    return parts;
  }

  private validateTime(
    value: string,
    label: string,
    field: 'startTime' | 'endTime',
    errors: Partial<Record<DateTimeField, string>>
  ): { hour: number; minute: number } | undefined {
    const normalized = value.trim();
    if (!normalized) {
      return undefined;
    }
    if (!/^[0-9]{4}$/.test(normalized)) {
      errors[field] = `${label}は4桁の半角数字で入力してください。`;
      return undefined;
    }
    const parts = { hour: Number(normalized.slice(0, 2)), minute: Number(normalized.slice(2, 4)) };
    if (parts.hour > 23 || parts.minute > 59) {
      errors[field] = `${label}に有効な時刻を入力してください。`;
      return undefined;
    }
    return parts;
  }

  private toComparableDateTime(
    date: { year: number; month: number; day: number },
    time: { hour: number; minute: number } | undefined,
    endOfDay: boolean
  ): number {
    return new Date(
      date.year,
      date.month - 1,
      date.day,
      time?.hour ?? (endOfDay ? 23 : 0),
      time?.minute ?? (endOfDay ? 59 : 0),
      endOfDay && !time ? 59 : 0,
      endOfDay && !time ? 999 : 0
    ).getTime();
  }

  private applyBackendValidationErrors(error: HttpErrorResponse): void {
    const details = Array.isArray(error.error?.errors) ? error.error.errors : [];
    if (details.length > 0) {
      const fieldErrors: Partial<Record<DateTimeField, string>> = {};
      for (const detail of details) {
        if (['startDate', 'startTime', 'endDate', 'endTime', 'dateRange'].includes(detail.field)) {
          fieldErrors[detail.field as DateTimeField] = detail.message;
        }
      }
      this.dateTimeErrors = fieldErrors;
      this.message = error.error?.message || '入力内容を確認してください。';
      return;
    }
    this.message = error.error?.message || '帳票一覧の取得に失敗しました。';
  }
}
