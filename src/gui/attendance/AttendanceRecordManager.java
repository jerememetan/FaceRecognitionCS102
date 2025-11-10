package gui.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Session;
import entity.SessionStudent;
import entity.Student;
import repository.AttendanceRecordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages attendance record initialization, persistence, and synchronization.
 */
public class AttendanceRecordManager {
    
    private final Session session;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRecordSyncHandler syncHandler;
    private final List<AttendanceRecord> attendanceRecords;
    private final Map<String, AttendanceRecord> recordMap;
    
    public AttendanceRecordManager(
            Session session,
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceRecordSyncHandler syncHandler) {
        this.session = session;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.syncHandler = syncHandler;
        this.attendanceRecords = new ArrayList<>();
        this.recordMap = new HashMap<>();
    }
    
    /**
     * Initializes attendance records for all students in the session roster.
     * Loads existing records from database or creates new ones.
     */
    public void initializeRecords() {
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
                syncInitialStatusFromSessionStudent(record, sessionStudent);
                
                createdCount++;
                AppLogger.info("Created new attendance record for " + student.getStudentId());
            }
            
            attendanceRecords.add(record);
            recordMap.put(student.getStudentId(), record);
        }
        
        AppLogger.info("Initialized " + attendanceRecords.size() + " attendance records for session " + session.getSessionId() + 
                      " (Loaded: " + loadedCount + ", Created: " + createdCount + ")");
    }
    
    private void syncInitialStatusFromSessionStudent(AttendanceRecord record, SessionStudent sessionStudent) {
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
                             "' for " + record.getStudent().getStudentId() + ", using PENDING");
            }
        }
        
        // Sync notes from SessionStudent
        if (sessionStudent.getNotes() != null && !sessionStudent.getNotes().isEmpty()) {
            record.setNotes(sessionStudent.getNotes());
        }
    }
    
    /**
     * Marks all pending students as absent.
     * @return Number of students marked as absent
     */
    public int markPendingAsAbsent() {
        LocalDateTime now = LocalDateTime.now();
        int markedCount = 0;
        
        for (AttendanceRecord record : attendanceRecords) {
            if (record.getStatus() == AttendanceRecord.Status.PENDING) {
                record.setStatus(AttendanceRecord.Status.ABSENT);
                record.setTimestamp(now);
                record.setMarkingMethod(AttendanceRecord.MarkingMethod.AUTOMATIC);
                markedCount++;
                
                // Sync to SessionStudent
                syncHandler.syncToSessionStudent(record);
            }
        }
        
        if (markedCount > 0) {
            AppLogger.info("Auto-marked " + markedCount + " students as ABSENT at session end");
        }
        
        return markedCount;
    }
    
    /**
     * Persists all attendance records to the database.
     * Uses save or update based on whether the record already exists in the database.
     */
    public void persistAllRecords(javax.swing.table.DefaultTableModel tableModel) {
        int savedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;
        
        for (AttendanceRecord record : attendanceRecords) {
            try {
                // Ensure notes from table are synced to record before persisting
                syncNotesFromTable(record, tableModel);
                
                // Log what we're about to persist
                String notesToPersist = record.getNotes() != null ? record.getNotes() : "";
                AppLogger.info(String.format(
                    "Persisting attendance record - Student: %s, Status: %s, Notes: '%s'",
                    record.getStudent().getStudentId(),
                    record.getStatus(),
                    notesToPersist
                ));
                
                // Sync to SessionStudent before persisting
                syncHandler.syncToSessionStudent(record);
                
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
    
    private void syncNotesFromTable(AttendanceRecord record, javax.swing.table.DefaultTableModel tableModel) {
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
    }
    
    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords;
    }
    
    public Map<String, AttendanceRecord> getRecordMap() {
        return recordMap;
    }
}

