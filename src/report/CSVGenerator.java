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

    // ✅ Constructor
    public CSVGenerator(ArrayList<String> headers, ArrayList<ArrayList<String>> data) {
        this.headers = headers;
        this.data = data;
    }

    @Override
    public boolean generate() {
        if (headers == null || data == null || headers.isEmpty()) {
            AppLogger.error("CSVGenerator: No headers or data provided.");
            return false;
        }

        String fileName = "ExportCSV.csv";

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
