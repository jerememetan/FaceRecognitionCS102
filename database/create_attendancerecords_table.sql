-- SQL script to create the attendancerecords table
-- Run this script in your PostgreSQL database before using the attendance marking feature

CREATE TABLE IF NOT EXISTS attendancerecords (
    id SERIAL PRIMARY KEY,
    studentid VARCHAR(50) NOT NULL,
    sessionid INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP,
    markingmethod VARCHAR(20),
    confidence DOUBLE PRECISION,
    notes TEXT,
    
    -- Foreign key constraints
    CONSTRAINT fk_attendancerecord_student 
        FOREIGN KEY (studentid) 
        REFERENCES students(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_attendancerecord_session 
        FOREIGN KEY (sessionid) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE,
    
    -- Unique constraint: one attendance record per student per session
    CONSTRAINT uk_attendancerecord_student_session 
        UNIQUE (studentid, sessionid)
);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_attendancerecords_sessionid 
    ON attendancerecords(sessionid);

CREATE INDEX IF NOT EXISTS idx_attendancerecords_studentid 
    ON attendancerecords(studentid);

CREATE INDEX IF NOT EXISTS idx_attendancerecords_status 
    ON attendancerecords(status);

-- Add comments to columns
COMMENT ON TABLE attendancerecords IS 'Stores attendance records for students in sessions';
COMMENT ON COLUMN attendancerecords.id IS 'Primary key, auto-incrementing';
COMMENT ON COLUMN attendancerecords.studentid IS 'Foreign key to students table';
COMMENT ON COLUMN attendancerecords.sessionid IS 'Foreign key to sessions table';
COMMENT ON COLUMN attendancerecords.status IS 'Attendance status: PENDING, PRESENT, LATE, or ABSENT';
COMMENT ON COLUMN attendancerecords.timestamp IS 'When the attendance was marked';
COMMENT ON COLUMN attendancerecords.markingmethod IS 'How attendance was marked: AUTOMATIC or MANUAL';
COMMENT ON COLUMN attendancerecords.confidence IS 'Recognition confidence (0.0-1.0) for automatic marking';
COMMENT ON COLUMN attendancerecords.notes IS 'Optional notes or remarks';

