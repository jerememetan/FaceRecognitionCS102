// ExcelGenerator: creates a new .xls file in export folder
package reportingandexport;

import java.io.FileOutputStream;
import java.io.IOException;
<<<<<<< HEAD
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

=======
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JFrame;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import ConfigurationAndLogging.*;
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

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
<<<<<<< HEAD
        String exportedFolderPath = "./data/export/Excel/";

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
=======

        // // Get the count of .*** files in export folder
        // // Error Handling to access export files
        // int newPDFCount = 0;
        // try {
        //     long pdfCount = util.countFilesInFolder(exportedFolderPath, "xlsx");
        //     newPDFCount = (int)pdfCount + 1;
        // } catch (IOException e) {
        //     System.err.println("ExcelReport: Error accessing the exportedDataFiles folder: " + e.getMessage());
        // }

        // // New Exported File Name with incremented count
        // String fileName = String.format("StudentFaceRecognitionData%d.xlsx", newPDFCount);

        String fileName = "StudentFaceRecognitionData.xlsx";

        try {
            // Prompt user to save the file using JFileChooser
            JFrame frame = new JFrame("Excel Export");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);
            frame.setVisible(true);
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose where to save your file");
            fileChooser.setSelectedFile(new File(fileName));

            int userSelection = fileChooser.showSaveDialog(frame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();

                    Workbook workbook = new XSSFWorkbook();
                    Sheet sheet = workbook.createSheet("Report");
                    int rowNum = 0;

                    // Create header cell style
                    CellStyle style = workbook.createCellStyle();
                    Font font = workbook.createFont();
                    font.setBold(true);
                    style.setFont(font);
                    style.setAlignment(HorizontalAlignment.CENTER);
                    style.setVerticalAlignment(VerticalAlignment.CENTER);
                    style.setBorderBottom(BorderStyle.THIN);
                    style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
                    style.setBorderLeft(BorderStyle.THIN);
                    style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
                    style.setBorderRight(BorderStyle.THIN);
                    style.setRightBorderColor(IndexedColors.BLACK.getIndex());
                    style.setBorderTop(BorderStyle.THIN);
                    style.setTopBorderColor(IndexedColors.BLACK.getIndex());
                    style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                    for (List<String> rowData : fullData) {
                        Row row = sheet.createRow(rowNum);
                        for (int i = 0; i < rowData.size(); i++) {
                            Cell cell = row.createCell(i);
                            cell.setCellValue(rowData.get(i));
                            if (rowNum == 0) {
                                cell.setCellStyle(style);
                            }
                        }
                        rowNum++;
                    }

                    for (int i = 0; i < fullData.get(0).size(); i++) {
                        sheet.autoSizeColumn(i);
                    }

                    sheet.setAutoFilter(new CellRangeAddress(0, fullData.size(), 0, 5));

                    try (FileOutputStream out = new FileOutputStream(fileToSave)) {
                        workbook.write(out);
                    }

                    workbook.close();
                    System.out.println("Excel file saved successfully to: " + fileToSave.getAbsolutePath());
                } else {
                    System.out.println("File save cancelled by user.");
                }

                frame.dispose(); // Close the frame
            } catch (IOException e) {
                e.printStackTrace();
            }
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    }

    public static void main(String[] args) {
        ReportGenerator generator = new ExcelGenerator();
        generator.generate();
    }
}
