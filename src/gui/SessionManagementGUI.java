package gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class SessionManagementGUI extends JFrame {
    private String role;
    private String id;

    public SessionManagementGUI(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Session Management - " + role + " (" + id + ")");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setVisible(true);

        createUI();
    }

    private void createUI() {
        // Header Panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Main Content Panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(248, 250, 252));

        // Center Panel - Simple content area
        JPanel centerPanel = createContentArea();
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom Panel - Simple bottom bar with buttons
        JPanel bottomPanel = createBottomBar();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(45, 55, 72));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Title
        JLabel titleLabel = new JLabel("Attendance Sessions");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        // Back Button
        JButton backButton = createStyledButton("← Back to Dashboard", new Color(239, 68, 68));
        backButton.addActionListener(e -> dispose());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(backButton, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createContentArea() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(new Color(248, 250, 252));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(Color.WHITE);
        textPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            BorderFactory.createEmptyBorder(30, 30, 30, 30)
        ));

        // Welcome label
        JLabel welcomeLabel = new JLabel("Session Management System", SwingConstants.CENTER);
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
        Welcome to the Session Management section.

        Create and manage attendance sessions for your classes.

        Use the bottom panel to access session management tools.
        """);
        
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40); 
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30));

        // Session status panel
        JPanel statusPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        statusPanel.setBackground(Color.WHITE);

        statusPanel.add(createStatusCard("Active Sessions", "0", new Color(34, 197, 94)));
        statusPanel.add(createStatusCard("Pending Sessions", "0", new Color(251, 191, 36)));
        statusPanel.add(createStatusCard("Completed Sessions", "0", new Color(239, 68, 68)));
        statusPanel.add(createStatusCard("Total Students", "0", new Color(59, 130, 246)));

        textPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        textPanel.add(textArea);
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(statusPanel);

        // Center the text block in the main content panel
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(textPanel, gbc);

        return contentPanel;
    }

    private JPanel createBottomBar() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(45, 55, 72));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Left side - Quick actions
        JPanel quickActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        quickActionsPanel.setBackground(new Color(45, 55, 72));

        JButton newSessionBtn = createBottomButton("New Session", new Color(34, 197, 94));
        JButton quickStartBtn = createBottomButton("Quick Start", new Color(251, 191, 36));
        JButton manageRosterBtn = createBottomButton("Manage Roster", new Color(59, 130, 246));

        quickActionsPanel.add(newSessionBtn);
        quickActionsPanel.add(quickStartBtn);
        quickActionsPanel.add(manageRosterBtn);

        // Right side - Status info
        JLabel statusLabel = new JLabel("System Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusLabel.setForeground(Color.WHITE);

        bottomBar.add(quickActionsPanel, BorderLayout.WEST);
        bottomBar.add(statusLabel, BorderLayout.EAST);

        return bottomBar;
    }

    private JPanel createStatusCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(new Color(71, 85, 105));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valueLabel.setForeground(color);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JButton createBottomButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // hover effects
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(color.darker());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color);
            }
        });
        
        return button;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        return button;
    }
}