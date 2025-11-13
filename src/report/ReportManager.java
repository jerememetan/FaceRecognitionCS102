package report;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import config.AppLogger;

public class ReportManager {
    private static final String REPORT_LOG_FILE = getReportLogFilePath();
    private static List<ReportLog> reportLogs = new ArrayList<>();

    public ReportManager() {
        reportLogs = loadReportLogs();
    }

    // Get the absolute path for the report log file based on the current working directory
    private static String getReportLogFilePath() {
        String currentDir = System.getProperty("user.dir");
        Path logFilePath = Paths.get(currentDir, "data", "export", "ReportLogs", "report_logs.csv");
        return logFilePath.toAbsolutePath().toString();
    }

    // Load report logs from a CSV file
    private static List<ReportLog> loadReportLogs() {
        List<ReportLog> logs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(REPORT_LOG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Assuming CSV format: "reportName,rowCount,exportTime"
                String[] parts = line.split(",");
                String reportName = parts[0];
                int rowCount = Integer.parseInt(parts[1]);
                LocalDateTime exportTime = LocalDateTime.parse(parts[2]);
                logs.add(new ReportLog(reportName, rowCount, exportTime));
            }
            System.out.println(reportLogs);
        } catch (IOException e) {
            // If the file does not exist or has no data, handle it gracefully
            System.out.println("No previous report logs found.");
        }
        return logs;
    }

    // Save report logs to a CSV file
    private static void saveReportLogs() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(REPORT_LOG_FILE, false))) { // 'true' for append mode
            for (ReportLog log : reportLogs) {
                // Write each log entry to the file, with a newline after each entry
                writer.write(log.getReportName() + "," + log.getRowCount() + "," + log.getExportTime() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Add a new log entry
    public static void addReportLog(String reportName, int rowCount) {
        LocalDateTime now = LocalDateTime.now();
        ReportLog newLog = new ReportLog(reportName, rowCount, now);
        reportLogs.add(newLog);
        saveReportLogs();
    }

    // Get total reports generated forever for each kind of report
    public static int getTotalReportsGenerated(String reportName) {
        return (int) reportLogs.stream().filter(log -> log.getReportName().contains(reportName)).count();
    }

    // Get total reports generated today for each kind of report
    public static int getTotalReportsGeneratedToday(String reportName) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        return (int) reportLogs.stream()
                .filter(log -> log.getReportName().contains(reportName) && log.getExportTime().isAfter(todayStart))
                .count();
    }

    // Get last export date/time for each kind of report
    public static LocalDateTime getLastExportDateTime(String reportName) {
        AppLogger.info("All reports inside manager: " + reportLogs.toString());

        if (reportName == null) {
            AppLogger.warn("⚠️ getLastExportDateTime() called with null reportName!");
            return null;
        }

        AppLogger.info("Looking up last export date/time for report: " + reportName);

        return reportLogs.stream()
                .filter(log -> {
                    if (log.getReportName() == null) {
                        AppLogger.warn("⚠️ Found a ReportLog entry with null reportName in reportLogs");
                        return false;
                    }
                    return log.getReportName().contains(reportName);
                })
                .map(ReportLog::getExportTime)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    // Get all report logs with the number of rows in each
    public static List<ReportLog> getAllReportLogs() {
        return reportLogs;
    }

    // Remove a specific report log entry
    public static boolean removeReportLog(ReportLog logToRemove) {
        return reportLogs.remove(logToRemove);
    }

    public static String getModifiedTitle(String title) {
        // Check if the title contains 'Session' and '_'
        if (title.contains("Session") && title.contains("_")) {
            return "SessionToStudent";
        }
        
        // Check if the title contains 'Roster' and '_'
        if (title.contains("Roster") && title.contains("_")) {
            return "RosterToStudent";
        }
        
        // Return the original title if no conditions are met
        return title;
    }
}
