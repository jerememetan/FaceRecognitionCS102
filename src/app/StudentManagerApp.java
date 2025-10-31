package app;

import entity.Student;
import gui.student.StudentEnrollmentGUI;
import javax.swing.*;

/**
 * Entry point for the Student Management system.
 * Launches the Student Enrollment gui.
 */
public class StudentManagerApp {

    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set look and feel: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Starting Student Enrollment System...");
                StudentEnrollmentGUI gui = new StudentEnrollmentGUI();
                gui.setVisible(true);
                System.out.println("Student Enrollment GUI launched successfully");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Failed to start Student Manager: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}






