package gui.student;

import entity.Student;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import service.student.StudentManager;

public class StudentDialog extends JDialog {
    private StudentManager studentManager;
    private Student student;
    private boolean confirmed = false;
    
    private JTextField studentIdField;
    private JTextField nameField;
    private JTextField emailField;
    private JTextField phoneField;
    
    private JButton saveButton;
    private JButton cancelButton;
    
    public StudentDialog(Frame parent, String title, Student student, StudentManager studentManager) {
        super(parent, title, true); 
        this.student = student;
        this.studentManager = studentManager;
        
        initializeDialog();
        populateFields();
    }
    
    private void initializeDialog() {
        setLayout(new BorderLayout());
        setSize(400, 250);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        add(createFormPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Student ID *:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        studentIdField = new JTextField(15);
        panel.add(studentIdField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST; 
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Name *:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        nameField = new JTextField(15);
        panel.add(nameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST; 
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Email:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        emailField = new JTextField(15);
        panel.add(emailField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST; 
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Phone:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        phoneField = new JTextField(15);
        panel.add(phoneField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        JLabel noteLabel = new JLabel("* Required fields");
        noteLabel.setFont(noteLabel.getFont().deriveFont(Font.ITALIC));
        noteLabel.setForeground(Color.GRAY);
        panel.add(noteLabel, gbc);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        saveButton = new JButton((student == null || student.getStudentId() == null || student.getStudentId().trim().isEmpty())
                                ? "Add Student" : "Save Changes");
        cancelButton = new JButton("Cancel");

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveStudent();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        panel.add(saveButton);
        panel.add(cancelButton);

        getRootPane().setDefaultButton(saveButton);

        return panel;
    }

    private void populateFields() {
        if (student != null && student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {
            // Editing existing student - make ID uneditable
            studentIdField.setText(student.getStudentId());
            studentIdField.setEditable(false);
            nameField.setText(student.getName());
            emailField.setText(student.getEmail() != null ? student.getEmail() : "");
            phoneField.setText(student.getPhone() != null ? student.getPhone() : "");
        } else {
            // Adding new student - make ID editable and clear all fields
            studentIdField.setText("");
            studentIdField.setEditable(true);
            nameField.setText("");
            emailField.setText("");
            phoneField.setText("");
        }
    }
    
    private void saveStudent() {
        try {
            if (!validateInput()) {
                return;
            }

            String studentId = studentIdField.getText().trim();
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();

            if (student == null || student.getStudentId() == null || student.getStudentId().trim().isEmpty()) {
                // Adding new student
                Student newStudent;
                if (!email.isEmpty() || !phone.isEmpty()) {
                    newStudent = new Student(studentId, name,
                                           email.isEmpty() ? null : email,
                                           phone.isEmpty() ? null : phone);
                } else {
                    newStudent = new Student(studentId, name);
                }

                if (studentManager.enrollStudent(newStudent)) {
                    JOptionPane.showMessageDialog(this, "Student added successfully!");
                    confirmed = true;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to add student. Please check if Student ID already exists.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            } else {
                // Updating existing student
                student.setName(name);

                student.setEmail(email.isEmpty() ? null : email);
                student.setPhone(phone.isEmpty() ? null : phone);

                if (studentManager.updateStudent(student)) {
                    JOptionPane.showMessageDialog(this, "Student updated successfully!");
                    confirmed = true;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to update student.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private boolean validateInput() {
        if (studentIdField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Student ID is required.", 
                "Validation Error", JOptionPane.WARNING_MESSAGE);
            studentIdField.requestFocus();
            return false;
        }
        
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", 
                "Validation Error", JOptionPane.WARNING_MESSAGE);
            nameField.requestFocus();
            return false;
        }
        
        String studentId = studentIdField.getText().trim();
        if (!studentId.matches("^S\\d{5}$")) {
            JOptionPane.showMessageDialog(this, 
                "Student ID must follow format S12345 (S followed by 5 digits).", 
                "Validation Error", 
                JOptionPane.WARNING_MESSAGE);
            studentIdField.requestFocus();
            return false;
        }
        
        String email = emailField.getText().trim();
        if (!email.isEmpty() && !isValidEmail(email)) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a valid email address.", 
                "Validation Error", 
                JOptionPane.WARNING_MESSAGE);
            emailField.requestFocus();
            return false;
        }
        
        String phone = phoneField.getText().trim();
        if (!phone.isEmpty() && !isValidPhone(phone)) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a valid phone number (digits only, 8-15 characters).", 
                "Validation Error", 
                JOptionPane.WARNING_MESSAGE);
            phoneField.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }
    
    private boolean isValidPhone(String phone) {
        return phone.matches("^\\d{8,15}$");
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
}






