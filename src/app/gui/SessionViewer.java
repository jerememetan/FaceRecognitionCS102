package app.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import app.entity.Session;
import app.test.SessionManager;

public class SessionViewer extends JFrame {

    private SessionManager manager;
    private JTable sessionTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton, createButton;

    public SessionViewer(SessionManager manager) {
        this.manager = manager;

        setTitle("Sessions List");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(sessionTable);
        TitledBorder border = BorderFactory.createTitledBorder("Double click a session to view/manage its roster");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 15));
        scrollPane.setBorder(border);
        viewerPanel.add(scrollPane, BorderLayout.CENTER);

        // Top panel (Refresh + Create)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        refreshButton = new JButton("Refresh");
        createButton = new JButton("➕ Create Session");

        topPanel.add(refreshButton);
        topPanel.add(createButton);
        viewerPanel.add(topPanel, BorderLayout.NORTH);

        refreshButton.addActionListener(e -> refreshTable());

        createButton.addActionListener(e -> {
            // Open the popup form (SessionForm)
            new SessionForm(manager);
            refreshTable();
        });

        // --- Double-click a row to view details ---
        sessionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = sessionTable.getSelectedRow();
                    System.out.println("Double-clicked row: " + selectedRow);
                    if (selectedRow >= 0) {
                        Object sessionObj = tableModel.getValueAt(selectedRow, 0);
                        int sessionId;
                        if (sessionObj instanceof Number) {
                            sessionId = ((Number) sessionObj).intValue();
                            System.out.println("Selected session ID: " + sessionId);
                        }
                        else{
                            sessionId = Integer.parseInt(sessionObj.toString());
                            System.out.println("Selected session ID: " + sessionId);
                        }
                        Session session = manager.getAllSessions()
                                .stream()
                                .filter(s -> Integer.parseInt(s.getSessionId()) == sessionId)
                                .findFirst()
                                .orElse(null);
                        if (session != null) {
                            new SessionRosterManagement(manager, session);
                        }
                    }
                }
            }
        });
        refreshTable();
        setVisible(true);
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
                    s.isActive() ? "Open" : "Closed"
            });
        }
    }

    // Demo main method
    public static void main(String[] args) {
        SessionManager manager = new SessionManager();

        // Example data
        manager.createSession("Math Workshop", java.time.LocalDate.now(),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(11, 0), "Room 101");
        manager.createSession("AI Seminar", java.time.LocalDate.now(),
                java.time.LocalTime.of(13, 0), java.time.LocalTime.of(15, 0), "Lab A");

        new SessionViewer(manager);
    }
}
