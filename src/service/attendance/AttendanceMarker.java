package service.attendance;

import entity.AttendanceRecord;

/**
 * Interface for polymorphic attendance marking.
 * Implementations handle automatic (face recognition) or manual marking.
 */
public interface AttendanceMarker {
    /**
     * Marks attendance for a student.
     * 
     * @param record The attendance record to mark
     * @return true if attendance was successfully marked, false otherwise
     */
    boolean markAttendance(AttendanceRecord record);
}

