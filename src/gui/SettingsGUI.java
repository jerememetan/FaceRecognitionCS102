package gui;

import gui.SettingsView.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import ConfigurationAndLogging.*;

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

        // Create the settings center (cards) and pass the same listener used by the pages
        IConfigChangeListener listener = new IConfigChangeListener() {
            @Override public void onScaleFactorChanged(double newScaleFactor) {
                AppConfig.getInstance().setDetectionScaleFactor(newScaleFactor);
                AppLogger.info("Detection scale factor changed to " + newScaleFactor);
            }
            @Override public void onMinNeighborsChanged(int newMinNeighbors) {
                AppConfig.getInstance().setDetectionMinNeighbors(newMinNeighbors);
                AppLogger.info("Min neighbors changed to " + newMinNeighbors);
            }
            @Override public void onMinSizeChanged(int newMinSize) {
                AppConfig.getInstance().setDetectionMinSize(newMinSize);
                AppLogger.info("Min size changed to " + newMinSize);
            }
            @Override public void onCaptureFaceRequested() {
                AppLogger.warn("Capture requested from Settings GUI - no student selected.");
                JOptionPane.showMessageDialog(SettingsGUI.this,
                        "Capture action requires selecting a student. Open the Student Management / Face Capture dialog.",
                        "Capture unavailable", JOptionPane.INFORMATION_MESSAGE);
            }
            @Override public void onSaveSettingsRequested() {
                AppConfig.getInstance().save();
                AppLogger.info("Settings saved from SettingsGUI");
                JOptionPane.showMessageDialog(SettingsGUI.this, "Settings saved", "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        // Center Panel - Simple settings content
        SettingsCenter center = new SettingsCenter(listener);
        // Left Panel - categories wired to center
        JPanel leftPanel = createCategoriesPanel(center);
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(center, BorderLayout.CENTER);

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

    // updated to accept the center so category buttons can switch views
    private JPanel createCategoriesPanel(SettingsCenter center) {
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
        
        JButton welcomeBtn = createCategoryButton("Welcome!");
        JButton detectionBtn = createCategoryButton("Camera Detection Settings");
        JButton myPageBtn = createCategoryButton("My Page");

        // wire buttons to the center card layout (use constants from SettingsCenter)
        welcomeBtn.addActionListener(e -> center.showCard(SettingsCenter.WELCOME));
        detectionBtn.addActionListener(e -> center.showCard(SettingsCenter.DETECTION));
        myPageBtn.addActionListener(e -> center.showCard(SettingsCenter.MYPAGE));

        // NOTE: MYPAGE is already registered in SettingsCenter (eager). Do NOT call center.addCard(...) here.

        categoriesPanel.add(welcomeBtn);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        categoriesPanel.add(detectionBtn);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        categoriesPanel.add(myPageBtn);
        categoriesPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        

        categoriesPanel.add(Box.createVerticalGlue());

        return categoriesPanel;
    }

    // createSettingsContent now provides the SettingsCenter wrapped in the same outer card style
    private JPanel createSettingsContent() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(new Color(248, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Inner white card to match previous style
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        // The actual center is created in createUI; return an empty placeholder here to keep compatibility
        // This method is no longer used directly in the new createUI flow, but kept for parity.
        card.add(new JLabel(""), BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(248, 250, 252));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        wrapper.add(card, gbc);

        contentPanel.add(wrapper, BorderLayout.CENTER);
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