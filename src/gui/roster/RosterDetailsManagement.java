package gui.roster;

import entity.Roster;
import entity.Student;
import entity.RosterStudent;
import gui.student.AddStudentDialog;
import repository.StudentRepositoryInstance;
import service.roster.RosterManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

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
        editButton = new JButton("Edit Roster Details");
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
        addButton = new JButton("Add Student");
        removeButton = new JButton("Remove Student");

        addButton.addActionListener(e -> {
            addStudent();
            refreshTable();
        });
        removeButton.addActionListener(e -> {
            removeStudent();
            refreshTable();
        });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
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
}
