package gui.attendance;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Session;
import entity.Student;
import repository.AttendanceRecordRepository;
import repository.AttendanceRecordRepositoryInstance;

/**
 * Window for viewing attendance history from the database.
 * Displays all attendance records with search functionality.
 */
public class AttendanceHistoryViewer extends JFrame {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private DefaultTableModel tableModel;
    private JTable attendanceTable;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private List<AttendanceRecord> allRecords;

    private static final String[] COLUMNS = {
            "Session ID", "Session Name", "Date", "Student ID", "Student Name",
            "Status", "Timestamp", "Method", "Confidence", "Notes"
    };

    public AttendanceHistoryViewer() {
        super("Attendance History");
        this.attendanceRecordRepository = new AttendanceRecordRepositoryInstance();
        this.allRecords = new ArrayList<>();

        setSize(1400, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initializeUI();
        loadAttendanceRecords();
    }

    private void initializeUI() {
        // Top panel with search bar
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search label and field (left side)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        searchField = new JTextField(30);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setToolTipText("Search by Session ID, Session Name, Student ID, Student Name, Status, or Notes");

        // Search button
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());

        // Clear button
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearSearch());

        // Refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadAttendanceRecords());

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(clearButton);
        searchPanel.add(refreshButton);

        topPanel.add(searchPanel, BorderLayout.CENTER);

        // Add Export button on the right
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton("📤 Export Options");
        exportButton.setBackground(new Color(37, 99, 235));
        exportButton.setForeground(Color.WHITE);
        exportButton.setFocusPainted(false);

        exportButton.addActionListener(e -> exportAttendanceRecords());

        exportPanel.add(exportButton);
        topPanel.add(exportPanel, BorderLayout.EAST);

        // Add search on Enter key
        searchField.addActionListener(e -> performSearch());

        add(topPanel, BorderLayout.NORTH);

        // Table panel
        JPanel tablePanel = new JPanel(new BorderLayout());
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only table
            }
        };

        attendanceTable = new JTable(tableModel);
        attendanceTable.setRowHeight(25);
        attendanceTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        attendanceTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        attendanceTable.setAutoCreateRowSorter(true);

        // Set column widths
        attendanceTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Session ID
        attendanceTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Session Name
        attendanceTable.getColumnModel().getColumn(2).setPreferredWidth(100);  // Date
        attendanceTable.getColumnModel().getColumn(3).setPreferredWidth(100);  // Student ID
        attendanceTable.getColumnModel().getColumn(4).setPreferredWidth(150); // Student Name
        attendanceTable.getColumnModel().getColumn(5).setPreferredWidth(80);   // Status
        attendanceTable.getColumnModel().getColumn(6).setPreferredWidth(180);   // Timestamp
        attendanceTable.getColumnModel().getColumn(7).setPreferredWidth(100);    // Method
        attendanceTable.getColumnModel().getColumn(8).setPreferredWidth(100);   // Confidence
        attendanceTable.getColumnModel().getColumn(9).setPreferredWidth(200);   // Notes

        // Create row sorter for search functionality
        sorter = new TableRowSorter<>(tableModel);
        attendanceTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(attendanceTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Attendance Records",
                TitledBorder.CENTER,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14)
        ));

        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // Status label at bottom
        statusLabel = new JLabel("Loading attendance records...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        add(tablePanel, BorderLayout.CENTER);
    }

    private void loadAttendanceRecords() {
        try {
            // Load all attendance records from database
            allRecords = attendanceRecordRepository.findAll();

            // Clear existing table data
            tableModel.setRowCount(0);

            // Populate table with records
            int loadedCount = 0;
            for (AttendanceRecord record : allRecords) {
                addTableRow(record);
                loadedCount++;
            }

            // Update status
            statusLabel.setText("Loaded " + loadedCount + " attendance records");

            AppLogger.info("Loaded " + loadedCount + " attendance records for history viewer");

        } catch (Exception e) {
            AppLogger.error("Error loading attendance records: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                    "Error loading attendance records: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addTableRow(AttendanceRecord record) {
        if (record == null || record.getStudent() == null || record.getSession() == null) {
            return;
        }

        Student student = record.getStudent();
        Session session = record.getSession();

        // Format timestamp
        String timestampStr = record.getTimestamp() != null ?
                record.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-";

        // Format confidence
        String confidenceStr = record.getConfidence() != null ?
                String.format("%.1f%%", record.getConfidence() * 100) : "-";

        // Format date
        String dateStr = session.getDate() != null ?
                session.getDate().toString() : "-";

        Object[] row = {
                session.getSessionId(),
                session.getName(),
                dateStr,
                student.getStudentId(),
                student.getName(),
                record.getStatus().toString(),
                timestampStr,
                record.getMarkingMethod() != null ? record.getMarkingMethod().toString() : "-",
                confidenceStr,
                record.getNotes() != null ? record.getNotes() : ""
        };

        tableModel.addRow(row);
    }

    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            // Show all records if search is empty
            sorter.setRowFilter(null);
            return;
        }

        // Create a row filter that searches across multiple columns
        RowFilter<DefaultTableModel, Object> filter = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                String searchLower = searchText.toLowerCase();

                // Search across all columns
                for (int i = 0; i < entry.getValueCount(); i++) {
                    Object value = entry.getValue(i);
                    if (value != null) {
                        String valueStr = value.toString().toLowerCase();
                        if (valueStr.contains(searchLower)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };

        sorter.setRowFilter(filter);

        // Update status with search results count
        int visibleRows = attendanceTable.getRowCount();
        statusLabel.setText("Showing " + visibleRows + " of " + allRecords.size() + " records (Search: '" + searchText + "')");
    }

    private void clearSearch() {
        searchField.setText("");
        sorter.setRowFilter(null);

        // Update status
        statusLabel.setText("Showing all " + allRecords.size() + " attendance records");
    }

    private void exportAttendanceRecords() {
        try {
            // Collect headers
            ArrayList<String> headers = new ArrayList<>();
            for (int i = 0; i < tableModel.getColumnCount(); i++) {
                headers.add(tableModel.getColumnName(i));
            }

            // Collect data
            ArrayList<ArrayList<String>> data = new ArrayList<>();
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                ArrayList<String> rowData = new ArrayList<>();
                for (int col = 0; col < tableModel.getColumnCount(); col++) {
                    Object value = tableModel.getValueAt(row, col);
                    rowData.add(value != null ? value.toString() : "");
                }
                data.add(rowData);
            }

            // Open your ExportPanel (reuse the existing one)
            report.ExportPanel exportWindow = new report.ExportPanel("AttendanceHistory", headers, data);
            exportWindow.setVisible(true);

            AppLogger.info("Opened Export Panel for attendance history.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open export panel: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
