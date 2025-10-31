package gui.student;

import entity.Student;
import java.util.List;
import service.student.StudentManager;

public class StudentStatistics {
    private StudentManager studentManager;

    public StudentStatistics(StudentManager studentManager) {
        this.studentManager = studentManager;
    }

    public String calculateStatistics() {
        List<Student> allStudents = studentManager.getAllStudents();
        int totalStudents = allStudents.size();
        int studentsWithFaces = 0;
        int totalImages = 0;
        for (Student student : allStudents) {
            if (student.getFaceData() != null && student.getFaceData().hasImages()) {
                studentsWithFaces++;
                totalImages += student.getFaceData().getImages().size();
            }
        }
        double completionRate = totalStudents > 0 ? (studentsWithFaces * 100.0) / totalStudents : 0;
        return String.format("📊 Total: %d students | ✅ With faces: %d (%.1f%%) | 🖼️ Total images: %d",
                totalStudents, studentsWithFaces, completionRate, totalImages);
    }
}






