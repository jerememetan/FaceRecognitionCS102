package gui.roster;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
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
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import entity.Roster;
import entity.RosterStudent;
import entity.Student;
import gui.homepage.UIComponents;
import gui.student.AddStudentDialog;
import report.CSVGenerator;
import report.ExcelGenerator;
import report.PDFGenerator;
import repository.StudentRepositoryInstance;
import service.roster.RosterManager;

//GUI class to view/modify details in a Roster entity
public class RosterDetailsManagement extends JDialog {

    private JFrame parent;
    private RosterManager manager;
    private Roster roster;
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JButton addButton, removeButton, editButton;
    private ArrayList<Student> allStudents;
    private JLabel courseCodeLabel, timeLabel, locationLabel;

    // Export buttons
    private JButton exportCSVButton, exportExcelButton, exportPDFButton;

    public RosterDetailsManagement(JFrame parent, RosterManager manager, Roster roster) {
        super(parent, "Roster Management", ModalityType.APPLICATION_MODAL);
        this.parent = parent;
        this.manager = manager;
        this.roster = roster;
        this.allStudents = new StudentRepositoryInstance().findAll();

        setTitle("Roster Management - " + roster.getCourseCode());
        setSize(700, 550);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel);

        // Roster Info Panel
        JPanel infoPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Roster Details"));
        infoPanel.add(new JLabel("Roster ID:"));
        infoPanel.add(new JLabel(String.valueOf(roster.getRosterId())));
        infoPanel.add(new JLabel("Course Code:"));
        courseCodeLabel = new JLabel(roster.getCourseCode());
        infoPanel.add(courseCodeLabel);
        infoPanel.add(new JLabel("Time"));
        timeLabel = new JLabel(roster.getStartTime().toString() + " - " + roster.getEndTime().toString());
        infoPanel.add(timeLabel);
        infoPanel.add(new JLabel("Location:"));
        locationLabel = new JLabel(roster.getLocation());
        infoPanel.add(locationLabel);

        // Edit button to modify roster details
        editButton = UIComponents.createAccentButton("Edit Roster Details", new Color(59, 130, 246));
        editButton.setFocusPainted(false);
        editButton.addActionListener(e -> editRosterDetails());
        infoPanel.add(editButton);

        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // Student Table
        String[] columns = {"Student ID", "Name", "Phone No"};
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
        TitledBorder border = BorderFactory.createTitledBorder("Roster Students");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 16));
        scrollPane.setBorder(border);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        addButton = UIComponents.createAccentButton("Add Student", new Color(59, 130, 246));
        removeButton = UIComponents.createAccentButton("Remove Student", new Color(59, 130, 246));

        addButton.addActionListener(e -> {
            addStudent();
            refreshTable();
        });
        removeButton.addActionListener(e -> {
            removeStudent();
            refreshTable();
        });

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

        // Load initial roster
        refreshTable();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        manager.loadRosterStudent(roster);
        for (RosterStudent rs : roster.getStudents()) {
            Student s = rs.getStudent();
            tableModel.addRow(new Object[]{
                    s.getStudentId(),
                    s.getName(),
                    s.getPhone() != null ? s.getPhone() : "N/A"
            });
        }
    }

    private void addStudent() {
        new AddStudentDialog(parent, manager, roster, allStudents).setVisible(true);
    }

    private void removeStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow >= 0) {
            String studentId = tableModel.getValueAt(selectedRow, 0).toString();
            Student selected = roster.getStudents().stream()
                    .map(RosterStudent::getStudent)
                    .filter(s -> s.getStudentId().equals(studentId))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                boolean removed = manager.removeStudentFromRoster(roster, selected);
                if (removed) {
                    tableModel.removeRow(selectedRow);
                    JOptionPane.showMessageDialog(this,
                            "Student " + selected.getName() + " removed from the roster successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to remove student from the roster (database error).",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please click on a student (row) to remove.", "No student selected", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void editRosterDetails() {
        RosterForm rosterForm = new RosterForm(this, manager);
        rosterForm.populateForm(roster);
        rosterForm.setVisible(true);
        refreshRosterDetails();
    }

    private void refreshRosterDetails(){
        courseCodeLabel.setText(roster.getCourseCode());
        timeLabel.setText(roster.getStartTime() + " - " + roster.getEndTime());
        locationLabel.setText(roster.getLocation());
        revalidate();
        repaint();
    }

    // ===== Export Functionality =====
    private void exportTable(String type) {
        String[] headers = {"Student ID", "Name", "Phone No"};
        
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

        // Generate the export filename based on the roster details
        String rosterId = String.valueOf(roster.getRosterId());
        String rosterName = roster.getCourseCode();  // Roster name (Course Code)
        String rosterDate = LocalDate.now().toString();  // Assuming you want the current date
        String rosterTime = roster.getStartTime().toString() + "-" + roster.getEndTime().toString(); // Time range
        String exportTitle = "Roster_" + rosterId + "_" + rosterName + "_" + rosterDate;

        try {
            switch (type) {
                case "CSV":
                    CSVGenerator csvGen = new CSVGenerator(selectedHeaders, data, exportTitle);
                    boolean csvSuccess = csvGen.generate();
                    JOptionPane.showMessageDialog(RosterDetailsManagement.this,
                            csvSuccess ? "CSV report generated successfully!"
                                    : "CSV export cancelled or failed.",
                            csvSuccess ? "Export Complete" : "Export Cancelled",
                            csvSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;
                case "Excel":
                    ExcelGenerator excelGen = new ExcelGenerator(selectedHeaders, data, exportTitle);
                    boolean excelSuccess = excelGen.generate();
                    JOptionPane.showMessageDialog(RosterDetailsManagement.this,
                            excelSuccess ? "Excel report generated successfully!"
                                    : "Excel export cancelled or failed.",
                            excelSuccess ? "Export Complete" : "Export Cancelled",
                            excelSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;
                case "PDF":
                    PDFGenerator pdfGen = new PDFGenerator(selectedHeaders, data, exportTitle);
                    boolean pdfSuccess = pdfGen.generate();
                    JOptionPane.showMessageDialog(RosterDetailsManagement.this,
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
