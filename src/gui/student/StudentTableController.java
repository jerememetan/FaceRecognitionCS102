package gui.student;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

import config.AppLogger;
import entity.Student;
import service.embedding.SimilarityCalculator;
import service.student.StudentManager;
import util.*;
public class StudentTableController {
    private final StudentManager studentManager;
    private final SimilarityCalculator similarityCalculator;
    private DefaultTableModel tableModel;

    public StudentTableController(StudentManager studentManager) {
        this.studentManager = studentManager;
        this.similarityCalculator = new SimilarityCalculator(false);
    }

    public void loadStudentData(DefaultTableModel tableModel, JLabel statusLabel) {
        this.tableModel = tableModel;
        try {
            statusLabel.setText("Loading student data...");
            statusLabel.setForeground(ColourTheme.WARNING_COLOR);

            tableModel.setRowCount(0);
            List<Student> students = studentManager.getAllStudents();
            AppLogger.info("DEBUG StudentTableController.loadStudentData(): Loaded " + students.size()
                    + " students from database");

            for (Student student : students) {
                int imageCount = student.getFaceData() != null ? student.getFaceData().getImages().size() : 0;
                double avgQuality = calculateAverageQuality(student);
                Double tightness = calculateEmbeddingTightness(student);
                Object[] rowData = {
                        student.getStudentId(),
                        student.getName(),
                        student.getEmail() != null ? student.getEmail() : "",
                        student.getPhone() != null ? student.getPhone() : "",
                        imageCount,
                        tightness != null ? tightness : (avgQuality > 0 ? avgQuality : null)
                };
                tableModel.addRow(rowData);
            }

            AppLogger.info("DEBUG StudentTableController.loadStudentData(): Added " + tableModel.getRowCount()
                    + " rows to table model");

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
            AppLogger.info(
                    "DEBUG StudentTableController.refreshTable(): tableModel is not null, calling loadStudentData");
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

    // Changed from private to public to allow call for Report&Export inside StudentEnrollmentGUI
    public double calculateAverageQuality(Student student) {
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

    // Changed from private to public to allow call for Report&Export inside StudentEnrollmentGUI
    public Double calculateEmbeddingTightness(Student student) {
        if (student == null || student.getFaceData() == null) {
            return null;
        }

        String folderPath = student.getFaceData().getFolderPath();
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return null;
        }

        Path folder;
        try {
            folder = Paths.get(folderPath);
        } catch (InvalidPathException ex) {
            AppLogger.warn("Invalid embedding path for student " + student.getStudentId() + ": " + folderPath);
            return null;
        }
        if (!Files.isDirectory(folder)) {
            return null;
        }

        List<byte[]> embeddings = readEmbeddings(folder);
        if (embeddings.size() < 2) {
            return null;
        }

        int referenceLength = embeddings.get(0).length;
        embeddings.removeIf(bytes -> bytes == null || bytes.length != referenceLength);
        if (embeddings.size() < 2) {
            return null;
        }

        double sum = 0.0;
        int comparisons = 0;
        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                double similarity = similarityCalculator.calculate(embeddings.get(i), embeddings.get(j));
                if (Double.isFinite(similarity)) {
                    sum += similarity;
                    comparisons++;
                }
            }
        }

        if (comparisons == 0) {
            return null;
        }

        double tightness = sum / comparisons;
        if (Double.isInfinite(tightness) || Double.isNaN(tightness)) {
            return null;
        }
        return Math.max(-1.0, Math.min(1.0, tightness));
    }

    private List<byte[]> readEmbeddings(Path folder) {
        List<byte[]> embeddings = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.emb")) {
            for (Path path : stream) {
                try {
                    byte[] data = Files.readAllBytes(path);
                    if (data != null && data.length > 0) {
                        embeddings.add(data);
                    }
                } catch (IOException ex) {
                    AppLogger.warn("Failed to read embedding file: " + path.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            AppLogger.warn("Failed to scan embeddings in folder: " + folder.toAbsolutePath());
        }
        return embeddings;
    }
}
