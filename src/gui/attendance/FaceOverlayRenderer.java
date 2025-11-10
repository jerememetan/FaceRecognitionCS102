package gui.attendance;

import entity.Student;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import service.recognition.LiveRecognitionService;
import service.student.StudentManager;
import java.util.List;
import java.util.Map;

/**
 * Handles rendering face detection boxes and labels on video frames.
 */
public class FaceOverlayRenderer {
    
    private final StudentManager studentManager;
    private final Map<String, FaceRecognitionInfo> recognitionCache;
    
    public FaceOverlayRenderer(StudentManager studentManager, Map<String, FaceRecognitionInfo> recognitionCache) {
        this.studentManager = studentManager;
        this.recognitionCache = recognitionCache;
    }
    
    /**
     * Draws face detection boxes on a frame using cached recognition results.
     * 
     * @param frame The video frame to draw on
     * @param detectedFaces List of detected face rectangles
     * @param cacheTimeoutMs Cache timeout in milliseconds
     * @return Frame with face boxes and labels drawn
     */
    public Mat drawFrameWithBoxes(Mat frame, List<Rect> detectedFaces, long cacheTimeoutMs) {
        Mat frameWithBoxes = frame.clone();
        
        try {
            // Clean up old recognition cache entries
            long currentTime = System.currentTimeMillis();
            recognitionCache.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().timestamp > cacheTimeoutMs);
            
            // Draw boxes for all detected faces
            for (Rect faceRect : detectedFaces) {
                // Try to find matching recognition result in cache
                FaceRecognitionInfo recognitionInfo = findMatchingRecognition(faceRect);
                
                Scalar boxColor;
                String label;
                
                if (recognitionInfo != null) {
                    // Use cached recognition result (has name and confidence-based color)
                    boxColor = recognitionInfo.color;
                    label = recognitionInfo.label;
                } else {
                    // Draw gray box for detected but not yet recognized face
                    boxColor = new Scalar(128, 128, 128); // Gray
                    label = "Detecting...";
                }
                
                // Draw bounding box
                Imgproc.rectangle(frameWithBoxes, 
                    new Point(faceRect.x, faceRect.y),
                    new Point(faceRect.x + faceRect.width, faceRect.y + faceRect.height),
                    boxColor, 3);
                
                // Draw label text above the box
                Point textPosition = new Point(faceRect.x, Math.max(25, faceRect.y - 10));
                Imgproc.putText(frameWithBoxes, label,
                    textPosition,
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, boxColor, 2);
            }
        } catch (Exception e) {
            config.AppLogger.error("Error drawing face detection boxes: " + e.getMessage(), e);
        }
        
        return frameWithBoxes;
    }
    
    /**
     * Finds a matching recognition result for a face rectangle.
     * Uses distance-based matching to find the closest recognition result.
     */
    private FaceRecognitionInfo findMatchingRecognition(Rect faceRect) {
        int faceCenterX = faceRect.x + faceRect.width / 2;
        int faceCenterY = faceRect.y + faceRect.height / 2;
        
        FaceRecognitionInfo bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (FaceRecognitionInfo info : recognitionCache.values()) {
            int infoCenterX = info.faceRect.x + info.faceRect.width / 2;
            int infoCenterY = info.faceRect.y + info.faceRect.height / 2;
            
            int distance = (int) Math.sqrt(
                Math.pow(faceCenterX - infoCenterX, 2) + 
                Math.pow(faceCenterY - infoCenterY, 2)
            );
            
            // If faces are close (within 80 pixels), consider it a match
            if (distance < 80 && distance < minDistance) {
                minDistance = distance;
                bestMatch = info;
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Returns color based on confidence level:
     * - Green: confidence >= 60%
     * - Orange: confidence 40-60%
     * - Red: confidence < 40%
     */
    public static Scalar getColorForConfidence(double confidence) {
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
     */
    public String getDisplayLabel(LiveRecognitionService.DetailedRecognitionResult result) {
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
    
    /**
     * Helper class to store recognition info for a face.
     */
    public static class FaceRecognitionInfo {
        public final Rect faceRect;
        public final Scalar color;
        public final String label;
        public final long timestamp; // When this recognition was done
        
        public FaceRecognitionInfo(Rect faceRect, Scalar color, String label) {
            this.faceRect = faceRect;
            this.color = color;
            this.label = label;
            this.timestamp = System.currentTimeMillis();
        }
    }
}

