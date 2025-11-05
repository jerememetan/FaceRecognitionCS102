package gui.student;

import entity.Student;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import model.FaceImage;
import service.student.StudentManager;
import config.*;
public class StudentTableController {
    private StudentManager studentManager;
    private DefaultTableModel tableModel;

    public StudentTableController(StudentManager studentManager) {
        this.studentManager = studentManager;
    }

    public void loadStudentData(DefaultTableModel tableModel, JLabel statusLabel) {
        this.tableModel = tableModel;
        try {
            statusLabel.setText("Loading student data...");
            statusLabel.setForeground(new Color(255, 152, 0));

            tableModel.setRowCount(0);
            List<Student> students = studentManager.getAllStudents();
            AppLogger.info("DEBUG StudentTableController.loadStudentData(): Loaded " + students.size() + " students from database");

            for (Student student : students) {
                int imageCount = student.getFaceData() != null ? student.getFaceData().getImages().size() : 0;
                double avgQuality = calculateAverageQuality(student);
                Object[] rowData = {
                        student.getStudentId(),
                        student.getName(),
                        student.getEmail() != null ? student.getEmail() : "",
                        student.getPhone() != null ? student.getPhone() : "",
                        imageCount,
                        avgQuality > 0 ? (int) (avgQuality * 100) : 0
                };
                tableModel.addRow(rowData);
            }

            AppLogger.info("DEBUG StudentTableController.loadStudentData(): Added " + tableModel.getRowCount() + " rows to table model");

        } catch (Exception e) {
            statusLabel.setText("Error loading data");
            statusLabel.setForeground(new Color(211, 47, 47));
            JOptionPane.showMessageDialog(null,
                    "Error loading student data: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshTable() {
        AppLogger.info("DEBUG StudentTableController.refreshTable(): Refreshing table");
        if (tableModel != null) {
            AppLogger.info("DEBUG StudentTableController.refreshTable(): tableModel is not null, calling loadStudentData");
            loadStudentData(tableModel, new JLabel()); // Simplified refresh
            AppLogger.info("DEBUG StudentTableController.refreshTable(): loadStudentData completed");
        } else {
            AppLogger.info("DEBUG StudentTableController.refreshTable(): tableModel is null!");
        }
    }

    public Student getStudentAt(int rowIndex) {
        if (tableModel == null || rowIndex < 0 || rowIndex >= tableModel.getRowCount()) {
            return null;
        }
        String studentId = (String) tableModel.getValueAt(rowIndex, 0);
        return studentManager.findStudentById(studentId);
    }

    private double calculateAverageQuality(Student student) {
        if (student.getFaceData() == null || student.getFaceData().getImages().isEmpty()) {
            return 0.0;
        }
        double totalQuality = 0.0;
        int count = 0;
        for (model.FaceImage faceImage : student.getFaceData().getImages()) {
            totalQuality += faceImage.getQualityScore();
            count++;
        }
        return count > 0 ? totalQuality / count : 0.0;
    }
}






