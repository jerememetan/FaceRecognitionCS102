package report;

import config.*;
import entity.Student;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


// CSVGenerator: creates a new .csv file in export folder
public class CSVGenerator implements ReportGenerator {
    @Override
    public void generate(){
        // Create a ReportBuilder class
        ReportBuilder ReportBuilder = new ReportBuilder();

        // Initialize Headers in ReportBuilder
        List<String> headers = Arrays.asList("StudentID", "Name", "Status", "Timestamp", "Confidence", "Method", "Notes");
        ReportBuilder.initializeFieldHeaders(headers);

        // Initialize Sample Student Data in ReportBuilder
        String studentsFileName = "./data/sampledata/sampleStudentData.txt";
        StudentData.loadSampleDataFromFile(studentsFileName);
        ReportBuilder.initializeData(StudentData.SampleStudentData);

        // Get both Headers and Data -> fullData
        List<List<String>> fullData = ReportBuilder.getFullData();


        // Export Path
        String exportedFolderPath = AppConfig.getInstance().getExportCsvFolderPath();

        // Get the count of .*** files in export folder
        // Error Handling to access export files
        int newCSVCount = 0;
        try {
            long csvCount = util.countFilesInFolder(exportedFolderPath, "csv");
            newCSVCount = (int)csvCount + 1;
        } catch (IOException e) {
            System.err.println("\nCSVReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        }

        // New Exported File Name with incremented count
        String fileName = String.format("StudentFaceRecognitionData%d.csv", newCSVCount);

        // Exported File Name with full path to export folder
        String exportedFileName = exportedFolderPath + fileName;


        // Fill the exported file with data
        try (FileWriter writer = new FileWriter(exportedFileName)) {
            for (List<String> row : fullData) {
                writer.append(String.join(",", row));
                writer.append("\n");
            }
            System.out.printf("CSV file [ %s ] created successfully!", fileName);
        } catch (IOException e) {
            AppLogger.warn("CSVReport: Error writing to CSV file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ReportGenerator generator = new CSVGenerator();
        generator.generate();
    }
}







