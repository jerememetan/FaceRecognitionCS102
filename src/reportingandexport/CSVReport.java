// CSVReport.java
// TODO: Can consider to switch from native FileWriter to use CSVWriter library

package reportingandexport;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CSVReport implements ReportGenerator{
    @Override
    public void generate(ArrayList<String> userData){
        ;
    }

    public static void main(String[] args) {

        String folderPath = "./data/export/";

        int newCSVCount = 0;

        try {
            long csvCount = countCsvFilesInFolder(folderPath);
            newCSVCount = (int)csvCount + 1;
        } catch (IOException e) {
            System.err.println("CSVReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        }

        String fileName = String.format("StudentFaceRecognitionData%d.csv", newCSVCount);

        // Build CSV rows from StudentData list
        List<String[]> data = new ArrayList<>();

        // CSV Headers for easy ref
        data.add(new String[] {"StudentID", "Name", "Status", "Timestamp", "Confidence", "Method", "Notes"});
        
        String studentsFileName = "./data/sampledata/sampleStudentData.txt";
        StudentData.loadSampleDataFromFile(studentsFileName);

        System.out.println(StudentData.SampleStudentData);

        // Add student data rows
        for (StudentData student : StudentData.SampleStudentData) { // From StudentData, Objects of Student Data
            data.add(new String[] {
                student.getStudentID().toString(),
                student.getName(),
                student.getStatus(),
                student.getTimestamp(),
                String.valueOf(student.getConfidence()),
                student.getMethod(),
                student.getNotes()
            });
        }

        try (FileWriter writer = new FileWriter(folderPath + fileName)) {
            for (String[] row : data) {
                writer.append(String.join(",", row));
                writer.append("\n");
            }
            System.out.println("CSV file created successfully!");
        } catch (IOException e) {
            System.err.println("CSVReport: Error writing to CSV file: " + e.getMessage());
        }
    }

    public static long countCsvFilesInFolder(String folderPath) throws IOException {
        Path dir = Paths.get(folderPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("CSVReport: countCsvFilesInFolder: Provided path is not a directory: " + folderPath);
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .count();
        }
    }
}
