export interface ReportSearchRequest {
  startDate?: string;
  startTime?: string;
  endDate?: string;
  endTime?: string;
}

export interface ReportSearchApiItem {
  reportId: string;
  reportName: string;
  createdDate: string;
  createdBy: string;
  outputStatus: 'NOT_OUTPUT' | 'OUTPUT_COMPLETED';
}

export interface ReportSearchApiResponse {
  reports: ReportSearchApiItem[];
  totalCount: number;
}
