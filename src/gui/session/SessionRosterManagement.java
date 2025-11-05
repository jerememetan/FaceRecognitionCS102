package gui.session;

import entity.Session;
import entity.SessionStudent;
import entity.Student;
import gui.student.AddStudentDialog;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import repository.StudentRepositoryInstance;
import service.roster.RosterManager;
import service.session.SessionManager;

//View further details of a selected session
public class SessionRosterManagement extends JDialog {

    private JFrame parent;
    private SessionManager manager;
    private Session session;
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JButton addButton, removeButton, openButton, closeButton, editButton;
    private ArrayList<Student> allStudents; // required to add students to a session
    private JLabel nameLabel, dateLabel, timeLabel, locationLabel;

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
        // adding padding to frame
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
        
        editButton = new JButton("Edit Session Details");
        editButton.setFocusPainted(false);
        editButton.addActionListener(e -> editSessionDetails());
        infoPanel.add(editButton);

        // Open a session
        if (session.isActive()){
            closeButton = new JButton("Close Session");
            closeButton.setFocusPainted(false);
            closeButton.addActionListener(e -> { closeSession(); } );
            infoPanel.add(closeButton);

        }
        else{
            openButton = new JButton("Open Session");
            openButton.setFocusPainted(false);
            openButton.addActionListener(e -> { openSession(); });
            infoPanel.add(openButton);
        }
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        //Student Roster Table
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

        //Buttons Panel
        JPanel buttonPanel = new JPanel();
        addButton = new JButton("Add Student");
        removeButton = new JButton("Remove Student");

        addButton.addActionListener(e -> { addStudent(); refreshTable(); });
        removeButton.addActionListener(e -> { removeStudent(); refreshTable(); });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Populate existing roster
        refreshTable();

    }
    

    private void refreshTable() {
        System.out.println("Refreshing roster table");
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
            // Logic to open the session
            manager.openSession(session);
            JOptionPane.showMessageDialog(this, "Session opened successfully.");
            dispose();
        }
        else{
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
        }
        else{
            dispose();
        }
    }
    private void editSessionDetails(){
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

    //open a gui dialog 'AddStudentDialog' to add students from a table of existing students.
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
                // send data to SessionManager to remove student from session
                boolean removed = manager.removeStudentFromSession(session, selected);
                if (removed) {
                    tableModel.removeRow(selectedRow);
                    JOptionPane.showMessageDialog(this,
                        "Student " + selected.getName() + " removed from the session successfully.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                }
                else{
                    JOptionPane.showMessageDialog(this,
                        "Failed to remove student from the session (database error).",
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        else{
            JOptionPane.showMessageDialog(this, "Please click on a student (row) to remove.", "No student selected", JOptionPane.WARNING_MESSAGE);
        }
    }
}







