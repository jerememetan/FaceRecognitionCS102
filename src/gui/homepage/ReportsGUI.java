package gui.homepage;
import util.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import config.AppLogger;
import gui.FullScreenUtil;
import report.ReportLog;
import report.ReportManager;

public class ReportsGUI extends JFrame {
    private String role;
    private String id;
    private String reportName = "";

    private JPanel mainPanel; // main layout container

    public ReportsGUI(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Reports & Analytics - " + role + " (" + id + ")");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Open maximized by default
        FullScreenUtil.enableFullScreen(this, FullScreenUtil.Mode.MAXIMIZED);
        setVisible(true);

        createUI();
    }

    private void createUI() {
        // Header Panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Main Content Panel
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ColourTheme.BACKGROUND);

        // Left Sidebar
        JPanel leftPanel = createSidebar();
        mainPanel.add(leftPanel, BorderLayout.WEST);

        // Center Panel
        JPanel centerPanel = createContentArea();
        JScrollPane scrollableCenterPanel = new JScrollPane(centerPanel);
        scrollableCenterPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollableCenterPanel.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainPanel.add(scrollableCenterPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColourTheme.HEADER_PANEL_BACKGROUND);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Reports & Analytics");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        JButton backButton = createStyledButton("← Back to Dashboard", ColourTheme.DANGER);
        backButton.addActionListener(e -> dispose());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(backButton, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(ColourTheme.HEADER_PANEL_BACKGROUND);
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        sidebar.setPreferredSize(new Dimension(200, 0));

        JLabel sidebarTitle = new JLabel("Report Types");
        sidebarTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sidebarTitle.setForeground(Color.WHITE);
        sidebarTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        sidebar.add(sidebarTitle);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        // Report type buttons
        sidebar.add(createSidebarButton("Attendance Report", "AttendanceHistory"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createSidebarButton("All Students Report", "AllStudentData"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createSidebarButton("All Sessions Report", "AllSessions"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createSidebarButton("All Rosters Report", "AllRosters"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createSidebarButton("Sess->Stu Report", "SessionToStudent"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createSidebarButton("Ros->Stu Report", "RosterToStudent"));

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private JButton createSidebarButton(String text, String targetReportName) {
        JButton button = createButton(text);
        button.addActionListener(e -> updateReportName(targetReportName));
        return button;
    }

    /** Updates reportName and refreshes content area */
    private void updateReportName(String newReportName) {
        if (role.equals("TA")) return;
        
        this.reportName = newReportName;
        AppLogger.info("🔄 Updating report view for: " + newReportName);

        // Create new content panel
        JPanel newContent = createContentArea();
        JScrollPane scrollableCenterPanel = new JScrollPane(newContent);
        scrollableCenterPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollableCenterPanel.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Replace old center panel with new one
        mainPanel.remove(1); // remove old center (index 0 = sidebar)
        mainPanel.add(scrollableCenterPanel, BorderLayout.CENTER);

        // Refresh UI
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private JPanel createContentArea() {
        AppLogger.info("reportName: " + this.reportName);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(ColourTheme.BACKGROUND);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(Color.WHITE);
        textPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
            BorderFactory.createEmptyBorder(30, 30, 30, 30)
        ));

        JLabel welcomeLabel = new JLabel("Reports & Analytics Center", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        welcomeLabel.setForeground(ColourTheme.TEXT_PRIMARY);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JTextArea textArea;

        if (role.equals("TA")){
            textArea = new JTextArea("""
                As a TA, you do not have permission to view this.
            """);
        } else {
            textArea = new JTextArea("""
            Welcome to the Reports & Analytics section.
            
            Generate comprehensive reports and analyze attendance data.
            
            Select a report type from the sidebar to get started.
            """);
        }

        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        textArea.setForeground(ColourTheme.FOREGROUND_COLOR);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40);
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30));

        // Stats panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        statsPanel.setBackground(Color.WHITE);

        // Fetch updated stats
        int totalReports = ReportManager.getTotalReportsGenerated(reportName);
        int reportsToday = ReportManager.getTotalReportsGeneratedToday(reportName);
        LocalDateTime lastExport = ReportManager.getLastExportDateTime(reportName);

        String formattedLastExport;
        if (lastExport != null) {
            formattedLastExport = lastExport.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } else {
            AppLogger.warn("⚠️ No last export date found for report: " + reportName);
            formattedLastExport = "N/A";
        }

        statsPanel.add(createStatCard("Total Reports", String.valueOf(totalReports), ColourTheme.PRIMARY_COLOR));
        statsPanel.add(createStatCard("Generated Today", String.valueOf(reportsToday), ColourTheme.SUCCESS_COLOR));
        statsPanel.add(createStatCard("Last Export", formattedLastExport, ColourTheme.WARNING_COLOR));

        textPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        textPanel.add(textArea);
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(statsPanel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(createLogTablePanel());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(textPanel, gbc);

        return contentPanel;
    }

    private JPanel createLogTablePanel() {
        JPanel tablePanel = new JPanel();
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));

        List<ReportLog> allLogs = ReportManager.getAllReportLogs();
        List<ReportLog> filteredLogs = allLogs.stream()
                .filter(log -> log.getReportName() != null && log.getReportName().contains(reportName))
                .collect(Collectors.toList());

        String[] columns = {"Report Type", "Data Points", "Export Time"};

        JPanel table = new JPanel();
        table.setLayout(new GridLayout(filteredLogs.size() + 1, columns.length, 10, 10));
        table.setBackground(Color.WHITE);

        for (String column : columns) {
            JLabel headerLabel = new JLabel(column, SwingConstants.CENTER);
            headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            headerLabel.setForeground(ColourTheme.FOREGROUND_COLOR);
            table.add(headerLabel);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Sort the logs by the earliest exportTime
        filteredLogs = filteredLogs.stream()
            .sorted(Comparator.comparing(ReportLog::getExportTime))  // Sort by exportTime in ascending order
            .collect(Collectors.toList());

        for (ReportLog log : filteredLogs) {
            table.add(new JLabel(log.getReportName(), SwingConstants.CENTER));
            table.add(new JLabel(String.valueOf(log.getRowCount()), SwingConstants.CENTER));

            LocalDateTime exportDateTime = log.getExportTime();
            String formattedExportDateTime = (exportDateTime != null)
                    ? exportDateTime.format(formatter)
                    : "N/A";
            table.add(new JLabel(formattedExportDateTime, SwingConstants.CENTER));
            AppLogger.info(formattedExportDateTime);
        }

        tablePanel.add(table);
        return tablePanel;
    }

    private JPanel createStatCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setForeground(ColourTheme.FOREGROUND_COLOR);

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(color);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(ColourTheme.PRIMARY_COLOR);
        button.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(180, 40));

        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(new Color(37, 99, 235)); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(ColourTheme.PRIMARY_COLOR); }
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
