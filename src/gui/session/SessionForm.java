package gui.session;

import entity.Session;
import entity.Student;
import entity.Roster;
import entity.RosterStudent;
import service.session.SessionManager;
import service.roster.RosterManager;
import gui.homepage.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import util.*;
public class SessionForm extends JDialog {
    private JComboBox<Roster> rosterDropdown;
    private JTextField nameField;
    private JTextField dateField;
    private JTextField startField;
    private JTextField endField;
    private JTextField locationField;
    private JButton submitButton;
    private JButton cancelButton;

    private SessionManager manager;
    private RosterManager rosterManager;

    private Session editingSession; // null = creating mode, not null = editing mode

    // === CREATE MODE ===
    public SessionForm(Window parent, SessionManager manager, RosterManager rosterManager) {
        this(parent, manager, rosterManager, null);
    }

    // === EDIT MODE ===
    public SessionForm(Window parent, SessionManager manager, RosterManager rosterManager, Session sessionToEdit) {
        super(parent, "Session Form", ModalityType.APPLICATION_MODAL);
        this.manager = manager;
        this.rosterManager = rosterManager;
        this.editingSession = sessionToEdit;
        rosterManager.populateRosters();

        setTitle(sessionToEdit == null ? "Create a Session" : "Edit Session");
        setSize(420, 330);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel formPanel = new JPanel(new GridLayout(7, 2, 5, 5));
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Roster selection
        formPanel.add(new JLabel("Select Roster (optional):"));
        rosterDropdown = new JComboBox<>();
        loadRosters();
        rosterDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) setText("— No Roster —");
                else if (value instanceof Roster)
                    setText(((Roster) value).getCourseCode());
                return this;
            }
        });
        formPanel.add(rosterDropdown);

        formPanel.add(new JLabel("Name:"));
        nameField = new JTextField();
        formPanel.add(nameField);

        formPanel.add(new JLabel("Date (DD-MM-YYYY):"));
        dateField = new JTextField();
        formPanel.add(dateField);

        formPanel.add(new JLabel("Start Time (HH:MM):"));
        startField = new JTextField();
        formPanel.add(startField);

        formPanel.add(new JLabel("End Time (HH:MM):"));
        endField = new JTextField();
        formPanel.add(endField);

        formPanel.add(new JLabel("Location:"));
        locationField = new JTextField();
        formPanel.add(locationField);

        submitButton = UIComponents.createAccentButton(sessionToEdit == null ? "Create Session" : "Save Changes", ColourTheme.PRIMARY_COLOR);
        cancelButton = UIComponents.createAccentButton("Cancel",  ColourTheme.DANGER);
        formPanel.add(submitButton);
        formPanel.add(cancelButton);

        add(formPanel);
        cancelButton.addActionListener(e -> dispose());

        rosterDropdown.addActionListener(e -> {
            Roster selected = (Roster) rosterDropdown.getSelectedItem();
            if (selected != null) populateFromRoster(selected);
            else clearForm();
        });

        submitButton.addActionListener(e -> handleSubmit());
        if (editingSession != null) populateForm(editingSession);

    }

    // === UTILITIES ===
    private void clearForm() {
        nameField.setText("");
        dateField.setText("");
        startField.setText("");
        endField.setText("");
        locationField.setText("");
    }
    //For JComboBox options
    private void loadRosters() {
        rosterDropdown.addItem(null);
        for (Roster roster : rosterManager.getAllRosters()) {
            rosterDropdown.addItem(roster);
        }
    }
    //Auto Fill details from Roster into input fields
    private void populateFromRoster(Roster roster) {
        if (roster == null) return;
        nameField.setText(roster.getCourseCode());
        startField.setText(roster.getStartTime().toString());
        endField.setText(roster.getEndTime().toString());
        locationField.setText(roster.getLocation());
    }
    // Editing mode -> AutoFill Session details
    private void populateForm(Session session) {
        nameField.setText(session.getName());
        dateField.setText(session.getDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        startField.setText(session.getStartTime().toString());
        endField.setText(session.getEndTime().toString());
        locationField.setText(session.getLocation());
    }

    private void handleSubmit() {
        try {
            String name = nameField.getText().trim();
            String dateStr = dateField.getText().trim();
            String startStr = startField.getText().trim();
            String endStr = endField.getText().trim();
            String location = locationField.getText().trim();

            if (name.isEmpty() || dateStr.isEmpty() || startStr.isEmpty() || endStr.isEmpty() || location.isEmpty())
                throw new IllegalArgumentException("All fields must be filled in.");

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate date = LocalDate.parse(dateStr, dateFmt);
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);

            if (!end.isAfter(start)) throw new IllegalArgumentException("End time must be after start time.");

            if (editingSession == null) {
                // === CREATE MODE ===
                Session session = manager.createNewSession(name, date, start, end, location);

                Roster selectedRoster = (Roster) rosterDropdown.getSelectedItem();
                if (selectedRoster != null) {
                    rosterManager.loadRosterStudent(selectedRoster);
                    for (RosterStudent rs : selectedRoster.getStudents()) {
                        Student s = rs.getStudent();
                        boolean success = manager.addStudentToSession(session, s);
                        System.out.println("Added " + s.getName() + ": " + success);
                    }
                }

                JOptionPane.showMessageDialog(this, "Session created successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);

            } else {
                // === EDIT MODE ===
                boolean success = manager.updateSession(editingSession);
                if (!success) throw new IllegalStateException("Failed to update session in database.");
                editingSession.setName(name);
                editingSession.setDate(date);
                editingSession.setStartTime(start);
                editingSession.setEndTime(end);
                editingSession.setLocation(location);
                Roster selectedRoster = (Roster) rosterDropdown.getSelectedItem();
                if (selectedRoster != null) {
                    rosterManager.loadRosterStudent(selectedRoster);
                    for (RosterStudent rs : selectedRoster.getStudents()) {
                        Student s = rs.getStudent();
                        success = manager.addStudentToSession(editingSession, s);
                        System.out.println("Added " + s.getName() + ": " + success);
                    }
                }
                JOptionPane.showMessageDialog(this, "Session updated successfully!", "Updated",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            dispose();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
