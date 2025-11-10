package service.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import java.time.LocalDateTime;

/**
 * Manual attendance marker for updating attendance through UI interactions.
 */
public class ManualMarker implements AttendanceMarker {
    
    @Override
    public boolean markAttendance(AttendanceRecord record) {
        if (record == null) {
            return false;
        }
        
        // For manual marking, the status should already be set by the UI
        // We just need to set the timestamp and marking method
        if (record.getStatus() == AttendanceRecord.Status.PENDING) {
            AppLogger.warn("Cannot manually mark attendance with PENDING status");
            return false;
        }
        
        // Always update timestamp to current time when status is changed manually
        record.setTimestamp(LocalDateTime.now());
        record.setMarkingMethod(AttendanceRecord.MarkingMethod.MANUAL);
        
        AppLogger.info("Manually marked " + record.getStudent().getStudentId() + 
                      " as " + record.getStatus() + " at " + record.getTimestamp());
        return true;
    }
    
    /**
     * Marks attendance with a specific status.
     * 
     * @param record The attendance record
     * @param status The status to set
     * @return true if successful
     */
    public boolean markAttendance(AttendanceRecord record, AttendanceRecord.Status status) {
        if (record == null || status == null) {
            return false;
        }
        
        record.setStatus(status);
        return markAttendance(record);
    }
}

