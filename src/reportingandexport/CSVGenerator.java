// CSVGenerator: creates a new .csv file in export folder
package reportingandexport;

import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import ConfigurationAndLogging.*;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

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

        try {
            // New Exported File Name with incremented count
            String fileName = "StudentFaceRecognitionData.csv";

            // Prompt user to save the file using JFileChooser
            JFrame frame = new JFrame("CSV Export");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);
            frame.setVisible(true);
                
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(frame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();

                if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                    fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
                }

                // Fill the exported file with data
                try (FileWriter writer = new FileWriter(fileToSave)) {
                    for (List<String> row : fullData) {
                        writer.append(String.join(",", row));
                        writer.append("\n");
                    }
                    System.out.println("CSV file saved successfully to: " + fileToSave.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("CSVReport: Error writing to CSV file: " + e.getMessage());
                }
            }

            frame.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ReportGenerator generator = new CSVGenerator();
        generator.generate();
    }
}
