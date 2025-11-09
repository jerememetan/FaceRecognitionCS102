package repository;

import entity.AttendanceRecord;
import java.util.List;

/**
 * Repository interface for AttendanceRecord persistence operations.
 */
public interface AttendanceRecordRepository extends Repository<AttendanceRecord> {
    
    /**
     * Find all attendance records for a specific session.
     * @param sessionId The session ID
     * @return List of attendance records for the session
     */
    List<AttendanceRecord> findBySessionId(String sessionId);
    
    /**
     * Find all attendance records for a specific student.
     * @param studentId The student ID
     * @return List of attendance records for the student
     */
    List<AttendanceRecord> findByStudentId(String studentId);
    
    /**
     * Find attendance record for a specific student in a specific session.
     * @param sessionId The session ID
     * @param studentId The student ID
     * @return The attendance record, or null if not found
     */
    AttendanceRecord findBySessionAndStudent(String sessionId, String studentId);
    
    /**
     * Update an existing attendance record.
     * @param record The attendance record to update
     * @return true if successful, false otherwise
     */
    boolean update(AttendanceRecord record);
}

