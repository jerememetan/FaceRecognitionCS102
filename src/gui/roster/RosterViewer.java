package gui.roster;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import entity.Roster;
import gui.homepage.UIComponents;
import report.ExportPanel;
import service.roster.RosterManager;

// First page you see when clicking "Manage Rosters" from RosterSessionMenu
// View all available rosters
public class RosterViewer extends JFrame {

    private RosterManager manager;
    private JTable rosterTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton, createButton, deleteButton, exportButton;

    public RosterViewer(RosterManager manager) {
        this.manager = manager;

        setTitle("Roster Viewer");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel);

        // --- Table setup ---
        String[] columns = {"ID", "Course Code", "Start Time", "End Time", "Location"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        rosterTable = new JTable(tableModel);
        rosterTable.setRowHeight(28);
        rosterTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        rosterTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));
        rosterTable.getTableHeader().setReorderingAllowed(false);
        rosterTable.getTableHeader().setResizingAllowed(false);
        rosterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(rosterTable);
        TitledBorder border = BorderFactory.createTitledBorder("Double click a roster to view/edit details");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 14));
        scrollPane.setBorder(border);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // --- Top controls ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        refreshButton = UIComponents.createAccentButton("🔄 Refresh", new Color(59, 130, 246));
        createButton = UIComponents.createAccentButton("➕ Create Roster", new Color(59, 246, 59));
        deleteButton = UIComponents.createAccentButton("🗑️ Delete Roster", new Color(239, 68, 68));
        topPanel.add(refreshButton);
        topPanel.add(createButton);
        topPanel.add(deleteButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        refreshButton.addActionListener(e -> refreshTable());
        createButton.addActionListener(e -> {
            RosterForm rosterForm = new RosterForm(this, manager);
            rosterForm.setVisible(true);
            refreshTable();
        });
        deleteButton.addActionListener(e -> deleteRoster());

        // --- Double click handler ---
        rosterTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int row = rosterTable.getSelectedRow();
                    System.out.println("Selected row " + row);
                    if (row >= 0) {
                        Object rosterObj = tableModel.getValueAt(row, 0);
                        int rosterId;
                        // set rosterId to the selected rosterId
                        if (rosterObj instanceof Number) {
                            rosterId = ((Number) rosterObj).intValue();
                            System.out.println("Selected roster ID: " + rosterId);
                        } else {
                            rosterId = Integer.parseInt(rosterObj.toString());
                            System.out.println("Selected roster ID: " + rosterId);
                        }
                        Roster selected = manager.getAllRosters().stream()
                                .filter(r -> Integer.parseInt(r.getRosterId()) == rosterId)
                                .findFirst()
                                .orElse(null);
                        if (selected != null) {
                            RosterDetailsManagement rosterManagement = new RosterDetailsManagement(RosterViewer.this, manager, selected);
                            rosterManagement.setVisible(true);
                        }
                    }
                }
            }
        });

        // --- Bottom Export button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        exportButton = UIComponents.createAccentButton("📤 Export Report", new Color(99, 102, 241));
        bottomPanel.add(exportButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        exportButton.addActionListener(e -> {
            // Prepare headers and data for ExportPanel
            List<Roster> rosters = manager.getAllRosters();
            ArrayList<String> headers = new ArrayList<>();
            headers.add("ID");
            headers.add("Course Code");
            headers.add("Start Time");
            headers.add("End Time");
            headers.add("Location");

            ArrayList<ArrayList<String>> data = new ArrayList<>();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm"); // format LocalTime to string
            for (Roster r : rosters) {
                ArrayList<String> row = new ArrayList<>();
                row.add(r.getRosterId());
                row.add(r.getCourseCode());
                row.add(r.getStartTime().format(timeFormatter));  // convert LocalTime to String
                row.add(r.getEndTime().format(timeFormatter));    // convert LocalTime to String
                row.add(r.getLocation());
                data.add(row);
            }

            // Open ExportPanel and pass "AllRosters" as the title
            ExportPanel exportPanel = new ExportPanel( "AllRosters", headers, data);
            exportPanel.setVisible(true);
        });

        refreshTable();
        setVisible(true);
    }

    // === Refresh Table ===
    private void refreshTable() {
        tableModel.setRowCount(0);
        List<Roster> rosters = manager.getAllRosters();
        for (Roster r : rosters) {
            tableModel.addRow(new Object[]{
                    r.getRosterId(),
                    r.getCourseCode(),
                    r.getStartTime(),
                    r.getEndTime(),
                    r.getLocation()
            });
        }
    }

    // === Delete selected Roster ===
    private void deleteRoster() {
        int selectedRow = rosterTable.getSelectedRow();
        if (selectedRow >= 0) {
            Object rosterObj = tableModel.getValueAt(selectedRow, 0);
            int rosterId;
            if (rosterObj instanceof Number) {
                rosterId = ((Number) rosterObj).intValue();
            } else {
                rosterId = Integer.parseInt(rosterObj.toString());
            }
            System.out.println(rosterId);
            int choice = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete roster?",
                    "Delete Roster",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                boolean success = manager.deleteRoster(rosterId);
                if (success) {
                    JOptionPane.showMessageDialog(this,
                            "Roster ID " + rosterId + " deleted successfully.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshTable();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete roster ID " + rosterId + ".",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Please click on a roster (row) before deleting.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}
