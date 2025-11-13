package gui.student;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import config.AppLogger;
import entity.Student;
import gui.homepage.UIComponents;
import repository.StudentRepository;
import repository.StudentRepositoryInstance;
import service.student.StudentManager;
import gui.student.StudentTableController;
import util.*;

public class StudentEnrollmentGUI extends JFrame {
    private StudentManager studentManager;
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField searchField;
    private JButton addButton, editButton, deleteButton, captureButton, refreshButton;
    private JButton exportCsvButton, exportExcelButton, exportPdfButton;
    private JButton showAllButton, showWithFacesButton, showWithoutFacesButton;
    private JLabel statusLabel, statsLabel;

    // New modular components
    private StudentTableController tableController;
    private StudentSearchFilter searchFilter;
    private StudentStatistics statistics;
    private StudentActionHandler actionHandler;

    private final String[] COLUMN_NAMES = { "Student ID", "Name", "Email", "Phone", "Face Images", "Quality Score" };

    public StudentEnrollmentGUI() {
        initializeServices();
        initializeGUI();
        loadStudentData();
    }

    private void initializeServices() {
        try {
            this.studentManager = new StudentManager();
            this.tableController = new StudentTableController(studentManager);
            this.searchFilter = new StudentSearchFilter();
            this.statistics = new StudentStatistics(studentManager);
            this.actionHandler = new StudentActionHandler(this, studentManager, tableController, searchFilter);
            AppLogger.info("All services initialized successfully");
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
        searchField.addActionListener(
                e -> searchFilter.performSearch(searchField.getText().trim(), rowSorter, statusLabel));

        JButton searchButton = UIComponents.createAccentButton("Search", ColourTheme.PRIMARY_COLOR);
        searchButton.addActionListener(
                e -> searchFilter.performSearch(searchField.getText().trim(), rowSorter, statusLabel));

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> searchFilter.clearSearch(searchField, rowSorter, statusLabel));

        refreshButton = new JButton("🔄 Refresh");
        refreshButton.addActionListener(actionHandler);

        showAllButton = new JButton("Show All");
        showWithFacesButton = new JButton("With Faces");
        showWithoutFacesButton = new JButton("Without Faces");

        showAllButton.addActionListener(e -> searchFilter.filterStudents("all", rowSorter, statusLabel));
        showWithFacesButton.addActionListener(e -> searchFilter.filterStudents("with_faces", rowSorter, statusLabel));
        showWithoutFacesButton
                .addActionListener(e -> searchFilter.filterStudents("without_faces", rowSorter, statusLabel));

        // style these controls like the sidebar buttons (blue)
        UIComponents.styleSidebarButton(searchButton);
        UIComponents.styleSidebarButton(clearButton);
        UIComponents.styleSidebarButton(refreshButton);
        UIComponents.styleSidebarButton(showAllButton);
        UIComponents.styleSidebarButton(showWithFacesButton);
        UIComponents.styleSidebarButton(showWithoutFacesButton);
        // Ensure background is painted (some LAFs require opaque)
        searchButton.setOpaque(true);
        searchButton.setContentAreaFilled(true);
        clearButton.setOpaque(true);
        clearButton.setContentAreaFilled(true);
        refreshButton.setOpaque(true);
        refreshButton.setContentAreaFilled(true);
        showAllButton.setOpaque(true);
        showAllButton.setContentAreaFilled(true);
        showWithFacesButton.setOpaque(true);
        showWithFacesButton.setContentAreaFilled(true);
        showWithoutFacesButton.setOpaque(true);
        showWithoutFacesButton.setContentAreaFilled(true);

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
                if (column == 4) {
                    return Integer.class;
                }
                if (column == 5) {
                    return Double.class;
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
                    c.setForeground(ColourTheme.SUCCESS_COLOR);
                } else if (count > 0) {
                    c.setForeground(ColourTheme.WARNING_COLOR);
                } else {
                    c.setForeground(ColourTheme.DANGER);
                }

                return c;
            }
        });

        studentTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (value instanceof Number) {
                    double tightness = ((Number) value).doubleValue();
                    setText(String.format("%.3f", tightness));
                    if (!isSelected) {
                        if (tightness >= 0.90) {
                            c.setForeground(ColourTheme.SUCCESS_COLOR);
                        } else if (tightness >= 0.80) {
                            c.setForeground(ColourTheme.WARNING_COLOR);
                        } else {
                            c.setForeground(ColourTheme.DANGER);
                        }
                    }
                } else {
                    setText("N/A");
                    if (!isSelected) {
                        c.setForeground(new Color(158, 158, 158));
                    }
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

        addButton = UIComponents.createAccentButton("➕ Add Student", ColourTheme.SUCCESS_COLOR);
        editButton = UIComponents.createAccentButton("✏️ Edit Student", ColourTheme.WARNING_COLOR);
        deleteButton = UIComponents.createAccentButton("🗑️ Delete Student", ColourTheme.DANGER);
        captureButton = UIComponents.createAccentButton("📷 Capture Face Images", ColourTheme.PRIMARY_COLOR);

        // emphasize primary actions
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD));
        captureButton.setFont(captureButton.getFont().deriveFont(Font.BOLD));

        // Set action commands for StudentActionHandler
        addButton.setActionCommand("Add Student");
        editButton.setActionCommand("Edit Student");
        deleteButton.setActionCommand("Delete Student");
        captureButton.setActionCommand("Capture Face");

        addButton.addActionListener(actionHandler);
        editButton.addActionListener(actionHandler);
        deleteButton.addActionListener(actionHandler);
        captureButton.addActionListener(actionHandler);

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(captureButton);

        JPanel exportPanel = new JPanel(new FlowLayout());

        JButton openExportPanelButton = UIComponents.createAccentButton("📤 Export Options", ColourTheme.PRIMARY_COLOR); // Blue color
        openExportPanelButton.setActionCommand("Open Export Panel");

        openExportPanelButton.addActionListener(e -> {
            try {
                StudentRepository studentRepo = new StudentRepositoryInstance();
                List<Student> students = studentRepo.findAll();

                ArrayList<String> headers = new ArrayList<>();
                headers.add("Student ID");
                headers.add("Name");
                headers.add("Email");
                headers.add("Phone");
                headers.add("Face Images");
                headers.add("Quality Score");

                ArrayList<ArrayList<String>> data = new ArrayList<>();

                for (Student s : students) {
                    int imageCount = s.getFaceData() != null ? s.getFaceData().getImages().size() : 0;
                    double avgQuality = tableController.calculateAverageQuality(s);
                    Double tightness = tableController.calculateEmbeddingTightness(s);

                    ArrayList<String> row = new ArrayList<>();
                    row.add(s.getStudentId());
                    row.add(s.getName());
                    row.add(s.getEmail());
                    row.add(s.getPhone());
                    row.add(Integer.toString(imageCount));
                    row.add(tightness != null ? Double.toString(tightness) : (avgQuality > 0 ? Double.toString(avgQuality) : null));
                    data.add(row);
                }

                report.ExportPanel exportWindow = new report.ExportPanel("AllStudentData", headers, data);
                exportWindow.setVisible(true);

                statusLabel.setText("Opened Export Panel");
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Error loading export data");
                JOptionPane.showMessageDialog(null,
                        "Failed to open export panel: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        exportPanel.add(openExportPanelButton);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusPanel.add(new JLabel("Status: "));
        statusPanel.add(statusLabel);

        panel.add(buttonPanel);
        panel.add(exportPanel);
        panel.add(statusPanel);

        return panel;
    }

    private void loadStudentData() {
        try {
            statusLabel.setText("Loading student data...");
            statusLabel.setForeground(ColourTheme.WARNING_COLOR);

            tableController.loadStudentData(tableModel, statusLabel);
            searchFilter.setTableModel(tableModel);
            searchFilter.setRowSorter(rowSorter);

            updateStatistics();
            statusLabel.setText("Ready - " + tableModel.getRowCount() + " students loaded");
            statusLabel.setForeground(ColourTheme.SUCCESS_COLOR);

        } catch (Exception e) {
            statusLabel.setText("Error loading data");
            statusLabel.setForeground(ColourTheme.DANGER);
            JOptionPane.showMessageDialog(this,
                    "Error loading student data: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatistics() {
        statsLabel.setText(statistics.calculateStatistics());
    }

    private void cleanup() {
        if (studentManager != null) {
            studentManager.cleanup();
        }
    }

    private String htmlize(String text) {
        if (text == null)
            return "";
        String trimmed = text.trim();
        if (trimmed.toLowerCase().startsWith("<html>"))
            return text;
        String escaped = trimmed
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>")
                .replace("\\n", "<br/>");
        return "<html>" + escaped + "</html>";
    }

    // Getter for studentTable to be used by StudentActionHandler
    public JTable getStudentTable() {
        return studentTable;
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
