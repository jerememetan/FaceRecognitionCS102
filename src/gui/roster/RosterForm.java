package gui.roster;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import service.roster.RosterManager;
import entity.Roster;

//GUI class to create/edit a Roster entity
public class RosterForm extends JDialog {
    private JTextField courseCodeField;
    private JTextField startTimeField;
    private JTextField endTimeField;
    private JTextField locationField;
    private JButton submitButton;
    private JButton cancelButton;

    private RosterManager manager;
    private Roster editingRoster;  // optional, if editing existing one

    public RosterForm(Window parent, RosterManager manager) {
        super(parent, "Roster Form", ModalityType.APPLICATION_MODAL);
        this.manager = manager;
        setSize(400, 250);
        setLocationRelativeTo(parent);

        JPanel formPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        formPanel.add(new JLabel("Course Code:"));
        courseCodeField = new JTextField();
        formPanel.add(courseCodeField);

        formPanel.add(new JLabel("Start Time (HH:mm):"));
        startTimeField = new JTextField();
        formPanel.add(startTimeField);

        formPanel.add(new JLabel("End Time (HH:mm):"));
        endTimeField = new JTextField();
        formPanel.add(endTimeField);

        formPanel.add(new JLabel("Location:"));
        locationField = new JTextField();
        formPanel.add(locationField);

        submitButton = new JButton("Save Roster");
        cancelButton = new JButton("Cancel");
        formPanel.add(submitButton);
        formPanel.add(cancelButton);

        add(formPanel);

        cancelButton.addActionListener(e -> dispose());

        submitButton.addActionListener(e -> {
            try {
                String courseCode = courseCodeField.getText().trim();
                String startStr = startTimeField.getText().trim();
                String endStr = endTimeField.getText().trim();
                String location = locationField.getText().trim();

                if (courseCode.isEmpty() || startStr.isEmpty() || endStr.isEmpty() || location.isEmpty()) {
                    throw new IllegalArgumentException("All fields must be filled in.");
                }

                LocalTime start = parseTime(startStr, "Start Time");
                LocalTime end = parseTime(endStr, "End Time");

                if (!end.isAfter(start)) {
                    throw new IllegalArgumentException("End time must be after start time.");
                }

                if (editingRoster != null) {
                    editingRoster.setCourseCode(courseCode);
                    editingRoster.setStartTime(start);
                    editingRoster.setEndTime(end);
                    editingRoster.setLocation(location);
                    manager.updateRoster(editingRoster);
                    JOptionPane.showMessageDialog(this, "Roster updated successfully!");
                } else {
                    Roster newRoster = manager.createNewRoster(courseCode, start, end, location);
                    JOptionPane.showMessageDialog(this, "Roster created successfully!\nCourse: " + newRoster.getCourseCode());
                }

                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private LocalTime parseTime(String timeStr, String label) {
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " must be in format HH:mm (24-hour).");
        }
    }

    public void populateForm(Roster roster) {
        this.editingRoster = roster;
        courseCodeField.setText(roster.getCourseCode());
        startTimeField.setText(roster.getStartTime().toString());
        endTimeField.setText(roster.getEndTime().toString());
        locationField.setText(roster.getLocation());
    }
}
