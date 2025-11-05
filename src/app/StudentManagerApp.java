package app;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import config.AppLogger;
import gui.student.StudentEnrollmentGUI;
/**
 * Entry point for the Student Management system.
 * Launches the Student Enrollment gui.
 */
public class StudentManagerApp {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            try {
                AppLogger.info("Starting Student Enrollment System...");
                StudentEnrollmentGUI gui = new StudentEnrollmentGUI();
                gui.setVisible(true);
                AppLogger.info("Student Enrollment GUI launched successfully");
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






