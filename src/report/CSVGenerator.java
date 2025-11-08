package report;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFileChooser;

import config.AppLogger;

public class CSVGenerator implements ReportGenerator {

    private ArrayList<String> headers;
    private ArrayList<ArrayList<String>> data;
    private String title; // new title field

    // Constructor updated to include title
    public CSVGenerator(ArrayList<String> headers, ArrayList<ArrayList<String>> data, String title) {
        this.headers = headers;
        this.data = data;
        this.title = title; // assign title
    }

    @Override
    public boolean generate() {
        if (headers == null || data == null || headers.isEmpty()) {
            AppLogger.error("CSVGenerator: No headers or data provided.");
            return false;
        }

        // Use title for filename; fallback to default if empty
        String fileName = (title != null && !title.isEmpty() ? title : "ExportCSV") + ".csv";

        try {
            // Prompt user for save location
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your CSV file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(null);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();

                try (FileWriter writer = new FileWriter(fileToSave)) {

                    // ✅ Write headers first
                    writer.append(String.join(",", headers));
                    writer.append("\n");

                    // Write data rows
                    for (ArrayList<String> row : data) {
                        writer.append(String.join(",", row));
                        writer.append("\n");
                    }
                }

                // Modify title based on conditions
                String modifiedTitle = ReportManager.getModifiedTitle(title);
                
                // Log the report generation with the modified title
                ReportManager.addReportLog(modifiedTitle, data.size());

                AppLogger.info("CSV file saved successfully to: " + fileToSave.getAbsolutePath());
                return true;

            } else {
                AppLogger.info("CSV export cancelled by user.");
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            AppLogger.error("Error generating CSV file: " + e.getMessage());
            return false;
        }
    }
}
