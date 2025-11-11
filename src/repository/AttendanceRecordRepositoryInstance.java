package repository;

import config.AppLogger;
import entity.AttendanceRecord;
import entity.Session;
import entity.Student;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AttendanceRecordRepository for database persistence.
 */
public class AttendanceRecordRepositoryInstance implements AttendanceRecordRepository {
    
    @Override
    public boolean save(AttendanceRecord record) {
        if (record == null || record.getStudent() == null || record.getSession() == null) {
            AppLogger.error("Cannot save attendance record: record, student, or session is null");
            return false;
        }
        
        String sql = "INSERT INTO attendancerecords " +
                     "(studentid, sessionid, status, timestamp, markingmethod, confidence, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, record.getStudent().getStudentId());
            ps.setInt(2, Integer.parseInt(record.getSession().getSessionId()));
            ps.setString(3, record.getStatus().toString());
            
            if (record.getTimestamp() != null) {
                ps.setTimestamp(4, Timestamp.valueOf(record.getTimestamp()));
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }
            
            if (record.getMarkingMethod() != null) {
                ps.setString(5, record.getMarkingMethod().toString());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            
            if (record.getConfidence() != null) {
                ps.setDouble(6, record.getConfidence());
            } else {
                ps.setNull(6, Types.DOUBLE);
            }
            
            String notes = record.getNotes() != null ? record.getNotes() : "";
            ps.setString(7, notes);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                AppLogger.info(String.format(
                    "Saved attendance record for student %s in session %s - Notes: '%s'",
                    record.getStudent().getStudentId(),
                    record.getSession().getSessionId(),
                    notes
                ));
                return true;
            }
            return false;
            
        } catch (SQLException e) {
            AppLogger.error("Error while inserting attendance record: " + e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean update(AttendanceRecord record) {
        if (record == null || record.getStudent() == null || record.getSession() == null) {
            AppLogger.error("Cannot update attendance record: record, student, or session is null");
            return false;
        }
        
        String sql = "UPDATE attendancerecords SET " +
                     "status = ?, timestamp = ?, markingmethod = ?, confidence = ?, notes = ? " +
                     "WHERE studentid = ? AND sessionid = ?";
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, record.getStatus().toString());
            
            if (record.getTimestamp() != null) {
                ps.setTimestamp(2, Timestamp.valueOf(record.getTimestamp()));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            
            if (record.getMarkingMethod() != null) {
                ps.setString(3, record.getMarkingMethod().toString());
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            
            if (record.getConfidence() != null) {
                ps.setDouble(4, record.getConfidence());
            } else {
                ps.setNull(4, Types.DOUBLE);
            }
            
            String notes = record.getNotes() != null ? record.getNotes() : "";
            ps.setString(5, notes);
            ps.setString(6, record.getStudent().getStudentId());
            ps.setInt(7, Integer.parseInt(record.getSession().getSessionId()));
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                AppLogger.info(String.format(
                    "Updated attendance record for student %s in session %s - Notes: '%s'",
                    record.getStudent().getStudentId(),
                    record.getSession().getSessionId(),
                    notes
                ));
                return true;
            }
            return false;
            
        } catch (SQLException e) {
            AppLogger.error("Error while updating attendance record: " + e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean delete(String id) {
        // For attendance records, we typically delete by composite key
        // This method might not be used, but we'll implement it for interface compliance
        String sql = "DELETE FROM attendancerecords WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(id));
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            AppLogger.error("Error while deleting attendance record: " + e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public AttendanceRecord findById(String id) {
        String sql = "SELECT * FROM attendancerecords WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(id));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAttendanceRecord(rs);
                }
            }
        } catch (SQLException e) {
            AppLogger.error("Error while finding attendance record by ID: " + e.getMessage(), e);
        }
        return null;
    }
    
    @Override
    public List<AttendanceRecord> findAll() {
        List<AttendanceRecord> records = new ArrayList<>();
        // Try optimized JOIN query first (much faster than N+1 queries)
        try {
            String sql = "SELECT ar.id, ar.studentid, ar.sessionid, ar.status, ar.timestamp, " +
                         "ar.markingmethod, ar.confidence, ar.notes, " +
                         "s.id as s_id, s.name as student_name, " +
                         "sess.id as sess_id, sess.name as session_name, sess.date as session_date, " +
                         "sess.starttime as session_starttime, sess.endtime as session_endtime, " +
                         "sess.location as session_location " +
                         "FROM attendancerecords ar " +
                         "LEFT JOIN students s ON ar.studentid = s.id " +
                         "LEFT JOIN sessions sess ON ar.sessionid = sess.id " +
                         "ORDER BY ar.timestamp DESC";
            
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    AttendanceRecord record = mapResultSetToAttendanceRecordWithJoins(rs);
                    if (record != null) {
                        records.add(record);
                    }
                }
                
                AppLogger.info("Successfully loaded " + records.size() + " attendance records using JOIN query");
                return records;
            }
        } catch (SQLException e) {
            AppLogger.error("Error with JOIN query, falling back to original method: " + e.getMessage(), e);
            // Fall back to original method if JOIN fails
        }
        
        // Fallback: Use original method (slower but more reliable)
        String sql = "SELECT * FROM attendancerecords";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                AttendanceRecord record = mapResultSetToAttendanceRecord(rs);
                if (record != null) {
                    records.add(record);
                }
            }
            AppLogger.info("Loaded " + records.size() + " attendance records using fallback method");
        } catch (SQLException e) {
            AppLogger.error("Error while retrieving all attendance records: " + e.getMessage(), e);
        }
        return records;
    }
    
    @Override
    public List<AttendanceRecord> findBySessionId(String sessionId) {
        List<AttendanceRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM attendancerecords WHERE sessionid = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(sessionId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceRecord record = mapResultSetToAttendanceRecord(rs);
                    if (record != null) {
                        records.add(record);
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.error("Error while finding attendance records by session ID: " + e.getMessage(), e);
        }
        return records;
    }
    
    @Override
    public List<AttendanceRecord> findByStudentId(String studentId) {
        List<AttendanceRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM attendancerecords WHERE studentid = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceRecord record = mapResultSetToAttendanceRecord(rs);
                    if (record != null) {
                        records.add(record);
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.error("Error while finding attendance records by student ID: " + e.getMessage(), e);
        }
        return records;
    }
    
    @Override
    public AttendanceRecord findBySessionAndStudent(String sessionId, String studentId) {
        String sql = "SELECT * FROM attendancerecords WHERE sessionid = ? AND studentid = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(sessionId));
            ps.setString(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAttendanceRecord(rs);
                }
            }
        } catch (SQLException e) {
            AppLogger.error("Error while finding attendance record by session and student: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Maps a ResultSet row to an AttendanceRecord object.
     */
    private AttendanceRecord mapResultSetToAttendanceRecord(ResultSet rs) throws SQLException {
        try {
            String studentId = rs.getString("studentid");
            String sessionId = String.valueOf(rs.getInt("sessionid"));
            
            // Load student and session from their repositories
            StudentRepositoryInstance studentRepo = new StudentRepositoryInstance();
            SessionRepositoryInstance sessionRepo = new SessionRepositoryInstance();
            
            Student student = studentRepo.findById(studentId);
            Session session = sessionRepo.findById(sessionId);
            
            if (student == null || session == null) {
                AppLogger.warn("Could not load student or session for attendance record");
                return null;
            }
            
            AttendanceRecord record = new AttendanceRecord(student, session);
            
            // Set status
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                try {
                    record.setStatus(AttendanceRecord.Status.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    AppLogger.warn("Invalid status value: " + statusStr);
                }
            }
            
            // Set timestamp
            Timestamp timestamp = rs.getTimestamp("timestamp");
            if (timestamp != null) {
                record.setTimestamp(timestamp.toLocalDateTime());
            }
            
            // Set marking method
            String methodStr = rs.getString("markingmethod");
            if (methodStr != null) {
                try {
                    record.setMarkingMethod(AttendanceRecord.MarkingMethod.valueOf(methodStr));
                } catch (IllegalArgumentException e) {
                    AppLogger.warn("Invalid marking method value: " + methodStr);
                }
            }
            
            // Set confidence
            double confidence = rs.getDouble("confidence");
            if (!rs.wasNull()) {
                record.setConfidence(confidence);
            }
            
            // Set notes
            String notes = rs.getString("notes");
            if (notes != null) {
                record.setNotes(notes);
            }
            
            return record;
            
        } catch (SQLException e) {
            AppLogger.error("Error mapping ResultSet to AttendanceRecord: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Maps a ResultSet row to an AttendanceRecord object using JOIN data.
     * This is much faster than making separate queries for each student and session.
     * Used by findAll() to avoid N+1 query problem.
     */
    private AttendanceRecord mapResultSetToAttendanceRecordWithJoins(ResultSet rs) throws SQLException {
        try {
            // Extract student data from JOIN
            String studentId = rs.getString("studentid");
            String studentName = rs.getString("student_name");
            
            // If JOIN didn't return student name, fall back to loading from repository
            Student student;
            if (studentId == null) {
                AppLogger.warn("Student ID is null in attendance record, skipping");
                return null;
            }
            
            if (studentName == null) {
                // Fall back to loading student from repository
                AppLogger.warn("Student name not found in JOIN for studentId: " + studentId + ", loading from repository");
                StudentRepositoryInstance studentRepo = new StudentRepositoryInstance();
                student = studentRepo.findById(studentId);
                if (student == null) {
                    AppLogger.warn("Could not load student from repository for ID: " + studentId);
                    return null;
                }
            } else {
                // Create student object from JOIN data (avoiding separate query)
                student = new Student(studentId, studentName);
            }
            
            // Extract session data from JOIN
            int sessionIdInt = rs.getInt("sessionid");
            String sessionId = String.valueOf(sessionIdInt);
            String sessionName = rs.getString("session_name");
            java.sql.Date sessionDate = rs.getDate("session_date");
            java.sql.Time sessionStartTime = rs.getTime("session_starttime");
            java.sql.Time sessionEndTime = rs.getTime("session_endtime");
            
            // If JOIN didn't return session name, fall back to loading from repository
            Session session;
            if (sessionName == null) {
                // Fall back to loading session from repository
                AppLogger.warn("Session name not found in JOIN for sessionId: " + sessionId + ", loading from repository");
                SessionRepositoryInstance sessionRepo = new SessionRepositoryInstance();
                session = sessionRepo.findById(sessionId);
                if (session == null) {
                    AppLogger.warn("Could not load session from repository for ID: " + sessionId);
                    return null;
                }
            } else {
                // Create session object from JOIN data (avoiding separate query)
                // Session constructor: (String nextId, String name, LocalDate date, LocalTime startTime, LocalTime endTime, String location)
                java.time.LocalDate date = sessionDate != null ? sessionDate.toLocalDate() : java.time.LocalDate.now();
                java.time.LocalTime startTime = sessionStartTime != null ? sessionStartTime.toLocalTime() : java.time.LocalTime.now();
                java.time.LocalTime endTime = sessionEndTime != null ? sessionEndTime.toLocalTime() : java.time.LocalTime.now();
                String location = rs.getString("session_location");
                if (location == null) {
                    location = ""; // Default to empty string if location is null
                }
                
                session = new Session(sessionId, sessionName, date, startTime, endTime, location);
            }
            
            AttendanceRecord record = new AttendanceRecord(student, session);
            
            // Set status
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                try {
                    record.setStatus(AttendanceRecord.Status.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    AppLogger.warn("Invalid status value: " + statusStr);
                }
            }
            
            // Set timestamp
            Timestamp timestamp = rs.getTimestamp("timestamp");
            if (timestamp != null) {
                record.setTimestamp(timestamp.toLocalDateTime());
            }
            
            // Set marking method
            String methodStr = rs.getString("markingmethod");
            if (methodStr != null) {
                try {
                    record.setMarkingMethod(AttendanceRecord.MarkingMethod.valueOf(methodStr));
                } catch (IllegalArgumentException e) {
                    AppLogger.warn("Invalid marking method value: " + methodStr);
                }
            }
            
            // Set confidence
            double confidence = rs.getDouble("confidence");
            if (!rs.wasNull()) {
                record.setConfidence(confidence);
            }
            
            // Set notes
            String notes = rs.getString("notes");
            if (notes != null) {
                record.setNotes(notes);
            }
            
            return record;
            
        } catch (SQLException e) {
            AppLogger.error("Error mapping ResultSet to AttendanceRecord with JOINs: " + e.getMessage(), e);
            throw e;
        }
    }
}

