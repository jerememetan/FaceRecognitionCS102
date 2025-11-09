package gui.homepage;

import app.FaceRecognitionApp;
import app.StudentManagerApp;
import entity.Student;
import gui.roster.RosterSessionMenu;
import gui.session.SessionViewer;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.Border;
import service.session.SessionManager;
import gui.FullScreenUtil;
import service.roster.RosterManager;
import report.ReportManager;
import util.ModuleLoader;
import config.AppLogger;
public class MainDashboard extends JFrame {
    private String role;
    private String id;
    private JPanel sidebar;
    private JPanel mainContent;
    private JButton menuButton; // burger button
    private boolean isSidebarVisible = true; // track if the sidebar is shown
    private SessionManager sessionManager; // for connecting to session btn
    private RosterManager rosterManager; // for connecting to roster btn


    public MainDashboard(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Dashboard - " + role + " (" + id + ")");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    setLayout(new BorderLayout()); // set the root layout
    // Enable fullscreen (windowed fullscreen) by default for the main dashboard
    FullScreenUtil.enableFullScreen(this, FullScreenUtil.Mode.MAXIMIZED);
    setVisible(true);

        // burger menu button
        JPanel burger = new JPanel(new FlowLayout(FlowLayout.LEFT));
        menuButton = UIComponents.createAccentButton("Menu",new Color(230, 130, 246)); // the symbol
        menuButton.setFont(new Font("Arial", Font.BOLD, 20)); // font, fontstyle, fontsize
        burger.add(menuButton);
        add(burger, BorderLayout.NORTH); // put it at the top

        // sidebar panel
        sidebar = new JPanel();
        UIComponents.applySidebarStyle(sidebar); // call from UIComponents.java

        addSideBarButtons();

        // Main content panel
        mainContent = new JPanel();
        UIComponents.applyMainContentStyle(mainContent); // call from UIComponents.java

        JPanel textPanel = new JPanel();
        UIComponents.applyTextPanelStyle(textPanel);

        // Welcome label
        JLabel welcomeLabel = new JLabel("Welcome to Face Recognition System!", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28)); 
        welcomeLabel.setForeground(new Color(30, 41, 59)); // Dark blue text
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Text area
        JTextArea textArea = new JTextArea();
        UIComponents.styleInfoTextArea(textArea); // call from UIComponents.java
        textArea.setText("""
         Live Recognition - Start real-time face detection and attendance marking
        
         Students - Manage student enrollment and facial data
        
         Attendance Sessions - Create and manage attendance sessions
        
         Reports - View and export attendance reports
        
         Settings - Configure application and camera settings
        """);
        
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(40); 
        textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        textArea.setMargin(new Insets(10, 30, 10, 30)); // inner padding

        textPanel.add(Box.createRigidArea(new Dimension(0, 40))); // pushes content downward
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20))); // spacing between label and text
        textPanel.add(textArea);

        // center the text block in the main content panel
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        mainContent.add(textPanel, gbc); // add to main content panel


        // add both sidebar and maincontent to the frame
        add(sidebar, BorderLayout.WEST);
        add(mainContent, BorderLayout.CENTER);

        // burger menu button action
        menuButton.addActionListener(new ActionListener() {
            // make sidebar appear by sliding (animation)
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleSidebar(); // all the function
            }
        });
        new Thread(() -> {
            try {
                // Touch ModuleLoader to trigger static initialization and collect status
                String status = ModuleLoader.getModuleStatusSummary();
                AppLogger.info("Module preload summary:\n" + status);
            } catch (Throwable t) {
                AppLogger.error("Module preload failed: " + t.getMessage(), t);
            }
        }, "ModulePreloader").start();

    }

    // function adding buttons
    public void addSideBarButtons() {
        // Add title to sidebar
        JLabel sidebarTitle = new JLabel("Navigation");
        sidebarTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        sidebarTitle.setForeground(Color.WHITE);
        sidebarTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));
        sidebar.add(sidebarTitle);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JButton recognitionBtn = new JButton("Live Recognition");
        JButton studentBtn = new JButton("Students");
        JButton sessionBtn = new JButton("Sessions");
        JButton reportBtn = new JButton("Reports");
        JButton settingBtn = new JButton("Settings");

        // define a consistent size for all the buttons
        Dimension buttonSize = new Dimension(200, 45);
        // put them in an array
        JButton[] buttons = { recognitionBtn, studentBtn, sessionBtn, reportBtn, settingBtn };
        for (JButton btn : buttons) {
            UIComponents.styleSidebarButton(btn);
            btn.setMaximumSize(buttonSize);
            btn.setPreferredSize(buttonSize);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            sidebar.add(Box.createRigidArea(new Dimension(0, 8))); // vertical spacing
            sidebar.add(btn);
        }
        sidebar.add(Box.createVerticalGlue()); // push everything up neatly

        // ---------ActionListener for the buttons--------------
        recognitionBtn.addActionListener(e -> {
        MainDashboard.this.setVisible(false);
            
            // Create a timer to detect when the recognition window appears and attach our listener
            Timer findWindowTimer = new Timer(100, null);
            findWindowTimer.addActionListener(evt -> {
                Window[] windows = Window.getWindows();
                for (Window window : windows) {
                    if (window.isVisible() && window instanceof JFrame && 
                        ((JFrame)window).getTitle().contains("Real-Time")) {
                        JFrame recognitionFrame = (JFrame)window;
                        recognitionFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        recognitionFrame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosed(WindowEvent e) {
                                SwingUtilities.invokeLater(() -> {
                                    MainDashboard.this.setVisible(true);
                                });
                            }
                        });
                        findWindowTimer.stop();
                        break;
                    }
                }
            });
            findWindowTimer.start();
            
            // Launch the recognition window
            SwingUtilities.invokeLater(() -> {
                try {
                    FaceRecognitionApp.main(null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    findWindowTimer.stop();
                    MainDashboard.this.setVisible(true);
                }
            });
        });

        // open the student enrollment application
        studentBtn.addActionListener(e -> {
            MainDashboard.this.setVisible(false);
            
            // Create a timer to detect when the student window appears and attach our listener
            Timer findWindowTimer = new Timer(100, null);
            findWindowTimer.addActionListener(evt -> {
                Window[] windows = Window.getWindows();
                for (Window window : windows) {
                    if (window.isVisible() && window instanceof JFrame && 
                        ((JFrame)window).getTitle().contains("Student")) {
                        JFrame studentFrame = (JFrame)window;
                        studentFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        studentFrame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosed(WindowEvent e) {
                                SwingUtilities.invokeLater(() -> {
                                    MainDashboard.this.setVisible(true);
                                });
                            }
                        });
                        findWindowTimer.stop();
                        break;
                    }
                }
            });
            findWindowTimer.start();
            
            // Launch the student management window
            SwingUtilities.invokeLater(() -> {
                try {
                    StudentManagerApp.main(null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    findWindowTimer.stop();
                    MainDashboard.this.setVisible(true);
                }
            });
        });

        sessionBtn.addActionListener(e -> {
            MainDashboard.this.setVisible(false);
            //create a SessionManager and initialize SessionViewer
            sessionManager = new SessionManager();
            rosterManager = new RosterManager();
            RosterSessionMenu sessionViewer = new RosterSessionMenu(rosterManager, sessionManager);
            sessionViewer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            sessionViewer.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    MainDashboard.this.setVisible(true);
                }
            });
        });

        reportBtn.addActionListener(e -> {
            MainDashboard.this.setVisible(false);

            new ReportManager(); // call constructor to load reportLogs data

            JFrame reportsFrame = new ReportsGUI(role, id);
            reportsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            reportsFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    MainDashboard.this.setVisible(true);
                }
            });
        });

        settingBtn.addActionListener(e -> {
            MainDashboard.this.setVisible(false);
            JFrame settingsFrame = new SettingsGUI(role, id);
            settingsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            settingsFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    MainDashboard.this.setVisible(true);
                }
            });
        });
    }

    

    // function of toggle sidebar
    private void toggleSidebar() {
        // make it as an animation (sliding)
        int startWidth = sidebar.getWidth(); // get the current width
        int targetWidth = isSidebarVisible ? 0 : 220; // 0 to hide, 220 to show
        int step = (targetWidth > startWidth) ? 10 : -10; // slide direction, how much to change the width

        javax.swing.Timer timer = new javax.swing.Timer(5, null); // runs every 5 milliseconds
        timer.addActionListener(new ActionListener() {
            int width = startWidth;

            @Override
            public void actionPerformed(ActionEvent e) {
                width += step; // changes the sidebar's width gradually

                // stop animation when we reach the target
                if ((step < 0 && width <= targetWidth) || (step > 0 && width >= targetWidth)) {
                    width = targetWidth;
                    timer.stop();
                    isSidebarVisible = !isSidebarVisible; // update the status, if true-->false, if false->true 
                }

                // resize the sidebar & window
                sidebar.setPreferredSize(new Dimension(Math.max(0, width), sidebar.getHeight()));
                sidebar.revalidate(); // recalculate and adjust the layout
                sidebar.repaint(); // redraws the window
                MainDashboard.this.revalidate();
            }
        });

        timer.start();
    }
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainDashboard("Admin", "123");
            }
        });
    }
}







