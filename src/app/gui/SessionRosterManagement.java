package app.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import app.entity.Session;
import app.entity.SessionStudent;
import app.entity.Student;
import app.test.SessionManager;

public class SessionRosterManagement extends JFrame {


    private SessionManager manager;
    private Session session;
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JButton addButton, removeButton, openButton;
    private int studentId = 1; // TODO: Adding existing students, this should be replaced

    public SessionRosterManagement(SessionManager manager, Session session) {
        this.manager = manager;
        this.session = session;
        
        // GUI setup
        setTitle("Session Roster Management - " + session.getName());
        setSize(600, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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
        infoPanel.add(new JLabel(session.getName()));
        infoPanel.add(new JLabel("Date:"));
        infoPanel.add(new JLabel(session.getDate().toString()));
        infoPanel.add(new JLabel("Time:"));
        infoPanel.add(new JLabel(session.getStartTime() + " - " + session.getEndTime()));
        infoPanel.add(new JLabel("Location:"));
        infoPanel.add(new JLabel(session.getLocation()));
        
        // Open a session
        openButton = new JButton("Open Session");
        openButton.setFocusPainted(false);
        openButton.addActionListener(e -> { openSession(); });
        infoPanel.add(openButton);
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
        studentTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 15));
        studentTable.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Student Roster"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        //Buttons Panel
        JPanel buttonPanel = new JPanel();
        addButton = new JButton("Add Student");
        removeButton = new JButton("Remove Student");

        addButton.addActionListener(e -> addStudent());
        removeButton.addActionListener(e -> removeStudent());

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Populate existing roster
        refreshTable();

        setVisible(true);
    }


    private void refreshTable() {
        tableModel.setRowCount(0);
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
            manager.openSession(Integer.parseInt(session.getSessionId()));
            JOptionPane.showMessageDialog(this, "Session opened successfully.");
            dispose();
        }
        else{
            dispose();
        }
    }


    private void addStudent() {

        String name = JOptionPane.showInputDialog(this, "Enter student name:");
        if (name == null || name.trim().isEmpty()) return;

        String phone = JOptionPane.showInputDialog(this, "Enter phone number (optional):");
        if (phone == null) phone = "N/A";

        // Create new student
        Student newStudent = new Student(Integer.toString(studentId++), name.trim());
        newStudent.setPhone(phone.trim().isEmpty() ? null : phone.trim());
        SessionStudent sessionStudent = new SessionStudent(newStudent);
        session.getStudentRoster().add(sessionStudent);
        tableModel.addRow(new Object[]{newStudent.getStudentId(), newStudent.getName(), newStudent.getPhone(), sessionStudent.getStatus(), sessionStudent.getNotes()});
    }

    private void removeStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow >= 0) {
            String studentId = tableModel.getValueAt(selectedRow, 0).toString();
            session.getStudentRoster().removeIf(s -> s.getStudent().getStudentId().equals(studentId));
            tableModel.removeRow(selectedRow);
        } else {
            JOptionPane.showMessageDialog(this, "Please click on a student (row) to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
}
