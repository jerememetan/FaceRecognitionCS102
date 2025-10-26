package gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class SettingsGUI extends JFrame {
    private String role;
    private String id;

    public SettingsGUI(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Settings & Configuration - " + role + " (" + id + ")");
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

        // Left Panel - Simple settings categories
        JPanel leftPanel = createCategoriesPanel();
        mainPanel.add(leftPanel, BorderLayout.WEST);

        // Center Panel - Simple settings content
        JPanel centerPanel = createSettingsContent();
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(45, 55, 72));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Title
        JLabel titleLabel = new JLabel("Settings & Configuration");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        // Back Button
        JButton backButton = createStyledButton("← Back to Dashboard", new Color(239, 68, 68));
        backButton.addActionListener(e -> dispose());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(backButton, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createCategoriesPanel() {
        JPanel categoriesPanel = new JPanel();
        categoriesPanel.setLayout(new BoxLayout(categoriesPanel, BoxLayout.Y_AXIS));
        categoriesPanel.setBackground(new Color(45, 55, 72));
        categoriesPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        categoriesPanel.setPreferredSize(new Dimension(200, 0));

        // Category Title
        JLabel categoryTitle = new JLabel("Categories");
        categoryTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        categoryTitle.setForeground(Color.WHITE);
        categoryTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        categoriesPanel.add(categoryTitle);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Category buttons
        JButton cameraBtn = createCategoryButton("Camera");
        JButton otherBtn = createCategoryButton("Some other Buttons");
        

        categoriesPanel.add(cameraBtn);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        categoriesPanel.add(otherBtn);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        

        categoriesPanel.add(Box.createVerticalGlue());

        return categoriesPanel;
    }

    private JPanel createSettingsContent() {
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
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(textPanel, gbc);

        return contentPanel;
    }

    // status card style at the buttom
    private JPanel createStatusCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setForeground(new Color(71, 85, 105));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        valueLabel.setForeground(color);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JButton createCategoryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(59, 130, 246));
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(180, 35));
        
        // hover effects
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(37, 99, 235));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(59, 130, 246));
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