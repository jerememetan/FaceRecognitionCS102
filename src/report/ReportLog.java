package report;

import java.time.LocalDateTime;

public class ReportLog {
    private String reportName;  // e.g., "AllStudents", "AllRosters"
    private int rowCount;       // Number of rows in the report
    private LocalDateTime exportTime;

    public ReportLog(String reportName, int rowCount, LocalDateTime exportTime) {
        this.reportName = reportName;
        this.rowCount = rowCount;
        this.exportTime = exportTime;
    }

    public String getReportName() {
        return reportName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public LocalDateTime getExportTime() {
        return exportTime;
    }

    @Override
    public String toString() {
        return "Report: " + reportName + ", Rows: " + rowCount + ", Time: " + exportTime.toString();
    }
}
