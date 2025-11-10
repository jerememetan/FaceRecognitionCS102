package gui.attendance;

import config.AppLogger;
import entity.Session;
import gui.recognition.CameraPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalTime;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import service.attendance.ManualMarker;
import service.detection.FaceDetector;
import service.recognition.LiveRecognitionService;
import service.session.SessionManager;
import service.student.StudentManager;

/**
 * Window for marking attendance during a session.
 * Displays camera feed on top and interactive attendance table at bottom.
 * 
 * This class coordinates the various managers and controllers for attendance marking.
 */
public class SessionAttendanceWindow extends JFrame {
    
    private final Session session;
    private final AttendanceRecordManager recordManager;
    private final AttendanceTableController tableController;
    private final AttendanceRecognitionManager recognitionManager;
    private final AttendanceRecordSyncHandler syncHandler;
    
    private final LiveRecognitionService recognitionService;
    private final StudentManager studentManager;
    private final ManualMarker manualMarker;
    private final FaceDetector faceDetector;
    private final SessionManager sessionManager;
    private final repository.AttendanceRecordRepository attendanceRecordRepository;
    private final repository.SessStuRepositoryInstance sessStuRepository;
    
    private CameraPanel cameraPanel;
    private Timer sessionEndTimer;
    
    public SessionAttendanceWindow(Session session) {
        super("Attendance Marking - " + session.getName());
        this.session = session;
        
        // Initialize services
        this.recognitionService = new LiveRecognitionService();
        this.studentManager = new StudentManager();
        this.manualMarker = new ManualMarker();
        this.faceDetector = new FaceDetector();
        this.sessionManager = new SessionManager();
        this.attendanceRecordRepository = new repository.AttendanceRecordRepositoryInstance();
        this.sessStuRepository = new repository.SessStuRepositoryInstance();
        this.recognitionExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AttendanceRecognitionWorker");
                t.setDaemon(true);
                return t;
            }
        });
        
        // Initialize sync handler
        this.syncHandler = new AttendanceRecordSyncHandler(session, sessStuRepository);
        
        // Initialize record manager
        this.recordManager = new AttendanceRecordManager(session, attendanceRecordRepository, syncHandler);
        recordManager.initializeRecords();
        
        // Initialize table controller
        this.tableController = new AttendanceTableController(
            recordManager.getRecordMap(),
            manualMarker,
            syncHandler
        );
        
        // Initialize UI
        initializeUI();
        
        // Initialize recognition manager
        this.recognitionManager = new AttendanceRecognitionManager(
            session,
            recognitionService,
            studentManager,
            faceDetector,
            cameraPanel,
            recordManager.getRecordMap(),
            syncHandler
        );
        
        // Set up attendance marking callback
        recognitionManager.setAttendanceMarkedListener((studentId, record) -> {
            SwingUtilities.invokeLater(() -> {
                int row = tableController.findRowByStudentId(studentId);
                if (row >= 0) {
                    tableController.updateTableRow(row, record);
                }
            });
        });
        
        // Reload recognition dataset to ensure latest faces are included
        recognitionService.reloadDataset();
        AppLogger.info("Recognition dataset reloaded for attendance window");
        
        // Start recognition and session end timer
        recognitionManager.start();
        startSessionEndTimer();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Handle window close button (X) to call closeWindow()
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                closeWindow();
            }
        }); 
        
        // Camera panel on top
        cameraPanel = new CameraPanel();
        cameraPanel.setPreferredSize(new Dimension(640, 480));
        cameraPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Live Camera Feed",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 14)
        ));
        add(cameraPanel, BorderLayout.NORTH);
        
        // Attendance table at bottom
        JPanel tablePanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableController.getTable());
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Attendance Records",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 14)
        ));
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        
        // Control buttons
        JPanel buttonPanel = new JPanel();
        JButton closeButton = new JButton("Close Session");
        closeButton.addActionListener(e -> closeWindow());
        buttonPanel.add(closeButton);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(tablePanel, BorderLayout.CENTER);
        
        // Populate table
        tableController.refreshTable(recordManager.getAttendanceRecords());
    }
    
    private void startSessionEndTimer() {
        // Check every minute if session has ended
        sessionEndTimer = new Timer(60000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LocalTime now = LocalTime.now();
                if (now.isAfter(session.getEndTime()) || now.equals(session.getEndTime())) {
                    markPendingAsAbsent();
                    sessionEndTimer.stop();
                }
            }
        });
        sessionEndTimer.start();
    }
    
    private void markPendingAsAbsent() {
        int markedCount = recordManager.markPendingAsAbsent();
        
        if (markedCount > 0) {
            tableController.refreshTable(recordManager.getAttendanceRecords());
            
            // Persist all attendance records to database
            recordManager.persistAllRecords(tableController.getTableModel());
            
            JOptionPane.showMessageDialog(this, 
                markedCount + " students marked as ABSENT (session ended).",
                "Session Ended",
                JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Even if no one was marked absent, persist all records
            recordManager.persistAllRecords(tableController.getTableModel());
        }
    }
    
    private void closeWindow() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Do you want to close the session? This will close the attendance window and mark the session as inactive.",
            "Close Session",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (choice == JOptionPane.YES_OPTION) {
            // Stop recognition manager
            recognitionManager.stop();
            
            // Stop session end timer
            if (sessionEndTimer != null) {
                sessionEndTimer.stop();
                sessionEndTimer = null;
            }
            
            // Check if session is already closed
            if (!session.isActive()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Session is already closed.",
                    "Session Already Closed",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                // Close the session
                try {
                    // Persist all attendance records before closing session
                    recordManager.persistAllRecords(tableController.getTableModel());
                    
                    sessionManager.closeSession(session);
                    AppLogger.info("Session " + session.getSessionId() + " closed from attendance window");
                    JOptionPane.showMessageDialog(
                        this,
                        "Session closed successfully. All attendance records have been saved.",
                        "Session Closed",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    AppLogger.error("Error closing session: " + e.getMessage(), e);
                    JOptionPane.showMessageDialog(
                        this,
                        "Error closing session: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
            
            // Always close the window, regardless of session close status
            // Use invokeLater to ensure it happens after dialogs are dismissed
            SwingUtilities.invokeLater(() -> {
                try {
                    setVisible(false);
                    dispose();
                    AppLogger.info("Attendance window closed");
                } catch (Exception e) {
                    AppLogger.error("Error disposing window: " + e.getMessage(), e);
                }
            });
        }
    }
}
