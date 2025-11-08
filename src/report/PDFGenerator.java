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

    // Constructor
    public PDFGenerator(ArrayList<String> headers, ArrayList<ArrayList<String>> data) {
        this.headers = headers;
        this.data = data;
    }

    @Override
    public boolean generate() {
        if (headers == null || data == null || headers.isEmpty()) {
            AppLogger.error("PDFGenerator: No headers or data provided.");
            return false;
        }

        String fileName = "ExportPDF.pdf";

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

            AppLogger.info("PDF file saved successfully to: " + fileToSave.getAbsolutePath());
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            AppLogger.error("Error generating PDF file: " + e.getMessage());
            return false;
        }
    }
}
