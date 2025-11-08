package report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import config.AppLogger;

public class ExcelGenerator implements ReportGenerator {

    private ArrayList<String> headers;
    private ArrayList<ArrayList<String>> data;
    private String title; // new title field

    // Constructor updated to include title
    public ExcelGenerator(ArrayList<String> headers, ArrayList<ArrayList<String>> data, String title) {
        this.headers = headers;
        this.data = data;
        this.title = title;
    }

    // Default constructor (if ever called by legacy code)
    public ExcelGenerator() {
        this.headers = new ArrayList<>();
        this.data = new ArrayList<>();
        this.title = "ExportExcel";
    }

    @Override
    public boolean generate() {  // change return type to boolean
        if (headers == null || data == null || headers.isEmpty()) {
            AppLogger.error("ExcelGenerator: No headers or data provided. Nothing to export.");
            return false;
        }

        try {
            List<List<String>> fullData = new ArrayList<>();
            fullData.add(new ArrayList<>(headers));
            for (ArrayList<String> row : data) fullData.add(new ArrayList<>(row));

            // Use title for filename, fallback if null/empty
            String fileName = (title != null && !title.isEmpty() ? title : "ExportExcel") + ".xlsx";
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(null);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                AppLogger.info("File save cancelled by user.");
                return false;  // <-- user cancelled
            }

            File fileToSave = fileChooser.getSelectedFile();
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Report");
            int rowNum = 0;

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (List<String> rowData : fullData) {
                Row row = sheet.createRow(rowNum);
                for (int i = 0; i < rowData.size(); i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(rowData.get(i));
                    if (rowNum == 0) cell.setCellStyle(headerStyle);
                }
                rowNum++;
            }

            for (int i = 0; i < fullData.get(0).size(); i++) sheet.autoSizeColumn(i);
            sheet.setAutoFilter(new CellRangeAddress(0, fullData.size(), 0, fullData.get(0).size() - 1));

            try (FileOutputStream out = new FileOutputStream(fileToSave)) {
                workbook.write(out);
            }
            workbook.close();

            // Modify title based on conditions
            String modifiedTitle = ReportManager.getModifiedTitle(title);
            
            // Log the report generation with the modified title
            ReportManager.addReportLog(modifiedTitle, fullData.size());

            AppLogger.info("Excel file saved successfully to: " + fileToSave.getAbsolutePath());
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            AppLogger.error("Error generating Excel file: " + e.getMessage());
            return false;
        }
    }

    // Keep this unchanged
    public static void main(String[] args) {
        ReportGenerator generator = new ExcelGenerator();
        generator.generate(); // Will not export anything if no headers/data are provided
    }
}
