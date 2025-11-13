package gui.session;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import config.AppLogger;
import entity.Session;
import gui.homepage.UIComponents;
import service.roster.RosterManager;
import service.session.SessionManager;
import util.*;
public class SessionViewer extends JFrame {

    private SessionManager manager;
    private JTable sessionTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton refreshButton, createButton, deleteButton;
    private TableRowSorter<DefaultTableModel> sorter; // For filtering

    public SessionViewer(SessionManager manager) {
        this.manager = manager;
        manager.populateSessions();
        setTitle("Sessions List");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        JPanel viewerPanel = new JPanel(new BorderLayout(10, 10));
        viewerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(viewerPanel);
        
        setLocationRelativeTo(null);

        // --- Table setup ---
        String[] columns = {"ID", "Name", "Date", "Start Time", "End Time", "Location", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        sessionTable = new JTable(tableModel);
        sessionTable.setRowHeight(30);
        sessionTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sessionTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));
        sessionTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
        sessionTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Session Name
        sessionTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Date
        sessionTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Start
        sessionTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // End
        sessionTable.getColumnModel().getColumn(5).setPreferredWidth(120); // Location
        sessionTable.getColumnModel().getColumn(6).setPreferredWidth(80);  // Status

        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionTable.getTableHeader().setReorderingAllowed(false);
        sessionTable.getTableHeader().setResizingAllowed(false);

        // Set custom renderer for Status column (column 6) to highlight with colors
        sessionTable.getColumnModel().getColumn(6).setCellRenderer(new StatusCellRenderer());

        sorter = new TableRowSorter<>(tableModel);
        sessionTable.setRowSorter(sorter);


        JScrollPane scrollPane = new JScrollPane(sessionTable);
        TitledBorder border = BorderFactory.createTitledBorder("Double click a session to view details");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 15));
        scrollPane.setBorder(border);
        viewerPanel.add(scrollPane, BorderLayout.CENTER);

        // Top panel (Refresh + Create)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JLabel searchLabel = new JLabel("Search for session by name:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        searchField = new JTextField(30);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        searchField.setPreferredSize(new Dimension(100, 35));
        searchPanel.add(searchLabel);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            searchField.getBorder(), 
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        searchPanel.add(searchField);
        topPanel.add(searchPanel, BorderLayout.WEST);
        refreshButton = UIComponents.createAccentButton("Refresh", ColourTheme.PRIMARY_COLOR );
        createButton = UIComponents.createAccentButton("➕ Create Session", ColourTheme.PRIMARY_COLOR) ;
        deleteButton = UIComponents.createAccentButton("🗑️ Delete Session", ColourTheme.DANGER);

        topPanel.add(refreshButton);
        topPanel.add(createButton);
        topPanel.add(deleteButton);
        viewerPanel.add(topPanel, BorderLayout.NORTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String text = searchField.getText().trim().toLowerCase();
                if (text.isEmpty()) {
                    sorter.setRowFilter(null); // show all
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1)); // filter by session name
                }
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });


        refreshButton.addActionListener(e -> refreshTable());

        createButton.addActionListener(e -> {
            // Open the popup form (SessionForm)
            SessionForm sessionForm = new SessionForm(this, manager, new RosterManager());  //RosterManager required in SessionForm
            sessionForm.setVisible(true);
            refreshTable();
        });



        deleteButton.addActionListener(e -> deleteSession());

        // --- Export Panel at the bottom ---
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton exportButton = UIComponents.createAccentButton("📤 Export Options", ColourTheme.PRIMARY_COLOR); // Blue color

        exportButton.addActionListener(e -> {
            try {
                // Collect headers from table model
                ArrayList<String> headers = new ArrayList<>();
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    headers.add(tableModel.getColumnName(i));
                }

                // Collect data from table model
                ArrayList<ArrayList<String>> data = new ArrayList<>();
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    ArrayList<String> rowData = new ArrayList<>();
                    for (int col = 0; col < tableModel.getColumnCount(); col++) {
                        Object value = tableModel.getValueAt(row, col);
                        rowData.add(value != null ? value.toString() : "");
                    }
                    data.add(rowData);
                }

                // Open Export Panel and pass "AllSessions" as the title
                report.ExportPanel exportWindow = new report.ExportPanel("AllSessions", headers, data);
                exportWindow.setVisible(true);

                AppLogger.info("Opened Export Panel for sessions.");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Failed to open export panel: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        exportPanel.add(exportButton);
        viewerPanel.add(exportPanel, BorderLayout.SOUTH);


        // --- Double-click a row to view details ---
        sessionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int selectedRow = sessionTable.getSelectedRow();
                    AppLogger.info("Double-clicked row: " + selectedRow);
                    if (selectedRow >= 0) {
                        Object sessionObj = tableModel.getValueAt(selectedRow, 0);
                        int sessionId;
                        if (sessionObj instanceof Number) {
                            sessionId = ((Number) sessionObj).intValue();
                            AppLogger.info("Selected session ID: " + sessionId);
                        }
                        else{
                            sessionId = Integer.parseInt(sessionObj.toString());
                            AppLogger.info("Selected session ID: " + sessionId);
                        }
                        Session session = manager.getAllSessions()
                                .stream()
                                .filter(s -> Integer.parseInt(s.getSessionId()) == sessionId)
                                .findFirst()
                                .orElse(null);
                        if (session != null) {
                            SessionRosterManagement rosterManagement = new SessionRosterManagement(SessionViewer.this, manager, session);
                            rosterManagement.setVisible(true);
                        }
                    }
                }
            }
        });
        refreshTable();
        setVisible(true);
    }

    private void deleteSession(){
        int selectedRow = sessionTable.getSelectedRow();
        if (selectedRow >= 0) {
            Object sessionObj = tableModel.getValueAt(selectedRow, 0);
            int sessionId;
            if (sessionObj instanceof Number) {
                sessionId = ((Number) sessionObj).intValue();
            }
            else{
                sessionId = Integer.parseInt(sessionObj.toString());
            }
            
            System.out.println(sessionId);
            int choice = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete session?",
                    "Delete Session",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                boolean success = manager.deleteSession(sessionId);
                if (success) {
                    JOptionPane.showMessageDialog(this,
                            "Session ID " + sessionId + " deleted successfully.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshTable();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete session ID " + sessionId + ".",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Please click on a session (row) before deleting.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0); // Clear existing rows
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        
        for (Session s : manager.getAllSessions()) {
            // Check if session should be automatically opened based on date and time
            boolean shouldBeOpen = false;
            if (s.getDate() != null && s.getStartTime() != null && s.getEndTime() != null) {
                LocalDate sessionDate = s.getDate();
                LocalTime sessionStartTime = s.getStartTime();
                LocalTime sessionEndTime = s.getEndTime();
                
                // Session should be open if:
                // 1. Current date matches session date
                // 2. Current time is after start time and before end time
                if (currentDate.equals(sessionDate) && 
                    currentTime.isAfter(sessionStartTime) && 
                    currentTime.isBefore(sessionEndTime)) {
                    shouldBeOpen = true;
                }
            }
            
            // Auto-open or auto-close session based on time window
            if (shouldBeOpen && !s.isActive()) {
                // Auto-open the session when conditions are met
                manager.openSession(s);
                AppLogger.info("Auto-opened session " + s.getSessionId() + " - within time window");
            } else if (!shouldBeOpen && s.isActive()) {
                // Auto-close the session if outside time window
                // Close if date doesn't match OR if date matches but time is outside window
                if (s.getDate() != null) {
                    if (!currentDate.equals(s.getDate())) {
                        manager.closeSession(s);
                        AppLogger.info("Auto-closed session " + s.getSessionId() + " - date mismatch");
                    } else if (s.getStartTime() != null && s.getEndTime() != null) {
                        // Date matches but time is outside window - close it
                        LocalTime sessionStartTime = s.getStartTime();
                        LocalTime sessionEndTime = s.getEndTime();
                        if (currentTime.isBefore(sessionStartTime) || currentTime.isAfter(sessionEndTime)) {
                            manager.closeSession(s);
                            AppLogger.info("Auto-closed session " + s.getSessionId() + " - outside time window");
                        }
                    }
                }
            }
            
            tableModel.addRow(new Object[]{
                    s.getSessionId(),
                    s.getName(),
                    s.getDate(),
                    s.getStartTime(),
                    s.getEndTime(),
                    s.getLocation(),
                    s.isActive() ? "Opened" : "Closed"
            });
        }
    }
    
    /**
     * Custom cell renderer for the Status column to highlight with colors.
     * Green background for "Opened", red background for "Closed".
     */
    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            // Only apply colors to the Status column
            if (column == 6) {
                String status = value != null ? value.toString() : "";
                if ("Opened".equals(status)) {
                    setBackground(ColourTheme.SUCCESS_COLOR); // Green
                    setForeground(Color.WHITE);
                } else if ("Closed".equals(status)) {
                    setBackground(ColourTheme.DANGER); // Red
                    setForeground(Color.WHITE);
                } else {
                    setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
                
                // Center align the text
                setHorizontalAlignment(JLabel.CENTER);
            } else {
                // For other columns, use default colors
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }
            
            return this;
        }
    }

    // Demo main method
    public static void main(String[] args) {
        AppLogger.info("Launching Session Viewer...");
        SessionManager manager = new SessionManager();
        manager.populateSessions();
        new SessionViewer(manager);
    }
}







