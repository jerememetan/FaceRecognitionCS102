package service.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Student;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import javax.swing.JOptionPane;

/**
 * Automatic attendance marker using face recognition.
 * Marks attendance based on confidence levels:
 * - 60%+: Auto-mark PRESENT
 * - 40-60%: Prompt user for confirmation
 * - <40%: Ignore
 */
public class AutoMarker implements AttendanceMarker {
    
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.60;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.40;
    private static final int LATE_THRESHOLD_MINUTES = 15;
    
    private final RecognitionResult recognitionResult;
    
    /**
     * Result of face recognition containing student and confidence.
     */
    public static class RecognitionResult {
        private final Student student;
        private final double confidence;
        private final boolean recognized;
        
        public RecognitionResult(Student student, double confidence, boolean recognized) {
            this.student = student;
            this.confidence = confidence;
            this.recognized = recognized;
        }
        
        public Student getStudent() {
            return student;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public boolean isRecognized() {
            return recognized;
        }
    }
    
    public AutoMarker(RecognitionResult recognitionResult) {
        this.recognitionResult = recognitionResult;
    }
    
    @Override
    public boolean markAttendance(AttendanceRecord record) {
        if (record == null || recognitionResult == null) {
            return false;
        }
        
        // Allow marking even if isRecognized() is false, as long as we have a student and confidence
        // The confidence thresholds below will determine if we actually mark
        if (recognitionResult.getStudent() == null || recognitionResult.getConfidence() <= 0) {
            return false;
        }
        
        // Verify the recognized student matches the record's student
        if (!recognitionResult.getStudent().getStudentId().equals(record.getStudent().getStudentId())) {
            AppLogger.warn("Recognition result student ID mismatch: expected " + 
                          record.getStudent().getStudentId() + ", got " + 
                          recognitionResult.getStudent().getStudentId());
            return false;
        }
        
        double confidence = recognitionResult.getConfidence();
        LocalDateTime now = LocalDateTime.now();
        
        // High confidence (60%+): Auto-mark
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            return markAsPresentOrLate(record, now);
        }
        
        // Medium confidence (40-60%): Prompt for confirmation
        if (confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
            return promptForConfirmation(record, now);
        }
        
        // Low confidence (<40%): Ignore
        AppLogger.info("Recognition confidence too low (" + confidence + ") for " + 
                      record.getStudent().getStudentId() + ", ignoring.");
        return false;
    }
    
    private boolean markAsPresentOrLate(AttendanceRecord record, LocalDateTime timestamp) {
        AttendanceRecord.Status status = determineStatus(record, timestamp);
        record.setStatus(status);
        record.setTimestamp(timestamp);
        record.setMarkingMethod(AttendanceRecord.MarkingMethod.AUTOMATIC);
        record.setConfidence(recognitionResult.getConfidence()); // Record confidence level
        
        AppLogger.info("Auto-marked " + record.getStudent().getStudentId() + 
                      " as " + status + " at " + timestamp + " with confidence " + 
                      recognitionResult.getConfidence());
        return true;
    }
    
    private boolean promptForConfirmation(AttendanceRecord record, LocalDateTime timestamp) {
        Student student = record.getStudent();
        String message = String.format("Is this %s, %s?\nConfidence: %.1f%%", 
                                      student.getName(), 
                                      student.getStudentId(),
                                      recognitionResult.getConfidence() * 100);
        
        int choice = JOptionPane.showConfirmDialog(
            null,
            message,
            "Confirm Student Identity",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (choice == JOptionPane.YES_OPTION) {
            return markAsPresentOrLate(record, timestamp);
        }
        
        return false;
    }
    
    private AttendanceRecord.Status determineStatus(AttendanceRecord record, LocalDateTime timestamp) {
        if (record.getSession() == null || record.getSession().getStartTime() == null) {
            return AttendanceRecord.Status.PRESENT;
        }
        
        // Calculate minutes from session start time
        LocalDateTime sessionStart = LocalDateTime.of(
            record.getSession().getDate(),
            record.getSession().getStartTime()
        );
        
        long minutesLate = ChronoUnit.MINUTES.between(sessionStart, timestamp);
        
        if (minutesLate > LATE_THRESHOLD_MINUTES) {
            return AttendanceRecord.Status.LATE;
        }
        
        return AttendanceRecord.Status.PRESENT;
    }
}

