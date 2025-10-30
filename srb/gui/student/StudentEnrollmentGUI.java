package gui.student;

import entity.Student;
import java.awt.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import service.student.StudentManager;

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
            System.out.println("All services initialized successfully");
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
        searchField.addActionListener(e -> searchFilter.performSearch(searchField.getText().trim(), rowSorter, statusLabel));

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchFilter.performSearch(searchField.getText().trim(), rowSorter, statusLabel));

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> searchFilter.clearSearch(searchField, rowSorter, statusLabel));

        refreshButton = new JButton("🔄 Refresh");
        refreshButton.addActionListener(actionHandler);

        showAllButton = new JButton("Show All");
        showWithFacesButton = new JButton("With Faces");
        showWithoutFacesButton = new JButton("Without Faces");

        showAllButton.addActionListener(e -> searchFilter.filterStudents("all", rowSorter, statusLabel));
        showWithFacesButton.addActionListener(e -> searchFilter.filterStudents("with_faces", rowSorter, statusLabel));
        showWithoutFacesButton.addActionListener(e -> searchFilter.filterStudents("without_faces", rowSorter, statusLabel));

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

        // Export buttons
        JPanel exportPanel = new JPanel(new FlowLayout());
        exportCsvButton = new JButton("📊 Export CSV");
        exportExcelButton = new JButton("📈 Export Excel");
        exportPdfButton = new JButton("📄 Export PDF");

        exportCsvButton.setActionCommand("Export CSV");
        exportExcelButton.setActionCommand("Export Excel");
        exportPdfButton.setActionCommand("Export PDF");

        exportCsvButton.addActionListener(actionHandler);
        exportExcelButton.addActionListener(actionHandler);
        exportPdfButton.addActionListener(actionHandler);

        exportPanel.add(exportCsvButton);
        exportPanel.add(exportExcelButton);
        exportPanel.add(exportPdfButton);

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
            statusLabel.setForeground(new Color(255, 152, 0));

            tableController.loadStudentData(tableModel, statusLabel);
            searchFilter.setTableModel(tableModel);
            searchFilter.setRowSorter(rowSorter);

            updateStatistics();
            statusLabel.setText("Ready - " + tableModel.getRowCount() + " students loaded");
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

    private void updateStatistics() {
        statsLabel.setText(statistics.calculateStatistics());
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






