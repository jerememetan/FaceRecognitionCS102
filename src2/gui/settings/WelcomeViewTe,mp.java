package gui.SettingsView;

public class WelcomeView {
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
        textArea.setText("""
        Welcome to the Settings & Configuration section.

        Configure system preferences, camera settings, and application behavior.

        Select a category from the sidebar to access specific settings.
        """);
        
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40); 
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30));

        // System status panel
        JPanel statusPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        statusPanel.setBackground(Color.WHITE);

        statusPanel.add(createStatusCard("System Status", "🟢 Online", new Color(34, 197, 94)));
        statusPanel.add(createStatusCard("Camera Status", "🟢 Connected", new Color(34, 197, 94)));
        statusPanel.add(createStatusCard("Database Status", "🟢 Active", new Color(34, 197, 94)));

        textPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        textPanel.add(textArea);
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(statusPanel);

        // Center the text block in the main content panel
    
}
