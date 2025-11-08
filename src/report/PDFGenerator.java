package report;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import config.AppLogger;

public class PDFGenerator implements ReportGenerator {

    private ArrayList<String> headers;
    private ArrayList<ArrayList<String>> data;
    private String title; // new title field

    // ✅ Constructor updated to include title
    public PDFGenerator(ArrayList<String> headers, ArrayList<ArrayList<String>> data, String title) {
        this.headers = headers;
        this.data = data;
        this.title = title;
    }

    @Override
    public boolean generate() {
        if (headers == null || data == null || headers.isEmpty()) {
            AppLogger.error("PDFGenerator: No headers or data provided.");
            return false;
        }

        // Use title for filename, fallback if null/empty
        String fileName = (title != null && !title.isEmpty() ? title : "ExportPDF") + ".pdf";

        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your PDF file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(null);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                AppLogger.info("PDF export cancelled by user.");
                return false;
            }

            File fileToSave = fileChooser.getSelectedFile();

            PdfWriter writer = new PdfWriter(fileToSave.getAbsolutePath());
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            Table table = new Table(headers.size());

            // Add header row
            for (String header : headers) {
                Cell headerCell = new Cell().add(new Paragraph(header).setBold());
                table.addCell(headerCell);
            }

            // Add data rows
            for (ArrayList<String> row : data) {
                for (String cellData : row) {
                    table.addCell(new Cell().add(new Paragraph(cellData)));
                }
            }

            document.add(table);
            document.close();

            // Modify title based on conditions
            String modifiedTitle = ReportManager.getModifiedTitle(title);
            
            // Log the report generation with the modified title
            ReportManager.addReportLog(modifiedTitle, data.size());

            AppLogger.info("PDF file saved successfully to: " + fileToSave.getAbsolutePath());
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            AppLogger.error("Error generating PDF file: " + e.getMessage());
            return false;
        }
    }
}
