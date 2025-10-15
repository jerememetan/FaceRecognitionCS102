package app.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import app.entity.Session;
import app.entity.Student;
import app.test.SessionManager;

public class SessionRosterManagement extends JFrame {

    private SessionManager manager;
    private Session session;
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JButton addButton, removeButton;
    private int studentId = 1; // TODO: Upon the completion of adding existing students, this should be replaced
    public SessionRosterManagement(SessionManager manager, Session session) {
        this.manager = manager;
        this.session = session;

        setTitle("Session Roster Management - " + session.getName());
        setSize(600, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel);

        //Session Info Panel
        JPanel infoPanel = new JPanel(new GridLayout(5, 2, 5, 5));
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
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        //Student Roster Table
        String[] columns = {"Student ID", "Name", "Phone Number"};
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
        for (Student s : session.getStudentRoster()) {
            tableModel.addRow(new Object[]{
                    s.getStudentId(),
                    s.getName(),
                    s.getPhone() != null ? s.getPhone() : "N/A"
                    
            });
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
        session.getStudentRoster().add(newStudent);
        tableModel.addRow(new Object[]{newStudent.getStudentId(), newStudent.getName(), newStudent.getPhone()});
    }

    private void removeStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow >= 0) {
            String studentId = tableModel.getValueAt(selectedRow, 0).toString();
            session.getStudentRoster().removeIf(s -> s.getStudentId().equals(studentId));
            tableModel.removeRow(selectedRow);
        } else {
            JOptionPane.showMessageDialog(this, "Please select a student to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
}
