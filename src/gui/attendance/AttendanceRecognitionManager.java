package gui.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Session;
import entity.Student;
import gui.recognition.CameraPanel;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;
import service.attendance.AutoMarker;
import service.detection.FaceDetector;
import service.recognition.LiveRecognitionService;
import service.student.StudentManager;
import javax.swing.*;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages camera capture, face detection, and recognition processing for attendance marking.
 */
public class AttendanceRecognitionManager {
    
    private static final int RECOGNITION_INTERVAL_MS = 500; // Process recognition every 500ms
    private static final int CAMERA_FPS_TARGET = 15; // Target FPS for camera display (minimum 15)
    private static final long RECOGNITION_CACHE_TIMEOUT_MS = 1000; // Recognition cache valid for 1 second
    
    private final Session session;
    private final LiveRecognitionService recognitionService;
    private final StudentManager studentManager;
    private final FaceDetector faceDetector;
    private final FaceOverlayRenderer overlayRenderer;
    private final CameraPanel cameraPanel;
    private final Map<String, AttendanceRecord> recordMap;
    private final AttendanceRecordSyncHandler syncHandler;
    
    private VideoCapture capture;
    private Timer recognitionTimer;
    private Thread cameraThread;
    private Mat currentFrame;
    private final Object frameLock = new Object();
    private Map<String, FaceOverlayRenderer.FaceRecognitionInfo> recognitionCache;
    private boolean isRunning = false;
    
    public AttendanceRecognitionManager(
            Session session,
            LiveRecognitionService recognitionService,
            StudentManager studentManager,
            FaceDetector faceDetector,
            CameraPanel cameraPanel,
            Map<String, AttendanceRecord> recordMap,
            AttendanceRecordSyncHandler syncHandler) {
        this.session = session;
        this.recognitionService = recognitionService;
        this.studentManager = studentManager;
        this.faceDetector = faceDetector;
        this.cameraPanel = cameraPanel;
        this.recordMap = recordMap;
        this.syncHandler = syncHandler;
        this.recognitionCache = new HashMap<>();
        this.overlayRenderer = new FaceOverlayRenderer(studentManager, recognitionCache);
    }
    
    /**
     * Starts camera capture and recognition processing.
     */
    public void start() {
        // Initialize camera
        capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            JOptionPane.showMessageDialog(null, "Failed to open camera!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Set camera resolution for better performance
        capture.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 640);
        capture.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
        
        isRunning = true;
        
        // Start camera capture thread for smooth video display at 15+ FPS
        cameraThread = new Thread(() -> {
            Mat frame = new Mat();
            long lastFrameTime = System.currentTimeMillis();
            long frameInterval = 1000 / CAMERA_FPS_TARGET; // Target frame interval in ms (66ms for 15fps)
            
            while (isRunning && capture.isOpened()) {
                long currentTime = System.currentTimeMillis();
                
                // Control frame rate to maintain minimum 15 FPS
                if (currentTime - lastFrameTime < frameInterval) {
                    try {
                        Thread.sleep(frameInterval - (currentTime - lastFrameTime));
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                lastFrameTime = System.currentTimeMillis();
                
                if (capture.read(frame) && !frame.empty()) {
                    // Detect faces
                    List<Rect> detectedFaces = detectFaces(frame);
                    
                    // Create display frame with face detection boxes
                    Mat displayFrame = overlayRenderer.drawFrameWithBoxes(frame.clone(), detectedFaces, RECOGNITION_CACHE_TIMEOUT_MS);
                    
                    synchronized (frameLock) {
                        if (currentFrame != null) {
                            currentFrame.release();
                        }
                        currentFrame = frame.clone();
                    }
                    
                    // Display frame immediately
                    cameraPanel.displayMat(displayFrame);
                    displayFrame.release();
                }
            }
            
            frame.release();
            AppLogger.info("Camera thread stopped");
        }, "CameraCaptureThread");
        cameraThread.setDaemon(true);
        cameraThread.start();
        
        // Recognition timer - processes recognition periodically on separate thread
        recognitionTimer = new Timer(RECOGNITION_INTERVAL_MS, e -> {
            if (!isRunning) return;
            
            // Process recognition on a separate thread to avoid blocking video
            new Thread(() -> {
                Mat frameToProcess = null;
                synchronized (frameLock) {
                    if (currentFrame != null && !currentFrame.empty()) {
                        frameToProcess = currentFrame.clone();
                    }
                }
                
                if (frameToProcess != null) {
                    try {
                        processRecognition(frameToProcess);
                    } catch (Exception ex) {
                        AppLogger.error("Error processing recognition: " + ex.getMessage(), ex);
                    } finally {
                        frameToProcess.release();
                    }
                }
            }, "RecognitionThread").start();
        });
        recognitionTimer.start();
    }
    
    /**
     * Stops camera capture and recognition processing.
     */
    public void stop() {
        isRunning = false;
        
        // Stop camera thread
        if (cameraThread != null && cameraThread.isAlive()) {
            try {
                cameraThread.interrupt();
                cameraThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                AppLogger.warn("Interrupted while waiting for camera thread to stop");
            }
        }
        
        if (recognitionTimer != null) {
            recognitionTimer.stop();
            recognitionTimer = null;
        }
        
        // Release frame resources
        synchronized (frameLock) {
            if (currentFrame != null) {
                currentFrame.release();
                currentFrame = null;
            }
        }
        
        // Clear recognition cache
        recognitionCache.clear();
        
        if (capture != null && capture.isOpened()) {
            capture.release();
            capture = null;
        }
    }
    
    /**
     * Processes recognition for faces in a frame and updates the recognition cache.
     * This runs less frequently to avoid blocking the video feed.
     */
    private void processRecognition(Mat frame) {
        try {
            // Validate frame first
            if (frame == null || frame.empty()) {
                return; // No valid frame to process
            }
            
            // Detect faces
            List<Rect> faces = detectFaces(frame);
            
            if (faces == null || faces.isEmpty()) {
                return; // No faces detected - do nothing
            }
            
            // Process each detected face for recognition
            for (Rect faceRect : faces) {
                // Strict validation: ensure face rectangle is valid and reasonable
                if (faceRect == null) {
                    continue;
                }
                
                // Validate face rectangle bounds and size
                if (faceRect.width <= 0 || faceRect.height <= 0 || 
                    faceRect.x < 0 || faceRect.y < 0 ||
                    faceRect.x + faceRect.width > frame.width() ||
                    faceRect.y + faceRect.height > frame.height()) {
                    continue;
                }
                
                // Additional validation: face must be reasonable size (at least 50x50 pixels)
                if (faceRect.width < 50 || faceRect.height < 50) {
                    continue;
                }
                
                // Recognize with detailed confidence info
                LiveRecognitionService.DetailedRecognitionResult result = 
                    recognitionService.analyzeFaceDetailed(frame, faceRect, session.getSessionId());
                
                // Only proceed if we have a valid result
                if (result == null) {
                    continue; // No recognition result - skip
                }
                
                // Get color and label based on confidence
                Scalar boxColor = FaceOverlayRenderer.getColorForConfidence(result.getConfidence());
                String displayLabel = overlayRenderer.getDisplayLabel(result);
                
                // Store in recognition cache (always cache for display purposes)
                String cacheKey = faceRect.x + "," + faceRect.y + "," + faceRect.width + "," + faceRect.height;
                recognitionCache.put(cacheKey, new FaceOverlayRenderer.FaceRecognitionInfo(
                    faceRect.clone(), boxColor, displayLabel));
                
                // Process attendance marking
                processAttendanceMarking(result, faceRect);
            }
        } catch (Exception e) {
            AppLogger.error("Error processing recognition: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes attendance marking for a recognition result.
     */
    private void processAttendanceMarking(LiveRecognitionService.DetailedRecognitionResult result, Rect faceRect) {
        // Only process attendance marking if:
        // 1. Result has valid student ID (not null, not empty)
        // 2. Result is actually recognized (decision.accepted() == true) OR confidence is very high (>= 0.6)
        // 3. Confidence is above minimum threshold (>= 0.4)
        // 4. Confidence is greater than 0 (to avoid zero-confidence false positives)
        boolean shouldProcessAttendance = false;
        
        if (result.getStudentId() != null && !result.getStudentId().isEmpty() && result.getConfidence() > 0) {
            // Only mark attendance if:
            // - Recognition was accepted by the decision engine, OR
            // - Confidence is high enough (>= 60%) to auto-mark
            if (result.isRecognized()) {
                // Recognition was accepted, process attendance if confidence >= 40%
                shouldProcessAttendance = result.getConfidence() >= 0.40;
            } else {
                // Recognition was not accepted, only process if confidence is very high (>= 60%)
                // This prevents false positives from low-quality matches
                shouldProcessAttendance = result.getConfidence() >= 0.60;
            }
        }
        
        if (shouldProcessAttendance) {
            String studentId = result.getStudentId();
            
            // Final validation: ensure studentId is valid
            if (studentId == null || studentId.isEmpty()) {
                AppLogger.warn("Attempted to process attendance with null/empty studentId");
                return;
            }
            
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
                        syncHandler.syncToSessionStudent(record);
                        
                        // Log that attendance was marked
                        AppLogger.info(String.format(
                            "Auto-marked attendance: Student=%s, Status=%s, Timestamp=%s, Method=%s, Confidence=%.2f, Recognized=%b",
                            studentId, record.getStatus(), record.getTimestamp(), record.getMarkingMethod(),
                            result.getConfidence(), result.isRecognized()));
                        
                        // Notify listener to update table
                        if (onAttendanceMarked != null) {
                            onAttendanceMarked.onMarked(studentId, record);
                        }
                    }
                } else {
                    AppLogger.warn("Student not found for ID: " + studentId);
                }
            } else if (record != null) {
                // Record exists but status is not PENDING - skip silently
                // (This is expected behavior when attendance is already marked)
            } else {
                // Record not found in map - this shouldn't happen for enrolled students
                AppLogger.warn("AttendanceRecord not found for studentId: " + studentId);
            }
        }
    }
    
    private List<Rect> detectFaces(Mat frame) {
        if (faceDetector == null) {
            return new ArrayList<>();
        }
        return faceDetector.detectFaces(frame);
    }
    
    // Callback interface for attendance marking events
    public interface AttendanceMarkedListener {
        void onMarked(String studentId, AttendanceRecord record);
    }
    
    private AttendanceMarkedListener onAttendanceMarked;
    
    public void setAttendanceMarkedListener(AttendanceMarkedListener listener) {
        this.onAttendanceMarked = listener;
    }
}

