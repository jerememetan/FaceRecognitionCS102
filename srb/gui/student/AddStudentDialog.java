package gui.student;

import entity.Session;
import entity.SessionStudent;
import entity.Student;
import gui.session.SessionRosterManagement;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import service.session.SessionManager;

//GUI class used in SessionRosterManagement to add students to a session
public class AddStudentDialog extends JDialog {

    private SessionManager manager;
    private Session session;
    private ArrayList<Student> allStudents;
    private JTable studentTable;
    private DefaultTableModel tableModel;

    public AddStudentDialog(JFrame parent, SessionManager manager, Session session, ArrayList<Student> allStudents) {
        super(parent, "Add Student", ModalityType.APPLICATION_MODAL);
        this.manager = manager;
        this.session = session;
        this.allStudents = allStudents;

        setLayout(new BorderLayout(10, 10));
        setSize(700, 500);
        setLocationRelativeTo(parent);

        JPanel paddingPanel = new JPanel(new BorderLayout(10, 10));
        paddingPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        add(paddingPanel, BorderLayout.CENTER);

        // Search bar
        JTextField searchField = new JTextField();
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            searchField.getBorder(), 
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        inputPanel.add(new JLabel("Search for student by name:"), BorderLayout.NORTH);
        inputPanel.add(searchField, BorderLayout.CENTER);
        paddingPanel.add(inputPanel, BorderLayout.NORTH);

        // Table
        String[] columnNames = {"Student ID", "Name", "Phone"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        studentTable = new JTable(tableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(studentTable);
        paddingPanel.add(scrollPane, BorderLayout.CENTER);

        // Populate Student Table
        ArrayList<String> rosterStudents = new ArrayList<>();   // to exclude students already in the session roster
        for (SessionStudent ss : session.getStudentRoster()) {
            rosterStudents.add(ss.getStudent().getStudentId());
        }
        for (Student s : allStudents) {
            if (!rosterStudents.contains(s.getStudentId())) {   //check if student is not already in roster
                tableModel.addRow(new Object[]{s.getStudentId(), s.getName(), s.getPhone()});
            }
        }

        // Live search functionality
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                String text = searchField.getText().trim().toLowerCase();
                tableModel.setRowCount(0);
                for (Student s : allStudents)
                    if (s.getName().toLowerCase().contains(text))
                        tableModel.addRow(new Object[]{s.getStudentId(), s.getName(), s.getPhone()});
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        JButton addButton = new JButton("Add Student");
        addButton.addActionListener(e -> onAddStudent());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(addButton);
        paddingPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onAddStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student.");
            return;
        }

        String id = (String) tableModel.getValueAt(selectedRow, 0);
        Student selected = allStudents.stream()
            .filter(s -> s.getStudentId().equals(id))
            .findFirst()
            .orElse(null);

        if (selected == null) return;

        boolean exists = session.getStudentRoster().stream()
            .anyMatch(s -> s.getStudent().getStudentId().equals(selected.getStudentId()));

        if (exists) {
            JOptionPane.showMessageDialog(this, "This student is already in the roster.");
            return;
        }

        try {
            boolean added = manager.addStudentToSession(session, selected);
            JOptionPane.showMessageDialog(rootPane,"added: " + added);
            if (added) {
                JOptionPane.showMessageDialog(this, "Student added successfully.");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add student (database error).", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}







