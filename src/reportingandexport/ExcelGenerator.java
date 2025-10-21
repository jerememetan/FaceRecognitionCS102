// ExcelGenerator: creates a new .xls file in export folder
package reportingandexport;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ConfigurationAndLogging.*;

public class ExcelGenerator implements ReportGenerator {
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
        String exportedFolderPath = AppConfig.getInstance().getExportExcelFolderPath();

        // Get the count of .*** files in export folder
        // Error Handling to access export files
        int newPDFCount = 0;
        try {
            long pdfCount = util.countFilesInFolder(exportedFolderPath, "xlsx");
            newPDFCount = (int)pdfCount + 1;
        } catch (IOException e) {
            System.err.println("ExcelReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        }

        // New Exported File Name with incremented count
        String fileName = String.format("StudentFaceRecognitionData%d.xlsx", newPDFCount);

        // Exported File Name with full path to export folder
        String exportedFileName = exportedFolderPath + fileName;


        // Fill the exported file with data
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Report");
            int rowNum = 0;
            for (List<String> rowData : fullData) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < rowData.size(); i++) {
                    row.createCell(i).setCellValue(rowData.get(i));
                }
            }
            for (int i = 0; i < fullData.get(0).size(); i++) {
                sheet.autoSizeColumn(i);
            }
            FileOutputStream out = new FileOutputStream(exportedFileName);
            workbook.write(out);
            out.close();
            workbook.close();

            System.out.printf("\nExcel file [ %s ] created successfully!", fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ReportGenerator generator = new ExcelGenerator();
        generator.generate();
    }
}
