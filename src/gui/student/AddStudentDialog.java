package gui.student;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import entity.Student;
import entity.Session;
import entity.SessionStudent;
import entity.Roster;
import service.session.SessionManager;
import service.roster.RosterManager;
import gui.homepage.UIComponents;
import util.ColourTheme;

//GUI class to handle addition of students from both Roster and Session entities
public class AddStudentDialog extends JDialog {

    private SessionManager sessionManager;
    private RosterManager rosterManager;
    private Session session;
    private Roster roster;
    private ArrayList<Student> allStudents;
    private JTable studentTable;
    private DefaultTableModel tableModel;

    // === Constructor for Session ===
    public AddStudentDialog(JFrame parent, SessionManager manager, Session session, ArrayList<Student> allStudents) {
        super(parent, "Add Student", ModalityType.APPLICATION_MODAL);
        this.sessionManager = manager;
        this.session = session;
        this.allStudents = allStudents;
        initDialogUI(parent, false); // false = single select
        populateSessionTable();
    }

    // === Constructor for Roster (multi-select mode) ===
    public AddStudentDialog(JFrame parent, RosterManager manager, Roster roster, ArrayList<Student> allStudents) {
        super(parent, "Add Students to Roster", ModalityType.APPLICATION_MODAL);
        this.rosterManager = manager;
        this.roster = roster;
        this.allStudents = allStudents;
        initDialogUI(parent, true); // true = multi-select
        populateRosterTable();
    }

    // === Shared GUI builder ===
    private void initDialogUI(JFrame parent, boolean multiSelect) {
        setLayout(new BorderLayout(10, 10));
        setSize(700, 500);
        setLocationRelativeTo(parent);

        JPanel paddingPanel = new JPanel(new BorderLayout(10, 10));
        paddingPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        add(paddingPanel, BorderLayout.CENTER);

        // Search bar
        JTextField searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            searchField.getBorder(),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JLabel("Search for student by name:"), BorderLayout.NORTH);
        inputPanel.add(searchField, BorderLayout.CENTER);
        paddingPanel.add(inputPanel, BorderLayout.NORTH);

        // Table
        String[] columnNames = {"Student ID", "Name", "Phone"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        studentTable = new JTable(tableModel);
        studentTable.setRowHeight(28);
        studentTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        studentTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 15));
        studentTable.setSelectionMode(
            multiSelect ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                        : ListSelectionModel.SINGLE_SELECTION
        );

        JScrollPane scrollPane = new JScrollPane(studentTable);
        paddingPanel.add(scrollPane, BorderLayout.CENTER);

        // Search functionality
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                String text = searchField.getText().trim().toLowerCase();
                filterStudents(text);
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        // Button
    JButton addButton = UIComponents.createAccentButton(multiSelect ? "Add Selected Students" : "Add Student", ColourTheme.SUCCESS_COLOR);
    addButton.addActionListener(e -> onAddStudent(multiSelect));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(addButton);
        paddingPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    // === Populate Session-based table ===
    private void populateSessionTable() {
        ArrayList<String> existing = new ArrayList<>();
        for (SessionStudent ss : session.getStudentRoster())
            existing.add(ss.getStudent().getStudentId());

        tableModel.setRowCount(0);
        for (Student s : allStudents)
            if (!existing.contains(s.getStudentId()))
                tableModel.addRow(new Object[]{s.getStudentId(), s.getName(), s.getPhone()});
    }

    // === Populate Roster-based table ===
    private void populateRosterTable() {
        ArrayList<String> existing = new ArrayList<>();
        roster.getStudents().forEach(s -> existing.add(s.getStudent().getStudentId()));

        tableModel.setRowCount(0);
        for (Student s : allStudents)
            if (!existing.contains(s.getStudentId()))
                tableModel.addRow(new Object[]{s.getStudentId(), s.getName(), s.getPhone()});
    }

    // === Search filter ===
    private void filterStudents(String text) {
        tableModel.setRowCount(0);
        for (Student s : allStudents)
            if (s.getName().toLowerCase().contains(text))
                tableModel.addRow(new Object[]{s.getStudentId(), s.getName(), s.getPhone()});
    }

    // === Add Student(s) handler ===
    private void onAddStudent(boolean multiSelect) {
        int[] selectedRows = studentTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one student.");
            return;
        }

        List<Student> selectedStudents = new ArrayList<>();
        for (int row : selectedRows) {
            String id = (String) tableModel.getValueAt(row, 0);
            Student s = allStudents.stream()
                .filter(st -> st.getStudentId().equals(id))
                .findFirst()
                .orElse(null);
            if (s != null) selectedStudents.add(s);
        }

        try {
            int addedCount = 0;
            for (Student s : selectedStudents) {
                boolean added = (session != null)
                    ? sessionManager.addStudentToSession(session, s)
                    : rosterManager.addStudentToRoster(roster, s);
                if (added) addedCount++;
            }

            JOptionPane.showMessageDialog(this,
                "Successfully added " + addedCount + " student(s).",
                "Success", JOptionPane.INFORMATION_MESSAGE
            );
            dispose();

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Unexpected error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
