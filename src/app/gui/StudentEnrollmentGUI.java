package app.gui;

import app.entity.Student;
import app.service.StudentManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Color;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;


public class StudentEnrollmentGUI extends JFrame {
    private StudentManager studentManager;
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField searchField;
    private JButton addButton, editButton, deleteButton, captureButton, refreshButton;
    private JLabel statusLabel, statsLabel;

    private final String[] COLUMN_NAMES = { "Student ID", "Name", "Email", "Phone", "Face Images", "Quality Score" };

    public StudentEnrollmentGUI() {
        initializeServices();
        initializeGUI();
        loadStudentData();
    }

    private void initializeServices() {
        try {
            this.studentManager = new StudentManager();
            System.out.println("StudentManager initialized successfully");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error initializing services: " + e.getMessage(),
                    "Initialization Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void initializeGUI() {
        setTitle("Student Manager");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
                dispose();
            }
        });

        add(createSearchPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(1100, 650);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 500));
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Search & Statistics"));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        searchField.addActionListener(e -> performSearch());

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearSearch());

        refreshButton = new JButton("🔄 Refresh");
        refreshButton.addActionListener(e -> loadStudentData());

        JButton showAllButton = new JButton("Show All");
        JButton showWithFacesButton = new JButton("With Faces");
        JButton showWithoutFacesButton = new JButton("Without Faces");

        showAllButton.addActionListener(e -> filterStudents("all"));
        showWithFacesButton.addActionListener(e -> filterStudents("with_faces"));
        showWithoutFacesButton.addActionListener(e -> filterStudents("without_faces"));

        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(clearButton);
        searchPanel.add(Box.createHorizontalStrut(20));
        searchPanel.add(refreshButton);
        searchPanel.add(Box.createHorizontalStrut(10));
        searchPanel.add(showAllButton);
        searchPanel.add(showWithFacesButton);
        searchPanel.add(showWithoutFacesButton);

        statsLabel = new JLabel("Loading statistics...");
        statsLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD));

        panel.add(searchPanel, BorderLayout.CENTER);
        panel.add(statsLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Student Records"));

        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 4 || column == 5) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        studentTable = new JTable(tableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.getTableHeader().setReorderingAllowed(false);
        studentTable.setRowHeight(30);

        rowSorter = new TableRowSorter<>(tableModel);
        studentTable.setRowSorter(rowSorter);

        studentTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        studentTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        studentTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        studentTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        studentTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        studentTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        studentTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                int count = (Integer) value;
                if (count >= 10) {
                    c.setForeground(new Color(46, 125, 50)); 
                } else if (count > 0) {
                    c.setForeground(new Color(255, 152, 0)); 
                } else {
                    c.setForeground(new Color(211, 47, 47)); 
                }

                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(studentTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel buttonPanel = new JPanel(new FlowLayout());

        addButton = new JButton("➕ Add Student");
        editButton = new JButton("✏️ Edit Student");
        deleteButton = new JButton("🗑️ Delete Student");
        captureButton = new JButton("📷 Capture Face Images");

        addButton.setBackground(new Color(76, 175, 80));
        addButton.setForeground(Color.BLACK);
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD));

        addButton.setOpaque(true);
        addButton.setContentAreaFilled(true);
        addButton.setBorderPainted(false);

        captureButton.setBackground(new Color(33, 150, 243));
        captureButton.setForeground(Color.BLACK);
        captureButton.setFont(captureButton.getFont().deriveFont(Font.BOLD));
        captureButton.setOpaque(true);
        captureButton.setContentAreaFilled(true);
        captureButton.setBorderPainted(false);

        deleteButton.setBackground(new Color(244, 67, 54));
        deleteButton.setForeground(Color.BLACK);
        deleteButton.setOpaque(true);
        deleteButton.setContentAreaFilled(true);
        deleteButton.setBorderPainted(false);
        
        addButton.addActionListener(e -> showAddStudentDialog());
        editButton.addActionListener(e -> showEditStudentDialog());
        deleteButton.addActionListener(e -> deleteSelectedStudent());
        captureButton.addActionListener(e -> showEnhancedFaceCaptureDialog());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(captureButton);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusPanel.add(new JLabel("Status: "));
        statusPanel.add(statusLabel);

        panel.add(buttonPanel);
        panel.add(statusPanel);

        return panel;
    }

    private void loadStudentData() {
        try {
            statusLabel.setText("Loading student data...");
            statusLabel.setForeground(new Color(255, 152, 0));

            tableModel.setRowCount(0);

            List<Student> students = studentManager.getAllStudents();
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

            updateStatistics();
            statusLabel.setText("Ready - " + students.size() + " students loaded");
            statusLabel.setForeground(new Color(46, 125, 50));

        } catch (Exception e) {
            statusLabel.setText("Error loading data");
            statusLabel.setForeground(new Color(211, 47, 47));
            JOptionPane.showMessageDialog(this,
                    "Error loading student data: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private double calculateAverageQuality(Student student) {
        if (student.getFaceData() == null || student.getFaceData().getImages().isEmpty()) {
            return 0.0;
        }

        double totalQuality = 0.0;
        int count = 0;

        for (app.model.FaceImage faceImage : student.getFaceData().getImages()) {
            totalQuality += faceImage.getQualityScore();
            count++;
        }

        return count > 0 ? totalQuality / count : 0.0;
    }

    private void updateStatistics() {
        List<Student> allStudents = studentManager.getAllStudents();
        int totalStudents = allStudents.size();

        int studentsWithFaces = 0;
        int totalImages = 0;

        for (Student student : allStudents) {
            if (student.getFaceData() != null && student.getFaceData().hasImages()) {
                studentsWithFaces++;
                totalImages += student.getFaceData().getImages().size();
            }
        }

        double completionRate = totalStudents > 0 ? (studentsWithFaces * 100.0) / totalStudents : 0;

        statsLabel.setText(String.format(
                "📊 Total: %d students | ✅ With faces: %d (%.1f%%) | 🖼️ Total images: %d",
                totalStudents, studentsWithFaces, completionRate, totalImages));
    }

    private void filterStudents(String filter) {
        switch (filter) {
            case "all":
                rowSorter.setRowFilter(null);
                statusLabel.setText("Showing all students");
                break;
            case "with_faces":
                rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        Integer imageCount = (Integer) entry.getValue(4);
                        return imageCount > 0;
                    }
                });
                statusLabel.setText("Showing students with face images");
                break;
            case "without_faces":
                rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        Integer imageCount = (Integer) entry.getValue(4);
                        return imageCount == 0;
                    }
                });
                statusLabel.setText("Showing students without face images");
                break;
        }
    }

    private void performSearch() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchTerm));
        }
        statusLabel.setText("Search results for: " + searchTerm);
    }

    private void clearSearch() {
        searchField.setText("");
        rowSorter.setRowFilter(null);
        statusLabel.setText("Showing all students");
    }

    private void showAddStudentDialog() {
        StudentDialog dialog = new StudentDialog(this, "Add New Student", null, studentManager);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            loadStudentData();
        }
    }

    private void showEditStudentDialog() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student to edit.");
            return;
        }

        int modelRow = studentTable.convertRowIndexToModel(selectedRow);
        String studentId = (String) tableModel.getValueAt(modelRow, 0);
        Student student = studentManager.findStudentById(studentId);

        if (student != null) {
            StudentDialog dialog = new StudentDialog(this, "Edit Student", student, studentManager);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                loadStudentData();
            }
        }
    }

    private void deleteSelectedStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student to delete.");
            return;
        }

        int modelRow = studentTable.convertRowIndexToModel(selectedRow);
        String studentId = (String) tableModel.getValueAt(modelRow, 0);
        String studentName = (String) tableModel.getValueAt(modelRow, 1);
        Integer imageCount = (Integer) tableModel.getValueAt(modelRow, 4);

    String message = String.format(
        "Are you sure you want to delete student: %s (%s)?\\n\\n" +
            "This will permanently delete:\\n" +
            "• Student record\\n" +
            "• %d face images\\n" +
            "• All associated data\\n\\n" +
            "This action cannot be undone!",
        studentName, studentId, imageCount);

    int result = JOptionPane.showConfirmDialog(this, htmlize(message), "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            statusLabel.setText("Deleting student...");
            statusLabel.setForeground(new Color(255, 152, 0));

            if (studentManager.deleteStudent(studentId)) {
                JOptionPane.showMessageDialog(this, "Student deleted successfully.");
                loadStudentData();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Error deleting student.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Error deleting student");
                statusLabel.setForeground(new Color(211, 47, 47));
            }
        }
    }

    private void showEnhancedFaceCaptureDialog() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a student to capture face images for.",
                    "No Student Selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = studentTable.convertRowIndexToModel(selectedRow);
        String studentId = (String) tableModel.getValueAt(modelRow, 0);
        Student student = studentManager.findStudentById(studentId);

        if (student != null) {
   
            FaceCaptureDialog captureDialog = new FaceCaptureDialog(this, student, studentManager);
            captureDialog.setVisible(true);

            if (captureDialog.isCaptureCompleted()) {
                loadStudentData();

                int imageCount = student.getFaceData() != null ? student.getFaceData().getImages().size() : 0;

        JOptionPane.showMessageDialog(this,
            htmlize(String.format("Face capture completed successfully!\\n\\n" +
                "Student: %s (%s)\\n" +
                "Total face images: %d\\n" +
                "Status: Ready for face recognition",
                student.getName(), student.getStudentId(), imageCount)),
            "Capture Successful",
            JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void cleanup() {
        if (studentManager != null) {
            studentManager.cleanup();
        }
    }

    private String htmlize(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.toLowerCase().startsWith("<html>")) return text;
        String escaped = trimmed
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>")
                .replace("\\n", "<br/>");
        return "<html>" + escaped + "</html>";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            UIManager.put("Button.font", new Font("Arial", Font.BOLD, 12));
            UIManager.put("Table.font", new Font("Arial", Font.PLAIN, 12));
            UIManager.put("TableHeader.font", new Font("Arial", Font.BOLD, 12));

        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new StudentEnrollmentGUI().setVisible(true);
        });
    }
}