package gui.attendance;

import config.AppLogger;
import entity.AttendanceRecord;
import service.attendance.ManualMarker;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Map;

/**
 * Manages the attendance table UI, including updates and user interactions.
 */
public class AttendanceTableController {
    
    private static final String[] COLUMNS = {"Student ID", "Name", "Status", "Timestamp", "Method", "Confidence", "Notes"};
    
    private final JTable attendanceTable;
    private final DefaultTableModel tableModel;
    private final Map<String, AttendanceRecord> recordMap;
    private final ManualMarker manualMarker;
    private final AttendanceRecordSyncHandler syncHandler;
    private boolean isUpdatingTable = false; // Flag to prevent recursive updates
    
    public AttendanceTableController(
            Map<String, AttendanceRecord> recordMap,
            ManualMarker manualMarker,
            AttendanceRecordSyncHandler syncHandler) {
        this.recordMap = recordMap;
        this.manualMarker = manualMarker;
        this.syncHandler = syncHandler;
        
        // Create table model
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Status column (2) and Notes column (6) are editable
                return column == 2 || column == 6;
            }
        };
        
        // Create table
        attendanceTable = new JTable(tableModel);
        attendanceTable.setRowHeight(25);
        attendanceTable.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        attendanceTable.getTableHeader().setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        
        // Make Status column a dropdown
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"PENDING", "PRESENT", "LATE", "ABSENT"});
        attendanceTable.getColumnModel().getColumn(2).setCellEditor(
            new javax.swing.DefaultCellEditor(statusCombo));
        
        // Make Notes column editable with a text field
        JTextField notesField = new JTextField();
        notesField.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        attendanceTable.getColumnModel().getColumn(6).setCellEditor(
            new javax.swing.DefaultCellEditor(notesField));
        
        // Set column widths
        attendanceTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Student ID
        attendanceTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Name
        attendanceTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Status
        attendanceTable.getColumnModel().getColumn(3).setPreferredWidth(180);  // Timestamp
        attendanceTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Method
        attendanceTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Confidence
        attendanceTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Notes
        
        // Add action listener for status and notes changes
        setupTableListener();
    }
    
    private void setupTableListener() {
        attendanceTable.getModel().addTableModelListener(e -> {
            // Ignore updates triggered by programmatic changes
            if (isUpdatingTable) {
                return;
            }
            
            int column = e.getColumn();
            int modelRow = e.getFirstRow();
            
            // Handle Status column changes
            if (column == 2) {
                handleStatusChange(modelRow);
            }
            
            // Handle Notes column changes
            if (column == 6) {
                handleNotesChange(modelRow);
            }
        });
    }
    
    private void handleStatusChange(int modelRow) {
        String studentId = (String) tableModel.getValueAt(modelRow, 0);
        String newStatus = (String) tableModel.getValueAt(modelRow, 2);
        
        AttendanceRecord record = recordMap.get(studentId);
        if (record != null) {
            // Check if status actually changed
            if (record.getStatus().toString().equals(newStatus)) {
                return; // No change, ignore
            }
            
            try {
                AttendanceRecord.Status status = AttendanceRecord.Status.valueOf(newStatus);
                
                // Only allow marking if status is not PENDING
                if (status == AttendanceRecord.Status.PENDING) {
                    AppLogger.warn("Cannot manually set status to PENDING");
                    // Revert the table cell to previous value
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        updateTableRow(modelRow, record);
                    });
                    return;
                }
                
                record.setStatus(status);
                if (manualMarker.markAttendance(record)) {
                    // Sync to SessionStudent
                    syncHandler.syncToSessionStudent(record);
                    
                    // Log that attendance was marked
                    AppLogger.info(String.format(
                        "Manually marked attendance: Student=%s, Status=%s, Timestamp=%s, Method=%s",
                        studentId, record.getStatus(), record.getTimestamp(), record.getMarkingMethod()));
                    
                    // Update table with model row index (this will update timestamp and method)
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        updateTableRow(modelRow, record);
                    });
                } else {
                    AppLogger.error("Failed to mark attendance for " + studentId);
                    // Revert the table cell to previous value
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        updateTableRow(modelRow, record);
                    });
                }
            } catch (IllegalArgumentException ex) {
                AppLogger.error("Invalid status: " + newStatus);
                // Revert the table cell to previous value
                javax.swing.SwingUtilities.invokeLater(() -> {
                    updateTableRow(modelRow, record);
                });
            }
        }
    }
    
    private void handleNotesChange(int modelRow) {
        String studentId = (String) tableModel.getValueAt(modelRow, 0);
        String newNotes = (String) tableModel.getValueAt(modelRow, 6);
        
        AttendanceRecord record = recordMap.get(studentId);
        if (record != null) {
            record.setNotes(newNotes != null ? newNotes : "");
            AppLogger.info("Updated notes for " + studentId + ": " + record.getNotes());
            
            // Sync to SessionStudent
            syncHandler.syncToSessionStudent(record);
        }
    }
    
    public JTable getTable() {
        return attendanceTable;
    }
    
    public DefaultTableModel getTableModel() {
        return tableModel;
    }
    
    /**
     * Refreshes the entire table with current attendance records.
     */
    public void refreshTable(List<AttendanceRecord> attendanceRecords) {
        tableModel.setRowCount(0);
        for (AttendanceRecord record : attendanceRecords) {
            addTableRow(record);
        }
    }
    
    /**
     * Adds a new row to the table.
     */
    private void addTableRow(AttendanceRecord record) {
        entity.Student student = record.getStudent();
        String confidenceStr = record.getConfidence() != null ? 
            String.format("%.1f%%", record.getConfidence() * 100) : "-";
        Object[] row = {
            student.getStudentId(),
            student.getName(),
            record.getStatus().toString(),
            record.getTimestamp() != null ? record.getTimestamp().toString() : "-",
            record.getMarkingMethod() != null ? record.getMarkingMethod().toString() : "-",
            confidenceStr,
            record.getNotes() != null ? record.getNotes() : ""
        };
        tableModel.addRow(row);
    }
    
    /**
     * Updates a table row with the current attendance record values.
     * @param modelRow The model row index (not view row index)
     * @param record The attendance record with updated values
     */
    public void updateTableRow(int modelRow, AttendanceRecord record) {
        // Ensure we're working with model row index
        if (modelRow < 0 || modelRow >= tableModel.getRowCount()) {
            AppLogger.warn("Invalid model row index: " + modelRow);
            return;
        }
        
        // Set flag to prevent recursive table model listener triggers
        isUpdatingTable = true;
        
        try {
            // Update all columns with current record values
            String statusStr = record.getStatus().toString();
            String timestampStr = record.getTimestamp() != null ? record.getTimestamp().toString() : "-";
            String methodStr = record.getMarkingMethod() != null ? record.getMarkingMethod().toString() : "-";
            String confidenceStr = record.getConfidence() != null ? 
                String.format("%.1f%%", record.getConfidence() * 100) : "-";
            String notesStr = record.getNotes() != null ? record.getNotes() : "";
            
            // Update Status column
            if (!statusStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 2)))) {
                tableModel.setValueAt(statusStr, modelRow, 2);
            }
            
            // Update Timestamp column
            if (!timestampStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 3)))) {
                tableModel.setValueAt(timestampStr, modelRow, 3);
            }
            
            // Update Method column
            if (!methodStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 4)))) {
                tableModel.setValueAt(methodStr, modelRow, 4);
            }
            
            // Update Confidence column
            if (!confidenceStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 5)))) {
                tableModel.setValueAt(confidenceStr, modelRow, 5);
            }
            
            // Update Notes column (only if not currently being edited by user)
            if (!notesStr.equals(String.valueOf(tableModel.getValueAt(modelRow, 6)))) {
                // Only update if the cell is not being edited
                if (attendanceTable.getEditingRow() != modelRow || attendanceTable.getEditingColumn() != 6) {
                    tableModel.setValueAt(notesStr, modelRow, 6);
                }
            }
            
            // Fire cell updated events to ensure UI refreshes
            tableModel.fireTableCellUpdated(modelRow, 2);
            tableModel.fireTableCellUpdated(modelRow, 3);
            tableModel.fireTableCellUpdated(modelRow, 4);
            tableModel.fireTableCellUpdated(modelRow, 5);
            if (attendanceTable.getEditingRow() != modelRow || attendanceTable.getEditingColumn() != 6) {
                tableModel.fireTableCellUpdated(modelRow, 6);
            }
            
            AppLogger.info(String.format("Updated table row %d: Status=%s, Timestamp=%s, Method=%s, Confidence=%s, Notes=%s",
                modelRow, record.getStatus(), record.getTimestamp(), record.getMarkingMethod(), 
                confidenceStr, notesStr));
        } finally {
            // Always reset flag
            isUpdatingTable = false;
        }
        
        // Force table repaint to ensure changes are visible
        javax.swing.SwingUtilities.invokeLater(() -> {
            attendanceTable.repaint();
        });
    }
    
    /**
     * Finds the model row index for a given student ID.
     * @param studentId The student ID to search for
     * @return The model row index, or -1 if not found
     */
    public int findRowByStudentId(String studentId) {
        // Search through model rows (not view rows) to find the correct index
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object cellValue = tableModel.getValueAt(i, 0);
            if (studentId.equals(cellValue)) {
                // Return model row index directly (updateTableRow expects model row)
                return i;
            }
        }
        AppLogger.warn("Could not find row for student ID: " + studentId);
        return -1;
    }
}

