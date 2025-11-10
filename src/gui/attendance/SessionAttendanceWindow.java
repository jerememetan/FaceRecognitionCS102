package gui.attendance;

import config.AppLogger;
import entity.*;
import gui.recognition.CameraPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import service.attendance.AutoMarker;
import service.attendance.ManualMarker;
import service.detection.FaceDetector;
import service.recognition.LiveRecognitionService;
import service.student.StudentManager;

/**
 * Window for marking attendance during a session.
 * Displays camera feed on top and interactive attendance table at bottom.
 */
public class SessionAttendanceWindow extends JFrame {
    
    private final Session session;
    private final List<AttendanceRecord> attendanceRecords;
    private final Map<String, AttendanceRecord> recordMap; // studentId -> record
    private final LiveRecognitionService recognitionService;
    private final StudentManager studentManager;
    private final ManualMarker manualMarker;
    private final FaceDetector faceDetector;
    private final service.session.SessionManager sessionManager;
    private final repository.AttendanceRecordRepository attendanceRecordRepository;
    private final repository.SessStuRepositoryInstance sessStuRepository;
    
    private CameraPanel cameraPanel;
    private JTable attendanceTable;
    private DefaultTableModel tableModel;
    private VideoCapture capture;
    private Timer recognitionTimer;
    private Timer sessionEndTimer;
    private boolean isRunning = false;
    private boolean isUpdatingTable = false; // Flag to prevent recursive updates
    
    private static final String[] COLUMNS = {"Student ID", "Name", "Status", "Timestamp", "Method", "Confidence", "Notes"};
    private static final int RECOGNITION_INTERVAL_MS = 500; // Check every 500ms
    
    public SessionAttendanceWindow(Session session) {
        super("Attendance Marking - " + session.getName());
        this.session = session;
        this.attendanceRecords = new ArrayList<>();
        this.recordMap = new HashMap<>();
        this.recognitionService = new LiveRecognitionService();
        this.studentManager = new StudentManager();
        this.manualMarker = new ManualMarker();
        this.faceDetector = new FaceDetector();
        this.sessionManager = new service.session.SessionManager();
        this.attendanceRecordRepository = new repository.AttendanceRecordRepositoryInstance();
        this.sessStuRepository = new repository.SessStuRepositoryInstance();
        
        initializeAttendanceRecords();
        initializeUI();
        
        // Reload recognition dataset to ensure latest faces are included
        recognitionService.reloadDataset();
        AppLogger.info("Recognition dataset reloaded for attendance window");
        
        startRecognition();
        startSessionEndTimer();
    }
    
    private void initializeAttendanceRecords() {
        // Load existing attendance records from database for this session
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findBySessionId(session.getSessionId());
        Map<String, AttendanceRecord> existingRecordMap = new HashMap<>();
        for (AttendanceRecord record : existingRecords) {
            existingRecordMap.put(record.getStudent().getStudentId(), record);
        }
        
        AppLogger.info("Found " + existingRecords.size() + " existing attendance records in database for session " + session.getSessionId());
        
        // Create or load attendance records for all students in session roster
        int loadedCount = 0;
        int createdCount = 0;
        
        for (SessionStudent sessionStudent : session.getStudentRoster()) {
            Student student = sessionStudent.getStudent();
            AttendanceRecord record;
            
            // Check if record exists in database
            AttendanceRecord existingRecord = existingRecordMap.get(student.getStudentId());
            
            if (existingRecord != null) {
                // Use existing record from database (has all the data: status, timestamp, method, confidence, notes)
                record = existingRecord;
                loadedCount++;
                AppLogger.info("Loaded existing attendance record from database for " + student.getStudentId() + 
                             " - Status: " + record.getStatus() + ", Notes: '" + record.getNotes() + "'");
            } else {
                // Create new record with default PENDING status
                record = new AttendanceRecord(student, session);
                
                // Sync initial status and notes from SessionStudent if available
                if (sessionStudent.getStatus() != null && !sessionStudent.getStatus().isEmpty()) {
                    try {
                        // Map SessionStudent status string to AttendanceRecord.Status enum
                        String sessStuStatus = sessionStudent.getStatus();
                        if (sessStuStatus.equalsIgnoreCase("Present")) {
                            record.setStatus(AttendanceRecord.Status.PRESENT);
                        } else if (sessStuStatus.equalsIgnoreCase("Late")) {
                            record.setStatus(AttendanceRecord.Status.LATE);
                        } else if (sessStuStatus.equalsIgnoreCase("Absent")) {
                            record.setStatus(AttendanceRecord.Status.ABSENT);
                        } else {
                            record.setStatus(AttendanceRecord.Status.PENDING);
                        }
                    } catch (Exception e) {
                        AppLogger.warn("Could not map SessionStudent status '" + sessionStudent.getStatus() + 
                                     "' for " + student.getStudentId() + ", using PENDING");
                    }
                }
                
                // Sync notes from SessionStudent
                if (sessionStudent.getNotes() != null && !sessionStudent.getNotes().isEmpty()) {
                    record.setNotes(sessionStudent.getNotes());
                }
                
                createdCount++;
                AppLogger.info("Created new attendance record for " + student.getStudentId());
            }
            
            attendanceRecords.add(record);
            recordMap.put(student.getStudentId(), record);
        }
        
        AppLogger.info("Initialized " + attendanceRecords.size() + " attendance records for session " + session.getSessionId() + 
                      " (Loaded: " + loadedCount + ", Created: " + createdCount + ")");
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
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Status column (2) and Notes column (6) are editable
                return column == 2 || column == 6;
            }
        };
        
        attendanceTable = new JTable(tableModel);
        attendanceTable.setRowHeight(25);
        attendanceTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        attendanceTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        
        // Make Status column a dropdown
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"PENDING", "PRESENT", "LATE", "ABSENT"});
        attendanceTable.getColumnModel().getColumn(2).setCellEditor(
            new javax.swing.DefaultCellEditor(statusCombo));
        
        // Make Notes column editable with a text field
        JTextField notesField = new JTextField();
        notesField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        attendanceTable.getColumnModel().getColumn(6).setCellEditor(
            new javax.swing.DefaultCellEditor(notesField));
        
        // Set column widths
        attendanceTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Student ID
        attendanceTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Name
        attendanceTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Status
        attendanceTable.getColumnModel().getColumn(3).setPreferredWidth(180);  // Timestamp
        attendanceTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Method
        attendanceTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Confidence
        attendanceTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Notes
        
        // Add action listener for status and notes changes
        attendanceTable.getModel().addTableModelListener(e -> {
            // Ignore updates triggered by programmatic changes
            if (isUpdatingTable) {
                return;
            }
            
            int column = e.getColumn();
            int modelRow = e.getFirstRow();
            
            // Handle Status column changes
            if (column == 2) {
                String studentId = (String) tableModel.getValueAt(modelRow, 0);
                String newStatus = (String) tableModel.getValueAt(modelRow, 2);
                
                AttendanceRecord record = recordMap.get(studentId);
                if (record != null) {
                    // Check if status actually changed
                    if (record.getStatus().toString().equals(newStatus)) {
                        return; // No change, ignore
                    }
                    
                    try {
                        AttendanceRecord.Status status = AttendanceRecord.Status.valueOf(newStatus);
                        
                        // Only allow marking if status is not PENDING
                        if (status == AttendanceRecord.Status.PENDING) {
                            AppLogger.warn("Cannot manually set status to PENDING");
                            // Revert the table cell to previous value
                            SwingUtilities.invokeLater(() -> {
                                updateTableRow(modelRow, record);
                            });
                            return;
                        }
                        
                        record.setStatus(status);
                        if (manualMarker.markAttendance(record)) {
                            // Sync to SessionStudent
                            syncAttendanceRecordToSessionStudent(record);
                            
                            // Log that attendance was marked
                            AppLogger.info(String.format(
                                "Manually marked attendance: Student=%s, Status=%s, Timestamp=%s, Method=%s",
                                studentId, record.getStatus(), record.getTimestamp(), record.getMarkingMethod()));
                            
                            // Update table with model row index (this will update timestamp and method)
                            SwingUtilities.invokeLater(() -> {
                                updateTableRow(modelRow, record);
                            });
                        } else {
                            AppLogger.error("Failed to mark attendance for " + studentId);
                            // Revert the table cell to previous value
                            SwingUtilities.invokeLater(() -> {
                                updateTableRow(modelRow, record);
                            });
                        }
                    } catch (IllegalArgumentException ex) {
                        AppLogger.error("Invalid status: " + newStatus);
                        // Revert the table cell to previous value
                        SwingUtilities.invokeLater(() -> {
                            updateTableRow(modelRow, record);
                        });
                    }
                }
            }
            
            // Handle Notes column changes
            if (column == 6) {
                String studentId = (String) tableModel.getValueAt(modelRow, 0);
                String newNotes = (String) tableModel.getValueAt(modelRow, 6);
                
                AttendanceRecord record = recordMap.get(studentId);
                if (record != null) {
                    record.setNotes(newNotes != null ? newNotes : "");
                    AppLogger.info("Updated notes for " + studentId + ": " + record.getNotes());
                    
                    // Sync to SessionStudent
                    syncAttendanceRecordToSessionStudent(record);
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(attendanceTable);
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
        refreshTable();
    }
    
    private void refreshTable() {
        tableModel.setRowCount(0);
        for (AttendanceRecord record : attendanceRecords) {
            addTableRow(record);
        }
    }
    
    private void addTableRow(AttendanceRecord record) {
        Student student = record.getStudent();
        String confidenceStr = record.getConfidence() != null ? 
            String.format("%.1f%%", record.getConfidence() * 100) : "-";
        Object[] row = {
            student.getStudentId(),
            student.getName(),
            record.getStatus().toString(),
            record.getTimestamp() != null ? record.getTimestamp().toString() : "-",
            record.getMarkingMethod() != null ? record.getMarkingMethod().toString() : "-",
            confidenceStr,
            record.getNotes() != null ? record.getNotes() : ""
        };
        tableModel.addRow(row);
    }
    
    /**
     * Updates a table row with the current attendance record values.
     * @param modelRow The model row index (not view row index)
     * @param record The attendance record with updated values
     */
    private void updateTableRow(int modelRow, AttendanceRecord record) {
        // Ensure we're working with model row index
        if (modelRow < 0 || modelRow >= tableModel.getRowCount()) {
            AppLogger.warn("Invalid model row index: " + modelRow);
            return;
        }
        
        // Set flag to prevent recursive table model listener triggers
        isUpdatingTable = true;
        
        try {
            // Update all columns with current record values
            String statusStr = record.getStatus().toString();
            String timestampStr = record.getTimestamp() != null ? record.getTimestamp().toString() : "-";
            String methodStr = record.getMarkingMethod() != null ? record.getMarkingMethod().toString() : "-";
            String confidenceStr = record.getConfidence() != null ? 
                String.format("%.1f%%", record.getConfidence() * 100) : "-";
            String notesStr = record.getNotes() != null ? record.getNotes() : "";
            
            // Update Status column
            if (!statusStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 2)))) {
                tableModel.setValueAt(statusStr, modelRow, 2);
            }
            
            // Update Timestamp column
            if (!timestampStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 3)))) {
                tableModel.setValueAt(timestampStr, modelRow, 3);
            }
            
            // Update Method column
            if (!methodStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 4)))) {
                tableModel.setValueAt(methodStr, modelRow, 4);
            }
            
            // Update Confidence column
            if (!confidenceStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 5)))) {
                tableModel.setValueAt(confidenceStr, modelRow, 5);
            }
            
            // Update Notes column (only if not currently being edited by user)
            if (!notesStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 6)))) {
                // Only update if the cell is not being edited
                if (attendanceTable.getEditingRow() != modelRow || attendanceTable.getEditingColumn() != 6) {
                    tableModel.setValueAt(notesStr, modelRow, 6);
                }
            }
            
            // Fire cell updated events to ensure UI refreshes
            tableModel.fireTableCellUpdated(modelRow, 2);
            tableModel.fireTableCellUpdated(modelRow, 3);
            tableModel.fireTableCellUpdated(modelRow, 4);
            tableModel.fireTableCellUpdated(modelRow, 5);
            if (attendanceTable.getEditingRow() != modelRow || attendanceTable.getEditingColumn() != 6) {
                tableModel.fireTableCellUpdated(modelRow, 6);
            }
            
            AppLogger.info(String.format("Updated table row %d: Status=%s, Timestamp=%s, Method=%s, Confidence=%s, Notes=%s",
                modelRow, record.getStatus(), record.getTimestamp(), record.getMarkingMethod(), 
                confidenceStr, notesStr));
        } finally {
            // Always reset flag
            isUpdatingTable = false;
        }
        
        // Force table repaint to ensure changes are visible
        SwingUtilities.invokeLater(() -> {
            attendanceTable.repaint();
        });
    }
    
    private void startRecognition() {
        // Initialize camera
        capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            JOptionPane.showMessageDialog(this, "Failed to open camera!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        isRunning = true;
        
        // Recognition timer - processes frames periodically
        recognitionTimer = new Timer(RECOGNITION_INTERVAL_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isRunning) return;
                
                Mat frame = new Mat();
                if (capture.read(frame) && !frame.empty()) {
                    // Detect faces, recognize, and draw overlays
                    Mat frameWithOverlays = processFrameForAttendance(frame);
                    
                    // Display frame with overlays
                    cameraPanel.displayMat(frameWithOverlays);
                    
                    frameWithOverlays.release();
                }
                frame.release();
            }
        });
        recognitionTimer.start();
    }
    
    private Mat processFrameForAttendance(Mat frame) {
        // Create a copy of the frame to draw on
        Mat frameWithOverlays = frame.clone();
        
        try {
            // Detect all faces in the frame
            List<Rect> faces = detectFaces(frame);
            
            if (faces.isEmpty()) {
                return frameWithOverlays;
            }
            
            // Process each detected face
            for (Rect faceRect : faces) {
                // Recognize with detailed confidence info
                LiveRecognitionService.DetailedRecognitionResult result = 
                    recognitionService.analyzeFaceDetailed(frame, faceRect, session.getSessionId());
                
                // Log recognition result for debugging
                if (result != null) {
                    AppLogger.info(String.format("Recognition result: studentId=%s, confidence=%.2f, recognized=%b",
                        result.getStudentId(), result.getConfidence(), result.isRecognized()));
                } else {
                    AppLogger.warn("Recognition result is null");
                }
                
                // Get color and label based on confidence
                Scalar boxColor = getColorForConfidence(result.getConfidence());
                String displayLabel = getDisplayLabel(result);
                
                // Draw bounding box
                Imgproc.rectangle(frameWithOverlays, 
                    new Point(faceRect.x, faceRect.y),
                    new Point(faceRect.x + faceRect.width, faceRect.y + faceRect.height),
                    boxColor, 3);
                
                // Draw label text above the box
                Point textPosition = new Point(faceRect.x, Math.max(25, faceRect.y - 10));
                Imgproc.putText(frameWithOverlays, displayLabel,
                    textPosition,
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, boxColor, 2);
                
                // Process attendance marking for recognized students
                if (result != null && result.getStudentId() != null && result.getConfidence() > 0) {
                    String studentId = result.getStudentId();
                    AttendanceRecord record = recordMap.get(studentId);
                    
                    if (record != null && record.getStatus() == AttendanceRecord.Status.PENDING) {
                        // Create AutoMarker with recognition result
                        Student student = studentManager.findStudentById(studentId);
                        if (student != null) {
                            AutoMarker.RecognitionResult recResult = new AutoMarker.RecognitionResult(
                                student, result.getConfidence(), result.isRecognized());
                            AutoMarker autoMarker = new AutoMarker(recResult);
                            
                            if (autoMarker.markAttendance(record)) {
                                // Sync to SessionStudent
                                syncAttendanceRecordToSessionStudent(record);
                                
                                // Log that attendance was marked
                                AppLogger.info(String.format(
                                    "Auto-marked attendance: Student=%s, Status=%s, Timestamp=%s, Method=%s",
                                    studentId, record.getStatus(), record.getTimestamp(), record.getMarkingMethod()));
                                
                                // Update table immediately
                                SwingUtilities.invokeLater(() -> {
                                    int row = findRowByStudentId(studentId);
                                    if (row >= 0) {
                                        updateTableRow(row, record);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.error("Error processing frame for attendance: " + e.getMessage(), e);
        }
        
        return frameWithOverlays;
    }
    
    /**
     * Returns color based on confidence level:
     * - Green: confidence >= 60%
     * - Orange: confidence 40-60%
     * - Red: confidence < 40%
     */
    private Scalar getColorForConfidence(double confidence) {
        if (confidence >= 0.60) {
            // Green for high confidence
            return new Scalar(0, 255, 0); // BGR format: Green
        } else if (confidence >= 0.40) {
            // Orange for medium confidence
            return new Scalar(0, 165, 255); // BGR format: Orange
        } else {
            // Red for low confidence
            return new Scalar(0, 0, 255); // BGR format: Red
        }
    }
    
    /**
     * Gets display label for the recognition result.
     * Shows student name and confidence percentage.
     * Also shows recognition result even if confidence is low (for debugging).
     */
    private String getDisplayLabel(LiveRecognitionService.DetailedRecognitionResult result) {
        if (result == null) {
            return "Unknown";
        }
        
        // Show result even if not "recognized" (decision.accepted() == false)
        // but has a student ID and confidence > 0
        if (result.getStudentId() != null && result.getConfidence() > 0) {
            Student student = studentManager.findStudentById(result.getStudentId());
            if (student != null) {
                // Format: "Name (Confidence%)"
                int confidencePercent = (int) (result.getConfidence() * 100);
                String status = result.isRecognized() ? "" : "?";
                return String.format("%s%s (%d%%)", student.getName(), status, confidencePercent);
            }
        }
        
        // If we have some confidence but no student ID, show confidence
        if (result.getConfidence() > 0) {
            int confidencePercent = (int) (result.getConfidence() * 100);
            return String.format("Unknown (%d%%)", confidencePercent);
        }
        
        return "Unknown";
    }
    
    private List<Rect> detectFaces(Mat frame) {
        if (faceDetector == null) {
            return new ArrayList<>();
        }
        return faceDetector.detectFaces(frame);
    }
    
    /**
     * Finds the model row index for a given student ID.
     * @param studentId The student ID to search for
     * @return The model row index, or -1 if not found
     */
    private int findRowByStudentId(String studentId) {
        // Search through model rows (not view rows) to find the correct index
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object cellValue = tableModel.getValueAt(i, 0);
            if (studentId.equals(cellValue)) {
                // Return model row index directly (updateTableRow expects model row)
                return i;
            }
        }
        AppLogger.warn("Could not find row for student ID: " + studentId);
        return -1;
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
        LocalDateTime now = LocalDateTime.now();
        int markedCount = 0;
        
        for (AttendanceRecord record : attendanceRecords) {
            if (record.getStatus() == AttendanceRecord.Status.PENDING) {
                record.setStatus(AttendanceRecord.Status.ABSENT);
                record.setTimestamp(now);
                record.setMarkingMethod(AttendanceRecord.MarkingMethod.AUTOMATIC);
                markedCount++;
                
                // Sync to SessionStudent
                syncAttendanceRecordToSessionStudent(record);
            }
        }
        
        if (markedCount > 0) {
            refreshTable();
            AppLogger.info("Auto-marked " + markedCount + " students as ABSENT at session end");
            
            // Persist all attendance records to database
            persistAllAttendanceRecords();
            
            JOptionPane.showMessageDialog(this, 
                markedCount + " students marked as ABSENT (session ended).",
                "Session Ended",
                JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Even if no one was marked absent, persist all records
            persistAllAttendanceRecords();
        }
    }
    
    /**
     * Persists all attendance records to the database.
     * Uses save or update based on whether the record already exists in the database.
     */
    private void persistAllAttendanceRecords() {
        int savedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;
        
        for (AttendanceRecord record : attendanceRecords) {
            try {
                // Ensure notes from table are synced to record before persisting
                // Search through model rows directly to find the student's row
                int modelRow = -1;
                String studentId = record.getStudent().getStudentId();
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    Object cellValue = tableModel.getValueAt(i, 0);
                    if (studentId.equals(cellValue)) {
                        modelRow = i;
                        break;
                    }
                }
                
                if (modelRow >= 0) {
                    Object tableNotesValue = tableModel.getValueAt(modelRow, 6);
                    if (tableNotesValue != null) {
                        String tableNotes = tableNotesValue.toString().trim();
                        // Update record with latest notes from table
                        record.setNotes(tableNotes);
                        AppLogger.info("Synced notes from table for " + studentId + 
                                     ": '" + tableNotes + "'");
                    } else {
                        // If table has null, ensure record has empty string
                        record.setNotes("");
                        AppLogger.info("Synced empty notes from table for " + studentId);
                    }
                } else {
                    AppLogger.warn("Could not find table row for student " + studentId + 
                                 " when syncing notes");
                }
                
                // Log what we're about to persist
                String notesToPersist = record.getNotes() != null ? record.getNotes() : "";
                AppLogger.info(String.format(
                    "Persisting attendance record - Student: %s, Status: %s, Notes: '%s'",
                    studentId,
                    record.getStatus(),
                    notesToPersist
                ));
                
                // Sync to SessionStudent before persisting
                syncAttendanceRecordToSessionStudent(record);
                
                // Check if record already exists in database
                AttendanceRecord existing = attendanceRecordRepository.findBySessionAndStudent(
                    session.getSessionId(), 
                    record.getStudent().getStudentId()
                );
                
                if (existing != null) {
                    // Update existing record
                    boolean success = attendanceRecordRepository.update(record);
                    if (success) {
                        updatedCount++;
                        AppLogger.info("Updated attendance record for " + record.getStudent().getStudentId());
                    } else {
                        errorCount++;
                        AppLogger.error("Failed to update attendance record for " + record.getStudent().getStudentId());
                    }
                } else {
                    // Save new record
                    boolean success = attendanceRecordRepository.save(record);
                    if (success) {
                        savedCount++;
                        AppLogger.info("Saved attendance record for " + record.getStudent().getStudentId());
                    } else {
                        errorCount++;
                        AppLogger.error("Failed to save attendance record for " + record.getStudent().getStudentId());
                    }
                }
            } catch (Exception e) {
                errorCount++;
                AppLogger.error("Error persisting attendance record for " + 
                              record.getStudent().getStudentId() + ": " + e.getMessage(), e);
            }
        }
        
        AppLogger.info(String.format(
            "Persisted attendance records: %d saved, %d updated, %d errors (Total: %d)",
            savedCount, updatedCount, errorCount, attendanceRecords.size()
        ));
    }
    
    /**
     * Synchronizes AttendanceRecord status and notes to the corresponding SessionStudent.
     * Updates both the in-memory SessionStudent object and the database.
     * 
     * @param record The AttendanceRecord to sync
     */
    private void syncAttendanceRecordToSessionStudent(AttendanceRecord record) {
        if (record == null || record.getStudent() == null || record.getSession() == null) {
            AppLogger.warn("Cannot sync AttendanceRecord to SessionStudent: record, student, or session is null");
            return;
        }
        
        try {
            // Find the SessionStudent in the session's roster
            SessionStudent sessionStudent = null;
            for (SessionStudent ss : session.getStudentRoster()) {
                if (ss.getStudent().getStudentId().equals(record.getStudent().getStudentId())) {
                    sessionStudent = ss;
                    break;
                }
            }
            
            if (sessionStudent == null) {
                AppLogger.warn("SessionStudent not found for student " + record.getStudent().getStudentId() + 
                             " in session " + record.getSession().getSessionId());
                return;
            }
            
            // Map AttendanceRecord.Status enum to SessionStudent status string
            String statusString = mapStatusEnumToString(record.getStatus());
            String notes = record.getNotes() != null ? record.getNotes() : "";
            
            // Update SessionStudent
            sessionStudent.setStatus(statusString);
            sessionStudent.setNotes(notes);
            
            // Update in database
            boolean success = sessStuRepository.update(sessionStudent);
            if (success) {
                AppLogger.info(String.format(
                    "Synced AttendanceRecord to SessionStudent - Student: %s, Status: %s, Notes: '%s'",
                    record.getStudent().getStudentId(), statusString, notes
                ));
            } else {
                AppLogger.error("Failed to update SessionStudent in database for student " + 
                              record.getStudent().getStudentId());
            }
            
        } catch (Exception e) {
            AppLogger.error("Error syncing AttendanceRecord to SessionStudent: " + e.getMessage(), e);
        }
    }
    
    /**
     * Maps AttendanceRecord.Status enum to SessionStudent status string.
     * 
     * @param status The AttendanceRecord.Status enum value
     * @return The corresponding status string for SessionStudent
     */
    private String mapStatusEnumToString(AttendanceRecord.Status status) {
        if (status == null) {
            return "Pending";
        }
        
        switch (status) {
            case PENDING:
                return "Pending";
            case PRESENT:
                return "Present";
            case LATE:
                return "Late";
            case ABSENT:
                return "Absent";
            default:
                AppLogger.warn("Unknown status enum: " + status);
                return "Pending";
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
            // Stop all timers and release resources first
            isRunning = false;
            if (recognitionTimer != null) {
                recognitionTimer.stop();
                recognitionTimer = null;
            }
            if (sessionEndTimer != null) {
                sessionEndTimer.stop();
                sessionEndTimer = null;
            }
            if (capture != null && capture.isOpened()) {
                capture.release();
                capture = null;
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
                    persistAllAttendanceRecords();
                    
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

