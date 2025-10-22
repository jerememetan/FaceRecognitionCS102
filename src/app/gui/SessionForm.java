package app.gui;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import app.test.SessionManager;
import app.entity.Session;
public class SessionForm extends JFrame {
    private JTextField nameField;
    private JTextField dateField;      // Format: DD-MM-YYYY
    private JTextField startField;     // Format: HH:MM
    private JTextField endField;       // Format: HH:MM
    private JTextField locationField;
    private JButton submitButton;
    private JButton cancelButton;

    private SessionManager manager;  // facade

    public SessionForm(SessionManager manager) {
        this.manager = manager;

        setTitle("Create a Session");
        setSize(400, 300);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel formPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setLocationRelativeTo(null);

        // Labels and input fields
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

        // Submit button
        submitButton = new JButton("Create Session");
        formPanel.add(submitButton);

        // Cancel button
        cancelButton = new JButton("Cancel");
        formPanel.add(cancelButton);
        cancelButton.addActionListener(e -> dispose());

        add(formPanel);

        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String name = nameField.getText().trim();
                    String dateStr = dateField.getText().trim();
                    String startStr = startField.getText().trim();
                    String endStr = endField.getText().trim();
                    String location = locationField.getText().trim();

                    // Validation
                    if (name.isEmpty() || dateStr.isEmpty() || startStr.isEmpty() || endStr.isEmpty() || location.isEmpty()) {
                        throw new IllegalArgumentException("All fields must be filled in.");
                    }

                    // Parse with strict format
                    LocalDate date;
                    LocalTime start, end;
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        date = LocalDate.parse(dateStr, formatter);
                    } catch (DateTimeParseException ex) {
                        throw new IllegalArgumentException("Date must be in format DD-MM-YYYY.");
                    }

                    try {
                        start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
                    } catch (DateTimeParseException ex) {
                        throw new IllegalArgumentException("Start Time must be in format HH:MM (24-hour).");
                    }

                    try {
                        end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));
                    } catch (DateTimeParseException ex) {
                        throw new IllegalArgumentException("End Time must be in format HH:MM (24-hour).");
                    }

                    if (date.isBefore(LocalDate.now())) {
                        throw new IllegalArgumentException("Date cannot be in the past.");
                    }

                    if (!end.isAfter(start)) {
                        throw new IllegalArgumentException("End Time must be after Start Time.");
                    }

                    // Delegate creation to SessionManager
                    Session session = manager.createSession(name, date, start, end, location);

                    JOptionPane.showMessageDialog(SessionForm.this,
                            "Session created successfully!\n\n" +
                            "ID: " + session.getSessionId() + "\n" +
                            "Name: " + session.getName() + "\n" +
                            "Date: " + session.getDate() + "\n" +
                            "Start: " + session.getStartTime() + "\n" +
                            "End: " + session.getEndTime() + "\n" +
                            "Location: " + session.getLocation() + "\n" +
                            "Students: " + session.getStudentRoster().size());
                    dispose();  // close form
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SessionForm.this,
                            "Error: " + ex.getMessage(),
                            "Validation Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        setVisible(true);
    }

    public static void main(String[] args) {
        SessionManager manager = new SessionManager();  // shared facade
        new SessionForm(manager);
    }
}
