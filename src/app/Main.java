package app;

import app.gui.StudentEnrollmentGUI;

import javax.swing.UIManager;
import javax.swing.*;

public class Main {
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Starting Student Enrollment System...");
                StudentEnrollmentGUI gui = new StudentEnrollmentGUI();
                gui.setVisible(true);
                System.out.println("GUI launched successfully");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Failed to start application: " + e.getMessage(),
                    "Startup Error", 
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}