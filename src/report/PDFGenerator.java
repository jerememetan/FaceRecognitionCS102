package report;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import entity.Student;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import config.*;
// CSVGenerator: creates a new .pdf file in export folder
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

        // =======================================================================
        // ==============PREVOUS EXPORT METHOD WITHOUT FILECHOOSER================
        // =======================================================================
        // // Export Path
        // String exportedFolderPath = "./data/export/PDF/";

        // // Get the count of .*** files in export folder
        // // Error Handling to access export files
        // int newPDFCount = 0;
        // try {
        //     long pdfCount = util.countFilesInFolder(exportedFolderPath, "pdf");
        //     newPDFCount = (int)pdfCount + 1;
        // } catch (IOException e) {
        //     System.err.println("PDFReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        // }

        // // New Exported File Name with incremented count
        // String fileName = String.format("StudentFaceRecognitionData%d.pdf", newPDFCount);

        // // Exported File Name with full path to export folder
        // String exportedFileName = exportedFolderPath + fileName;


        String fileName = "StudentFaceRecognitionData.pdf";

        try {
            // Prompt user to save the file using JFileChooser
            JFrame frame = new JFrame("PDF Export");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);
            frame.setVisible(true);
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(frame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();

                    PdfWriter writer = new PdfWriter(fileToSave.getAbsolutePath());
                    PdfDocument pdf = new PdfDocument(writer);
                    Document document = new Document(pdf);

                    Table table = new Table(headers.size());
                    for (List<String> row : fullData) {
                        for (String cellData : row) {
                            table.addCell(new Cell().add(new Paragraph(cellData)));
                        }
                    }
                    document.add(table);

                    document.close();
                    AppLogger.info("PDF file saved successfully to: " + fileToSave.getAbsolutePath());
                } else {
                    AppLogger.info("File save cancelled by user.");
                }

                frame.dispose(); // Close the frame
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public static void main(String[] args) {
        ReportGenerator generator = new PDFGenerator();
        generator.generate();
    }
}






