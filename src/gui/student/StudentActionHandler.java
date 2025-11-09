package gui.student;

import config.AppLogger;
import entity.Student;
import gui.detection.FaceCaptureDialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

import gui.FullScreenUtil;
import service.student.StudentManager;

public class StudentActionHandler implements ActionListener {
    private StudentEnrollmentGUI gui;
    private StudentManager studentManager;
    private StudentTableController tableController;
    private StudentSearchFilter searchFilter;

    public StudentActionHandler(StudentEnrollmentGUI gui, StudentManager studentManager,
                                StudentTableController tableController, StudentSearchFilter searchFilter) {
        this.gui = gui;
        this.studentManager = studentManager;
        this.tableController = tableController;
        this.searchFilter = searchFilter;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        switch (command) {
            case "Add Student":
                handleAddStudent();
                break;
            case "Edit Student":
                handleEditStudent();
                break;
            case "Delete Student":
                handleDeleteStudent();
                break;
            case "Capture Face":
                handleCaptureFace();
                break;
            case "Refresh":
                handleRefresh();
                break;
            case "Export CSV":
                handleExportCSV();
                break;
            case "Export Excel":
                handleExportExcel();
                break;
            case "Export PDF":
                handleExportPDF();
                break;
        }
    }

    private void handleAddStudent() {
        Student newStudent = new Student("", "", null, null); // Create empty student for dialog
        StudentDialog dialog = new StudentDialog(gui, "Add Student", newStudent, studentManager);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            tableController.refreshTable();
            searchFilter.applyFilter();
        }
    }

    private void handleEditStudent() {
        int selectedRow = gui.getStudentTable().getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(gui, "Please select a student to edit.");
            return;
        }
        Student selectedStudent = tableController.getStudentAt(selectedRow);
        if (selectedStudent != null) {
            StudentDialog dialog = new StudentDialog(gui, "Edit Student", selectedStudent, studentManager);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                tableController.refreshTable();
                searchFilter.applyFilter();
            }
        }
    }

    private void handleDeleteStudent() {
        int selectedRow = gui.getStudentTable().getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(gui, "Please select a student to delete.");
            return;
        }
        Student selectedStudent = tableController.getStudentAt(selectedRow);

        AppLogger.info("GUI: User attempting to delete student: " + selectedStudent.getStudentId() + " - " + selectedStudent.getName());

        int confirm = JOptionPane.showConfirmDialog(gui,
                "Are you sure you want to delete student: " + selectedStudent.getName() + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            AppLogger.info("GUI: User confirmed deletion, calling studentManager.deleteStudent() for ID: " + selectedStudent.getStudentId());
            boolean success = studentManager.deleteStudent(selectedStudent.getStudentId());
            AppLogger.info("GUI: studentManager.deleteStudent() returned: " + success);

            if (success) {
                JOptionPane.showMessageDialog(gui, "Student deleted successfully.");
                tableController.refreshTable();
                searchFilter.applyFilter();
            } else {
                JOptionPane.showMessageDialog(gui,
                    "Failed to delete student. Please try again.",
                    "Delete Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            AppLogger.info("GUI: User cancelled deletion for student: " + selectedStudent.getStudentId());
        }
    }

    private void handleCaptureFace() {
        int selectedRow = gui.getStudentTable().getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(gui, "Please select a student to capture face.");
            return;
        }
        Student selectedStudent = tableController.getStudentAt(selectedRow);
        FaceCaptureDialog dialog = new FaceCaptureDialog(gui, selectedStudent, studentManager);
        try {
            FullScreenUtil.enableFullScreen(dialog, FullScreenUtil.Mode.MAXIMIZED);
        }
        catch (Throwable t){
            
        }
        dialog.setVisible(true);
        if (dialog.isCaptureCompleted()) {
            tableController.refreshTable();
            searchFilter.applyFilter();
        }
    }

    private void handleRefresh() {
        tableController.refreshTable();
        searchFilter.applyFilter();
    }

    private void handleExportCSV() {
        // Implementation for CSV export
        JOptionPane.showMessageDialog(gui, "CSV export functionality to be implemented.");
    }

    private void handleExportExcel() {
        // Implementation for Excel export
        JOptionPane.showMessageDialog(gui, "Excel export functionality to be implemented.");
    }

    private void handleExportPDF() {
        // Implementation for PDF export
        JOptionPane.showMessageDialog(gui, "PDF export functionality to be implemented.");
    }
}






