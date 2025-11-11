package gui.attendance;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
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
import entity.Roster;
import entity.Session;
import entity.Student;
import repository.AttendanceRecordRepository;
import repository.AttendanceRecordRepositoryInstance;
import repository.RosterRepository;
import repository.RosterRepositoryInstance;
import repository.SessionRepositoryInstance;

/**
 * Window for viewing attendance history in a hierarchical view:
 * Level 1: Rosters
 * Level 2: Sessions (when roster is clicked)
 * Level 3: Attendance Records (when session is clicked)
 */
public class AttendanceHistoryViewer extends JFrame {

    private enum ViewLevel {
        ROSTERS, SESSIONS, ATTENDANCE_RECORDS
    }

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final RosterRepository rosterRepository;
    private final SessionRepositoryInstance sessionRepository;
    
    private DefaultTableModel tableModel;
    private JTable dataTable;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private JButton backButton;
    
    private ViewLevel currentLevel = ViewLevel.ROSTERS;
    private Roster selectedRoster = null;
    private Session selectedSession = null;
    
    // Column definitions for each level
    private static final String[] ROSTER_COLUMNS = {
        "Roster ID", "Course Code", "Location", "Start Time", "End Time"
    };
    
    private static final String[] SESSION_COLUMNS = {
        "Session ID", "Session Name", "Course Code", "Date", "Start Time", "End Time"
    };
    
    private static final String[] ATTENDANCE_COLUMNS = {
        "Session ID", "Session Name", "Course Code", "Date", "Student ID", "Student Name",
        "Status", "Timestamp", "Method", "Confidence Level", "Notes"
    };

    public AttendanceHistoryViewer() {
        super("Attendance History");
        this.attendanceRecordRepository = new AttendanceRecordRepositoryInstance();
        this.rosterRepository = new RosterRepositoryInstance();
        this.sessionRepository = new SessionRepositoryInstance();

        setSize(1400, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initializeUI();
        loadRosters();
    }

    private void initializeUI() {
        // Top panel with navigation and search
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left side: Back button and breadcrumb
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        backButton = new JButton("← Back");
        backButton.setVisible(false); // Hidden by default (at top level)
        backButton.addActionListener(e -> navigateBack());
        navPanel.add(backButton);
        
        JLabel breadcrumbLabel = new JLabel("Rosters");
        breadcrumbLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        navPanel.add(breadcrumbLabel);
        topPanel.add(navPanel, BorderLayout.WEST);

        // Center: Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        searchField = new JTextField(30);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setToolTipText("Search in the current view");

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearSearch());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshCurrentView());

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(clearButton);
        searchPanel.add(refreshButton);
        topPanel.add(searchPanel, BorderLayout.CENTER);

        // Right side: Export button
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton("📤 Export Options");
        exportButton.setBackground(new Color(37, 99, 235));
        exportButton.setForeground(Color.WHITE);
        exportButton.setFocusPainted(false);
        exportButton.addActionListener(e -> exportCurrentView());
        exportPanel.add(exportButton);
        topPanel.add(exportPanel, BorderLayout.EAST);

        searchField.addActionListener(e -> performSearch());
        add(topPanel, BorderLayout.NORTH);

        // Table panel
        JPanel tablePanel = new JPanel(new BorderLayout());
        tableModel = new DefaultTableModel(ROSTER_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only table
            }
        };

        dataTable = new JTable(tableModel);
        dataTable.setRowHeight(25);
        dataTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dataTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        dataTable.setAutoCreateRowSorter(true);

        // Add mouse listener for row clicks
        dataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Double-click
                    int row = dataTable.getSelectedRow();
                    if (row >= 0) {
                        handleRowDoubleClick(row);
                    }
                }
            }
        });

        // Create row sorter for search functionality
        sorter = new TableRowSorter<>(tableModel);
        dataTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Data",
                TitledBorder.CENTER,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14)
        ));

        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // Status label at bottom
        statusLabel = new JLabel("Loading...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        add(tablePanel, BorderLayout.CENTER);
    }

    private void handleRowDoubleClick(int viewRow) {
        int modelRow = dataTable.convertRowIndexToModel(viewRow);
        
        switch (currentLevel) {
            case ROSTERS:
                // Get roster ID from the row
                String rosterId = (String) tableModel.getValueAt(modelRow, 0);
                loadSessionsForRoster(rosterId);
                break;
            case SESSIONS:
                // Get session ID from the row
                String sessionId = (String) tableModel.getValueAt(modelRow, 0);
                loadAttendanceRecordsForSession(sessionId);
                break;
            case ATTENDANCE_RECORDS:
                // No action on attendance records
                break;
        }
    }

    private void navigateBack() {
        switch (currentLevel) {
            case SESSIONS:
                // Go back to rosters
                currentLevel = ViewLevel.ROSTERS;
                selectedRoster = null;
                backButton.setVisible(false); // Hide at top level
                loadRosters();
                break;
            case ATTENDANCE_RECORDS:
                // Go back to sessions
                if (selectedRoster != null) {
                    currentLevel = ViewLevel.SESSIONS;
                    selectedSession = null;
                    backButton.setVisible(true); // Keep visible when going back to sessions
                    loadSessionsForRoster(selectedRoster.getRosterId());
                }
                break;
            case ROSTERS:
                // Already at top level, nothing to do
                break;
        }
    }

    private void loadRosters() {
        statusLabel.setText("Loading rosters...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        new Thread(() -> {
            try {
                List<Roster> rosters = rosterRepository.findAll();
                
                SwingUtilities.invokeLater(() -> {
                    try {
                        updateTableColumns(ROSTER_COLUMNS);
                        tableModel.setRowCount(0);
                        
                        for (Roster roster : rosters) {
                            Object[] row = {
                                roster.getRosterId(),
                                roster.getCourseCode(),
                                roster.getLocation(),
                                roster.getStartTime() != null ? roster.getStartTime().toString() : "-",
                                roster.getEndTime() != null ? roster.getEndTime().toString() : "-"
                            };
                            tableModel.addRow(row);
                        }
                        
                        statusLabel.setText("Loaded " + rosters.size() + " rosters. Double-click a row to view sessions.");
                        AppLogger.info("Loaded " + rosters.size() + " rosters for history viewer");
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                });
            } catch (Exception e) {
                AppLogger.error("Error loading rosters: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    statusLabel.setText("Error loading rosters");
                    JOptionPane.showMessageDialog(this,
                            "Error loading rosters: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "RosterLoader").start();
    }

    private void loadSessionsForRoster(String rosterId) {
        statusLabel.setText("Loading sessions...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        new Thread(() -> {
            try {
                // Find the roster
                Roster roster = rosterRepository.findById(rosterId);
                if (roster == null) {
                    SwingUtilities.invokeLater(() -> {
                        setCursor(Cursor.getDefaultCursor());
                        JOptionPane.showMessageDialog(this,
                                "Roster not found: " + rosterId,
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                selectedRoster = roster;
                
                // Get all sessions and filter by course code (matching roster's course code)
                // Since sessions don't have course code, we'll match by location or get all sessions
                // For now, we'll get all sessions and let the user see them
                // TODO: If sessions have course code in DB, filter by that
                List<Session> allSessions = sessionRepository.findAll();
                List<Session> matchingSessions = allSessions.stream()
                    .filter(s -> s.getLocation() != null && s.getLocation().equals(roster.getLocation()))
                    .collect(Collectors.toList());
                
                // If no sessions match by location, show all sessions
                if (matchingSessions.isEmpty()) {
                    matchingSessions = allSessions;
                }
                
                final List<Session> sessionsToShow = matchingSessions;
                
                SwingUtilities.invokeLater(() -> {
                    try {
                        currentLevel = ViewLevel.SESSIONS;
                        backButton.setVisible(true); // Show back button when viewing sessions
                        updateTableColumns(SESSION_COLUMNS);
                        tableModel.setRowCount(0);
                        
                        for (Session session : sessionsToShow) {
                            Object[] row = {
                                session.getSessionId(),
                                session.getName(),
                                roster.getCourseCode(), // Use roster's course code
                                session.getDate() != null ? session.getDate().toString() : "-",
                                session.getStartTime() != null ? session.getStartTime().toString() : "-",
                                session.getEndTime() != null ? session.getEndTime().toString() : "-"
                            };
                            tableModel.addRow(row);
                        }
                        
                        statusLabel.setText("Loaded " + sessionsToShow.size() + " sessions for course " + roster.getCourseCode() + ". Double-click a row to view attendance records.");
                        AppLogger.info("Loaded " + sessionsToShow.size() + " sessions for roster " + rosterId);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                });
            } catch (Exception e) {
                AppLogger.error("Error loading sessions: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    statusLabel.setText("Error loading sessions");
                    JOptionPane.showMessageDialog(this,
                            "Error loading sessions: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "SessionLoader").start();
    }

    private void loadAttendanceRecordsForSession(String sessionId) {
        statusLabel.setText("Loading attendance records...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        new Thread(() -> {
            try {
                Session session = sessionRepository.findById(sessionId);
                if (session == null) {
                    SwingUtilities.invokeLater(() -> {
                        setCursor(Cursor.getDefaultCursor());
                        JOptionPane.showMessageDialog(this,
                                "Session not found: " + sessionId,
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                selectedSession = session;
                
                List<AttendanceRecord> records = attendanceRecordRepository.findBySessionId(sessionId);
                
                SwingUtilities.invokeLater(() -> {
                    try {
                        currentLevel = ViewLevel.ATTENDANCE_RECORDS;
                        backButton.setVisible(true); // Show back button when viewing attendance records
                        updateTableColumns(ATTENDANCE_COLUMNS);
                        tableModel.setRowCount(0);
                        
                        String courseCode = selectedRoster != null ? selectedRoster.getCourseCode() : "-";
                        
                        for (AttendanceRecord record : records) {
                            if (record == null || record.getStudent() == null || record.getSession() == null) {
                                continue;
                            }
                            
                            Student student = record.getStudent();
                            
                            String timestampStr = record.getTimestamp() != null ?
                                    record.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-";
                            
                            String confidenceStr = record.getConfidence() != null ?
                                    String.format("%.1f%%", record.getConfidence() * 100) : "-";
                            
                            String dateStr = session.getDate() != null ?
                                    session.getDate().toString() : "-";
                            
                            Object[] row = {
                                session.getSessionId(),
                                session.getName(),
                                courseCode,
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
                        
                        statusLabel.setText("Loaded " + records.size() + " attendance records for session " + session.getName());
                        AppLogger.info("Loaded " + records.size() + " attendance records for session " + sessionId);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                });
            } catch (Exception e) {
                AppLogger.error("Error loading attendance records: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    statusLabel.setText("Error loading attendance records");
                    JOptionPane.showMessageDialog(this,
                            "Error loading attendance records: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "AttendanceRecordLoader").start();
    }

    private void updateTableColumns(String[] columns) {
        tableModel.setColumnIdentifiers(columns);
        
        // Adjust column widths based on level
        if (columns == ROSTER_COLUMNS) {
            dataTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Roster ID
            dataTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Course Code
            dataTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Location
            dataTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Start Time
            dataTable.getColumnModel().getColumn(4).setPreferredWidth(100); // End Time
        } else if (columns == SESSION_COLUMNS) {
            dataTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Session ID
            dataTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Session Name
            dataTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Course Code
            dataTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Date
            dataTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Start Time
            dataTable.getColumnModel().getColumn(5).setPreferredWidth(100); // End Time
        } else if (columns == ATTENDANCE_COLUMNS) {
            dataTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Session ID
            dataTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Session Name
            dataTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Course Code
            dataTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Date
            dataTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Student ID
            dataTable.getColumnModel().getColumn(5).setPreferredWidth(150); // Student Name
            dataTable.getColumnModel().getColumn(6).setPreferredWidth(80);  // Status
            dataTable.getColumnModel().getColumn(7).setPreferredWidth(180); // Timestamp
            dataTable.getColumnModel().getColumn(8).setPreferredWidth(100); // Method
            dataTable.getColumnModel().getColumn(9).setPreferredWidth(120); // Confidence
            dataTable.getColumnModel().getColumn(10).setPreferredWidth(200); // Notes
        }
    }

    private void refreshCurrentView() {
        switch (currentLevel) {
            case ROSTERS:
                loadRosters();
                break;
            case SESSIONS:
                if (selectedRoster != null) {
                    loadSessionsForRoster(selectedRoster.getRosterId());
                }
                break;
            case ATTENDANCE_RECORDS:
                if (selectedSession != null) {
                    loadAttendanceRecordsForSession(selectedSession.getSessionId());
                }
                break;
        }
    }

    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }

        RowFilter<DefaultTableModel, Object> filter = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                String searchLower = searchText.toLowerCase();
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
        int visibleRows = dataTable.getRowCount();
        statusLabel.setText("Showing " + visibleRows + " results (Search: '" + searchText + "')");
    }

    private void clearSearch() {
        searchField.setText("");
        sorter.setRowFilter(null);
        refreshCurrentView();
    }

    private void exportCurrentView() {
        try {
            ArrayList<String> headers = new ArrayList<>();
            for (int i = 0; i < tableModel.getColumnCount(); i++) {
                headers.add(tableModel.getColumnName(i));
            }

            ArrayList<ArrayList<String>> data = new ArrayList<>();
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                ArrayList<String> rowData = new ArrayList<>();
                for (int col = 0; col < tableModel.getColumnCount(); col++) {
                    Object value = tableModel.getValueAt(row, col);
                    rowData.add(value != null ? value.toString() : "");
                }
                data.add(rowData);
            }

            String exportName = "AttendanceHistory_" + currentLevel.toString();
            report.ExportPanel exportWindow = new report.ExportPanel(exportName, headers, data);
            exportWindow.setVisible(true);

            AppLogger.info("Opened Export Panel for " + exportName);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open export panel: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
