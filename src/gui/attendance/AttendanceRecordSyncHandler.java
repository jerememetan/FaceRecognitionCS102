package gui.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Session;
import entity.SessionStudent;
import repository.SessStuRepositoryInstance;

/**
 * Handles synchronization between AttendanceRecord and SessionStudent entities.
 */
public class AttendanceRecordSyncHandler {
    
    private final Session session;
    private final SessStuRepositoryInstance sessStuRepository;
    
    public AttendanceRecordSyncHandler(Session session, SessStuRepositoryInstance sessStuRepository) {
        this.session = session;
        this.sessStuRepository = sessStuRepository;
    }
    
    /**
     * Synchronizes AttendanceRecord status and notes to the corresponding SessionStudent.
     * Updates both the in-memory SessionStudent object and the database.
     * 
     * @param record The AttendanceRecord to sync
     */
    public void syncToSessionStudent(AttendanceRecord record) {
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
}

