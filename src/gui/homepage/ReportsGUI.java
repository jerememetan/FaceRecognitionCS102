package gui.homepage;

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

import report.ReportLog;
import report.ReportManager;
import config.AppLogger;

public class ReportsGUI extends JFrame {
    private String role;
    private String id;
    private String reportName = "AllStudentData";

    public ReportsGUI(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Reports & Analytics - " + role + " (" + id + ")");
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

        // Left Panel - Simple sidebar with report options
        JPanel leftPanel = createSidebar();
        mainPanel.add(leftPanel, BorderLayout.WEST);

        // Center Panel - Simple content area
        JPanel centerPanel = createContentArea();

        try {
            // Wrap the centerPanel with a JScrollPane to enable scrolling
            JScrollPane scrollableCenterPanel = new JScrollPane(centerPanel);
            scrollableCenterPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);  // Always show vertical scrollbar
            scrollableCenterPanel.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); // Horizontal scrollbar when needed

            mainPanel.add(scrollableCenterPanel, BorderLayout.CENTER);  // Add the scrollable panel to the center of mainPanel

            add(mainPanel, BorderLayout.CENTER);
        } catch (Exception e) {
            AppLogger.error("Error Loading UI: ", e);
        }
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(45, 55, 72));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Title
        JLabel titleLabel = new JLabel("Reports & Analytics");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        // Back Button
        JButton backButton = createStyledButton("← Back to Dashboard", new Color(239, 68, 68));
        backButton.addActionListener(e -> dispose());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(backButton, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(45, 55, 72));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        sidebar.setPreferredSize(new Dimension(200, 0));

        // Sidebar Title
        JLabel sidebarTitle = new JLabel("Report Types");
        sidebarTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sidebarTitle.setForeground(Color.WHITE);
        sidebarTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(sidebarTitle);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        // Report type buttons
        JButton attendanceReportBtn = createButton("Attendance Report");
        JButton studentReportBtn = createButton("All Students Report");
        JButton sessionReportBtn = createButton("All Sessions Report");
        JButton rosterReportBtn = createButton("All Rosters Report");
        JButton sessToStuReportBtn = createButton("Sess->Stu Report");
        JButton rosToStuReportBtn = createButton("Ros->Stu Report");

        sidebar.add(attendanceReportBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(studentReportBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(sessionReportBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(rosterReportBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(sessToStuReportBtn);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(rosToStuReportBtn);

        // Add action listeners to each button
        attendanceReportBtn.addActionListener(e -> updateReportName("none"));
        studentReportBtn.addActionListener(e -> updateReportName("AllStudentData"));
        sessionReportBtn.addActionListener(e -> updateReportName("AllSessions"));
        rosterReportBtn.addActionListener(e -> updateReportName("AllRosters"));
        sessToStuReportBtn.addActionListener(e -> updateReportName("SessionToStudent"));
        rosToStuReportBtn.addActionListener(e -> updateReportName("rosToStuReportBtn"));

        sidebar.add(Box.createVerticalGlue());

        return sidebar;
    }

    // Add the action listener method that updates the reportName
    private void updateReportName(String newReportName) {
        this.reportName = newReportName;
        System.out.println("Current Report: " + reportName); 
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
        JLabel welcomeLabel = new JLabel("Reports & Analytics Center", SwingConstants.CENTER);
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
        Welcome to the Reports & Analytics section.
        
        Generate comprehensive reports and analyze attendance data.
        
        Select a report type from the sidebar to get started.
        """);
        
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40); 
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30));

        // stats panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        statsPanel.setBackground(Color.WHITE);

        // Fetch data for the stats
        int totalReports = ReportManager.getTotalReportsGenerated(reportName);
        int reportsToday = ReportManager.getTotalReportsGeneratedToday(reportName);
        LocalDateTime lastExport = ReportManager.getLastExportDateTime(reportName);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedLastExport = lastExport.format(formatter);

        statsPanel.add(createStatCard("Total Reports", String.valueOf(totalReports), new Color(59, 130, 246)));
        statsPanel.add(createStatCard("Generated Today", String.valueOf(reportsToday), new Color(34, 197, 94)));
        statsPanel.add(createStatCard("Last Export", formattedLastExport != null ? formattedLastExport : "Never", new Color(251, 191, 36)));

        textPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        textPanel.add(textArea);
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(statsPanel);

        // Table for Report Logs
        JPanel logTablePanel = createLogTablePanel();
        textPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        textPanel.add(logTablePanel);

        // Center the text block in the main content panel
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

        // Fetch all report logs
        List<ReportLog> allLogs = ReportManager.getAllReportLogs();

        // Filter the logs by the reportName
        List<ReportLog> filteredLogs = allLogs.stream()
                .filter(log -> log.getReportName().equals(reportName)) // Assuming getLog() returns a string representing the log type
                .collect(Collectors.toList()); // Collect the filtered results into a new list
        
        // Column names for the table
        String[] columns = {"Report Type", "Data Points", "Export Time"};
        
        // Create a panel to hold the table
        JPanel table = new JPanel();
        table.setLayout(new GridLayout(allLogs.size() + 1, columns.length, 10, 10));
        table.setBackground(Color.WHITE);
        
        // Create the header row
        for (String column : columns) {
            JLabel headerLabel = new JLabel(column, SwingConstants.CENTER);
            headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            headerLabel.setForeground(new Color(71, 85, 105));
            table.add(headerLabel);
        }
        
        // Create data rows
        for (ReportLog log : allLogs) {
            table.add(new JLabel(log.getReportName(), SwingConstants.CENTER));
            table.add(new JLabel(String.valueOf(log.getRowCount()), SwingConstants.CENTER));

            LocalDateTime exportDateTime = log.getExportTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedExportDateTime = exportDateTime.format(formatter);
            table.add(new JLabel(formattedExportDateTime, SwingConstants.CENTER));
        }
        
        tablePanel.add(table);
        return tablePanel;
    }

    private JPanel createStatCard(String title, String value, Color color) {
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
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(color);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(59, 130, 246));
        button.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(180, 40));
        
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






