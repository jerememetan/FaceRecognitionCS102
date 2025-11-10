package entity;

import java.time.LocalDateTime;

/**
 * Encapsulates attendance information for a student in a session.
 */
public class AttendanceRecord {
    public enum Status {
        PENDING,
        PRESENT,
        LATE,
        ABSENT
    }
    
    public enum MarkingMethod {
        AUTOMATIC,
        MANUAL
    }
    
    private Student student;
    private Session session;
    private Status status;
    private LocalDateTime timestamp;
    private MarkingMethod markingMethod;
    private Double confidence; // Confidence level when marked automatically (0.0-1.0)
    private String notes; // Notes/remarks (e.g., reason for absence or late)
    
    public AttendanceRecord(Student student, Session session) {
        this.student = student;
        this.session = session;
        this.status = Status.PENDING;
        this.timestamp = null;
        this.markingMethod = null;
        this.confidence = null;
        this.notes = "";
    }
    
    public AttendanceRecord(Student student, Session session, Status status, 
                           LocalDateTime timestamp, MarkingMethod markingMethod) {
        this.student = student;
        this.session = session;
        this.status = status;
        this.timestamp = timestamp;
        this.markingMethod = markingMethod;
        this.confidence = null;
        this.notes = "";
    }
    
    // Getters and Setters
    public Student getStudent() {
        return student;
    }
    
    public void setStudent(Student student) {
        this.student = student;
    }
    
    public Session getSession() {
        return session;
    }
    
    public void setSession(Session session) {
        this.session = session;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public MarkingMethod getMarkingMethod() {
        return markingMethod;
    }
    
    public void setMarkingMethod(MarkingMethod markingMethod) {
        this.markingMethod = markingMethod;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public String getNotes() {
        return notes != null ? notes : "";
    }
    
    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
    }
    
    @Override
    public String toString() {
        return String.format("AttendanceRecord{student=%s, session=%s, status=%s, timestamp=%s, method=%s, confidence=%.2f, notes=%s}",
                student != null ? student.getStudentId() : "null",
                session != null ? session.getSessionId() : "null",
                status,
                timestamp,
                markingMethod,
                confidence != null ? confidence : 0.0,
                notes != null ? notes : "");
    }
}

