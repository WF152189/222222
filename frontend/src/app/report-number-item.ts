/**
 * PDF管理画面の番号検索候補 1 件分。
 * バックエンドの GET /api/reports/numbers が返す
 * {"number": 帳票番号, "pdfName": PDF名} に対応します。
 */
export interface ReportNumberItem {
  number: string;
  pdfName: string;
}
