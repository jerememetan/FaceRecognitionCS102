package gui.session;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import config.AppLogger;
import entity.Session;
import entity.SessionStudent;
import entity.Student;
import gui.homepage.UIComponents;
import gui.student.AddStudentDialog;
import report.CSVGenerator;
import report.ExcelGenerator;
import report.PDFGenerator;
import repository.StudentRepositoryInstance;
import service.roster.RosterManager;
import service.session.SessionManager;

// View further details of a selected session
public class SessionRosterManagement extends JDialog {

    private JFrame parent;
    private SessionManager manager;
    private Session session;
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JButton addButton, removeButton, openButton, closeButton, editButton;
    private ArrayList<Student> allStudents; // required to add students to a session
    private JLabel nameLabel, dateLabel, timeLabel, locationLabel;

    // Export buttons
    private JButton exportCSVButton, exportExcelButton, exportPDFButton;

    public SessionRosterManagement(JFrame parent, SessionManager manager, Session session) {
        super(parent, "Session Roster Management", true);
        this.parent = parent;
        this.manager = manager;
        this.session = session;
        this.allStudents = new StudentRepositoryInstance().findAll();   //fetch students from db

        // GUI setup
        setTitle("Session Roster Management - " + session.getName());
        setSize(700, 550);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Adding padding to frame
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel);

        // Session Info Panel
        JPanel infoPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Session Details"));
        infoPanel.add(new JLabel("Session ID:"));
        infoPanel.add(new JLabel(String.valueOf(session.getSessionId())));
        infoPanel.add(new JLabel("Name:"));
        nameLabel = new JLabel(session.getName());
        infoPanel.add(nameLabel);
        infoPanel.add(new JLabel("Date:"));
        dateLabel = new JLabel(session.getDate().toString());
        infoPanel.add(dateLabel);
        infoPanel.add(new JLabel("Time:"));
        timeLabel = new JLabel(session.getStartTime() + " - " + session.getEndTime());
        infoPanel.add(timeLabel);
        infoPanel.add(new JLabel("Location:"));
        locationLabel = new JLabel(session.getLocation());
        infoPanel.add(locationLabel);

        editButton = UIComponents.createAccentButton("Edit Session Details", new Color(59, 130, 246));
        editButton.setFocusPainted(false);
        editButton.addActionListener(e -> editSessionDetails());
        infoPanel.add(editButton);

        // Open a session
        if (session.isActive()) {
            closeButton = UIComponents.createAccentButton("Close Session", new Color(239, 68, 68));
            closeButton.addActionListener(e -> { closeSession(); });
            infoPanel.add(closeButton);
        } else {
            openButton = UIComponents.createAccentButton("Open Session", new Color(59, 130, 246));
            openButton.addActionListener(e -> { openSession(); });
            infoPanel.add(openButton);
        }
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // Student Roster Table
        String[] columns = {"Student ID", "Name", "Phone No", "Status", "Remarks"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        studentTable = new JTable(tableModel);
        studentTable.setFillsViewportHeight(true);
        studentTable.setRowHeight(25);
        studentTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));
        studentTable.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(studentTable);
        TitledBorder border = BorderFactory.createTitledBorder("Student Roster");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 16));
        scrollPane.setBorder(border);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        addButton = UIComponents.createAccentButton("Add Student", new Color(34, 197, 94));
        removeButton = UIComponents.createAccentButton("Remove Student", new Color(239, 68, 68));

        addButton.addActionListener(e -> { addStudent(); refreshTable(); });
        removeButton.addActionListener(e -> { removeStudent(); refreshTable(); });

        // ===== Export buttons =====
        exportCSVButton = UIComponents.createAccentButton("Export CSV", new Color(14, 165, 233));
        exportExcelButton = UIComponents.createAccentButton("Export Excel", new Color(34, 197, 94));
        exportPDFButton = UIComponents.createAccentButton("Export PDF", new Color(239, 68, 68));

        exportCSVButton.addActionListener(e -> exportTable("CSV"));
        exportExcelButton.addActionListener(e -> exportTable("Excel"));
        exportPDFButton.addActionListener(e -> exportTable("PDF"));

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(exportCSVButton);
        buttonPanel.add(exportExcelButton);
        buttonPanel.add(exportPDFButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Populate existing roster
        refreshTable();
    }

    private void refreshTable() {
        AppLogger.info("Refreshing roster table");
        tableModel.setRowCount(0);
        manager.loadSessionRoster(session);
        for (SessionStudent ss : session.getStudentRoster()) {
            Student s = ss.getStudent();
            tableModel.addRow(new Object[]{
                    s.getStudentId(),
                    s.getName(),
                    s.getPhone() != null ? s.getPhone() : "N/A",
                    ss.getStatus(),
                    ss.getNotes()
            });
        }
    }

    private void openSession() {
        JLabel confirmationJLabel = new JLabel("Do you want to open the session?");
        int choice = JOptionPane.showConfirmDialog(this, confirmationJLabel, "Open Session", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            if (session.isActive()) {
                JOptionPane.showMessageDialog(this, "Session is already open.");
                dispose();
                return;
            }
            if (session.getStudentRoster().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cannot open session with an empty student roster.");
                return;
            }
            
            // Load session roster from database
            manager.loadSessionRoster(session);
            
            // Logic to open the session
            manager.openSession(session);
            
            // Open attendance marking window
            SwingUtilities.invokeLater(() -> {
                gui.attendance.SessionAttendanceWindow attendanceWindow = 
                    new gui.attendance.SessionAttendanceWindow(session);
                attendanceWindow.setVisible(true);
            });
            
            JOptionPane.showMessageDialog(this, "Session opened successfully. Attendance window opened.");
            dispose();
        } else {
            dispose();
        }
    }

    private void closeSession() {
        JLabel confirmationJLabel = new JLabel("Do you want to close the session?");
        int choice = JOptionPane.showConfirmDialog(this, confirmationJLabel, "Close Session", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            if (!session.isActive()) {
                JOptionPane.showMessageDialog(this, "Session is already closed.");
                dispose();
                return;
            }
            // Logic to close the session
            manager.closeSession(session);
            JOptionPane.showMessageDialog(this, "Session closed successfully.");
            dispose();
        } else {
            dispose();
        }
    }

    private void editSessionDetails() {
        SessionForm sessionForm = new SessionForm(this, manager, new RosterManager(), session);
        sessionForm.setVisible(true);
        refreshSessionDetails();
        refreshTable();
    }

    private void refreshSessionDetails() {
        nameLabel.setText(session.getName());
        dateLabel.setText(session.getDate().toString());
        timeLabel.setText(session.getStartTime() + " - " + session.getEndTime());
        locationLabel.setText(session.getLocation());
        revalidate();
        repaint();
    }

    // Open a GUI dialog 'AddStudentDialog' to add students from a table of existing students.
    private void addStudent() {
        new AddStudentDialog(parent, manager, session, allStudents).setVisible(true);
    }

    private void removeStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow >= 0) {
            String studentId = tableModel.getValueAt(selectedRow, 0).toString();
            Student selected = session.getStudentRoster().stream()
                    .map(SessionStudent::getStudent)
                    .filter(s -> s.getStudentId().equals(studentId))
                    .findFirst()
                    .orElse(null);
            if (selected != null) {
                // Send data to SessionManager to remove student from session
                boolean removed = manager.removeStudentFromSession(session, selected);
                if (removed) {
                    tableModel.removeRow(selectedRow);
                    JOptionPane.showMessageDialog(this,
                            "Student " + selected.getName() + " removed from the session successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to remove student from the session (database error).",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please click on a student (row) to remove.", "No student selected", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ===== Export Functionality =====
    private void exportTable(String type) {
        String[] headers = {"Student ID", "Name", "Phone No", "Status", "Remarks"};
        // Checkbox selection dialog
        JCheckBox[] checkboxes = new JCheckBox[headers.length];
        JPanel panel = new JPanel(new GridLayout(headers.length, 1));
        for (int i = 0; i < headers.length; i++) {
            checkboxes[i] = new JCheckBox(headers[i], true);
            panel.add(checkboxes[i]);
        }
        int result = JOptionPane.showConfirmDialog(this, panel, "Select Columns to Export", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        ArrayList<String> selectedHeaders = new ArrayList<>();
        ArrayList<ArrayList<String>> data = new ArrayList<>();
        for (int i = 0; i < headers.length; i++) {
            if (checkboxes[i].isSelected()) {
                selectedHeaders.add(headers[i]);
            }
        }

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            ArrayList<String> rowData = new ArrayList<>();
            for (int col = 0; col < headers.length; col++) {
                if (checkboxes[col].isSelected()) {
                    rowData.add(tableModel.getValueAt(row, col).toString());
                }
            }
            data.add(rowData);
        }

        // Generate the export filename based on session details
        String sessionId = String.valueOf(session.getSessionId());
        String sessionName = session.getName();  // Session name
        String sessionDate = session.getDate().toString();  // Session date
        String sessionTime = session.getStartTime() + "-" + session.getEndTime();  // Session time

        String exportTitle = "Session_" + sessionId + "_" + sessionName + "_" + sessionDate;

        try {
            switch (type) {
                case "CSV":
                    CSVGenerator csvGen = new CSVGenerator(selectedHeaders, data, exportTitle);
                    boolean csvSuccess = csvGen.generate();
                    JOptionPane.showMessageDialog(SessionRosterManagement.this,
                            csvSuccess ? "CSV report generated successfully!"
                                    : "CSV export cancelled or failed.",
                            csvSuccess ? "Export Complete" : "Export Cancelled",
                            csvSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;
                case "Excel":
                    ExcelGenerator excelGen = new ExcelGenerator(selectedHeaders, data, exportTitle);
                    boolean excelSuccess = excelGen.generate();
                    JOptionPane.showMessageDialog(SessionRosterManagement.this,
                            excelSuccess ? "Excel report generated successfully!"
                                    : "Excel export cancelled or failed.",
                            excelSuccess ? "Export Complete" : "Export Cancelled",
                            excelSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;
                case "PDF":
                    PDFGenerator pdfGen = new PDFGenerator(selectedHeaders, data, exportTitle);
                    boolean pdfSuccess = pdfGen.generate();
                    JOptionPane.showMessageDialog(SessionRosterManagement.this,
                            pdfSuccess ? "PDF report generated successfully!"
                                    : "PDF export cancelled or failed.",
                            pdfSuccess ? "Export Complete" : "Export Cancelled",
                            pdfSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to export " + type + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
