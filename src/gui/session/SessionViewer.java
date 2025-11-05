package gui.session;

import entity.Session;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import service.roster.RosterManager;
import service.session.SessionManager;
import config.*;
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
        refreshButton = new JButton("Refresh");
        createButton = new JButton("➕ Create Session");
        deleteButton = new JButton("🗑️ Delete Session");

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
        for (Session s : manager.getAllSessions()) {
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

    // Demo main method
    public static void main(String[] args) {
        AppLogger.info("Launching Session Viewer...");
        SessionManager manager = new SessionManager();
        manager.populateSessions();
        new SessionViewer(manager);
    }
}







