package gui.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import gui.StatusCard;
import gui.StatusMonitor;
import ConfigurationAndLogging.*;
import javax.swing.SwingUtilities;
import java.awt.Color;

/**
 * Welcome/placeholder view used in the Settings center.
 * Based on the WelcomeViewTemp design but self-contained and safe to add as a JPanel.
 */
public class WelcomeView extends JPanel {
    private final StatusCard systemCard;
    private final StatusCard cameraCard;
    private final StatusCard dbCard;
    private final StatusMonitor monitor = new StatusMonitor();

    public WelcomeView() {
        setLayout(new GridBagLayout());
        setBackground(new Color(248, 250, 252));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Welcome label
        JLabel welcomeLabel = new JLabel("Settings & Configuration", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        welcomeLabel.setForeground(new Color(30, 41, 59));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Text area
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        textArea.setForeground(new Color(71, 85, 105));
        textArea.setText(
            "Welcome to the Settings & Configuration section.\n\n" +
            "Configure system preferences, camera settings, and application behavior.\n\n" +
            "Select a category from the sidebar to access specific settings."
        );
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40);
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30));

        // System status panel
        JPanel statusPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setOpaque(false);
        // TODO: Add a check whether the system is online, the camera status is connected and the Database status is active
        
        systemCard = new StatusCard("System Status", "• Ready");
        cameraCard = new StatusCard("Camera Status", "• Unknown");
        dbCard = new StatusCard("Database Status", "• Unknown");

        statusPanel.add(systemCard);
        statusPanel.add(cameraCard);
        statusPanel.add(dbCard);

        monitor.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case "camera": {
                    boolean ok = Boolean.TRUE.equals(evt.getNewValue());
                    cameraCard.setValue(ok ? "🟢 Connected" : "🔴 Not available", ok ? new Color(34,197,94) : Color.RED);
                    break;
                }
                case "database": {
                    boolean ok = Boolean.TRUE.equals(evt.getNewValue());
                    dbCard.setValue(ok ? "🟢 Active" : "🔴 Unreachable", ok ? new Color(34,197,94) : Color.RED);
                    break;
                }
            }
        });

        // trigger checks off-EDT; if AppConfig doesn't provide getters use defaults
        try {
            AppConfig cfg = AppConfig.getInstance(); 
            monitor.checkCamera(cfg.getCameraIndex()); 
            monitor.checkDatabase(cfg.getDatabaseURL(), cfg.getDatabaseUser(), cfg.getDatabasePassword());
            AppLogger.info("database check: Active!");
        } catch (Throwable t) {
            // fallback: simple camera check index 0
            monitor.checkCamera(0);
            AppLogger.warn("database check: Not Active!");
        }
    }

}
