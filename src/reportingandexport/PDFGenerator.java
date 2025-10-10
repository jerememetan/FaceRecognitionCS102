// CSVGenerator: creates a new .pdf file in export folder
package reportingandexport;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

public class PDFGenerator implements ReportGenerator {
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
        String exportedFolderPath = "./data/export/PDF/";

        // Get the count of .*** files in export folder
        // Error Handling to access export files
        int newPDFCount = 0;
        try {
            long pdfCount = util.countFilesInFolder(exportedFolderPath, "pdf");
            newPDFCount = (int)pdfCount + 1;
        } catch (IOException e) {
            System.err.println("PDFReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        }

        // New Exported File Name with incremented count
        String fileName = String.format("StudentFaceRecognitionData%d.pdf", newPDFCount);

        // Exported File Name with full path to export folder
        String exportedFileName = exportedFolderPath + fileName;


        // Fill the exported file with data
        try {
            PdfWriter writer = new PdfWriter(exportedFileName);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            for (List<String> row : fullData) {
                document.add(new Paragraph(String.join(", ", row)));
            }
            System.out.printf("\nPDF file [ %s ] created successfully!", fileName);
            document.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ReportGenerator generator = new PDFGenerator();
        generator.generate();
    }
}
