package gui;

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
        // Use a wrapper so the settings panel (which is self-contained) sits nicely in the center
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(new Color(248, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Inner white card to match previous style
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        // Footer label for transient feedback from the settings listener
        JLabel footerLabel = new JLabel(" ");
        footerLabel.setFont(footerLabel.getFont().deriveFont(12f));
        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Listener implementation: update AppConfig, log, and write short feedback to footerLabel
        IConfigChangeListener listener = new IConfigChangeListener() {
            @Override
            public void onScaleFactorChanged(double newScaleFactor) {
                AppConfig.getInstance().setDetectionScaleFactor(newScaleFactor);
                AppLogger.info("Detection scale factor changed to " + newScaleFactor);
                footerLabel.setText("Scale factor set to " + String.format("%.2f", newScaleFactor));
                // Clear feedback shortly after
                new javax.swing.Timer(1500, ev -> footerLabel.setText(" ")).start();
            }

            @Override
            public void onMinNeighborsChanged(int newMinNeighbors) {
                AppConfig.getInstance().setDetectionMinNeighbors(newMinNeighbors);
                AppLogger.info("Min neighbors changed to " + newMinNeighbors);
                footerLabel.setText("Min neighbors set to " + newMinNeighbors);
                new javax.swing.Timer(1500, ev -> footerLabel.setText(" ")).start();
            }

            @Override
            public void onMinSizeChanged(int newMinSize) {
                AppConfig.getInstance().setDetectionMinSize(newMinSize);
                AppLogger.info("Min size changed to " + newMinSize);
                footerLabel.setText("Min face size set to " + newMinSize + " px");
                new javax.swing.Timer(1500, ev -> footerLabel.setText(" ")).start();
            }

            @Override
            public void onCaptureFaceRequested() {
                // No student context here — inform user how to capture
                AppLogger.warn("Capture requested from Settings GUI - no student selected.");
                JOptionPane.showMessageDialog(SettingsGUI.this,
                        "Capture action requires selecting a student. Open the Student Management / Face Capture dialog.",
                        "Capture unavailable", JOptionPane.INFORMATION_MESSAGE);
            }

            @Override
            public void onSaveSettingsRequested() {
                AppConfig.getInstance().save();
                AppLogger.info("Settings saved from SettingsGUI");
                footerLabel.setText("Settings saved");
                new javax.swing.Timer(1500, ev -> footerLabel.setText(" ")).start();
            }
        };

        // Create and add the settings panel (self-contained)
        // Pass showSaveButton = false because SettingsGUI renders its own centered Save button
        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(listener, false, false);
        card.add(settingsPanel, BorderLayout.CENTER);

        // Build a small south area that contains the centered save button and the footer label
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        btnRow.setOpaque(false);
        JButton saveBtn = createStyledButton("Save Detection Settings", new Color(59, 130, 246));
        saveBtn.addActionListener(e -> {
            // forward to listener (saves to AppConfig) and show transient feedback
            listener.onSaveSettingsRequested();
            footerLabel.setText("Settings saved");
            new javax.swing.Timer(1400, ev -> footerLabel.setText(" ")).start();
        });
        btnRow.add(saveBtn);

        south.add(btnRow);
        south.add(footerLabel);
        card.add(south, BorderLayout.SOUTH);

        // Center the card but make it expand to fill available space
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