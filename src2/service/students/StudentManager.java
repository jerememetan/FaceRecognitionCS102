package app.service;

import app.entity.Student;
import app.model.FaceData;
import app.repository.StudentRepository;
import app.repository.StudentRepositoryInstance;
import ConfigurationAndLogging.AppLogger;

import java.util.List;
import java.util.ArrayList;

public class StudentManager {
    private StudentRepository studentRepository;
    // private FaceDetection faceDetection; // Temporarily commented out for testing

    public StudentManager() {
        this.studentRepository = new StudentRepositoryInstance();
        // this.faceDetection = new FaceDetection(); // Temporarily commented out for testing
    }

    private boolean validateStudentData(Student student) {
        if (student == null) {
            System.out.println("Student cannot be null");
            return false;
        }
        if (student.getStudentId() == null || student.getStudentId().trim().isEmpty()) {
            System.err.println("Student ID is required");
            return false;
        }

        if (student.getName() == null || student.getName().trim().isEmpty()) {
            System.err.println("Student name is required");
            return false;
        }

        if (!student.getStudentId().matches("^S\\d{5}$")) {
            System.err.println("Student ID must follow format S12345 (S followed by 5 digits)");
            return false;
        }

        return true;
    }

    public boolean enrollStudent(Student student) {
        try {
            if (!validateStudentData(student)) {
                return false;
            }

            if (studentRepository.existsByStudentId(student.getStudentId())) {
                System.out.println("Student ID already exists: " + student.getStudentId());
                return false;
            }

            boolean success = studentRepository.save(student);
            return success;

        } catch (Exception e) {
            System.out.println("Error enrolling student: " + e.getMessage());
        }
        return false;
    }

    public boolean updateStudent(Student student) {
        try {
            if (!validateStudentData(student)) {
                return false;
            }

            return studentRepository.update(student);

        } catch (Exception e) {
            System.err.println("Error updating student: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteStudent(String studentId) {
        AppLogger.info("Attempting to delete student with ID: " + studentId);
        try {
            Student student = studentRepository.findById(studentId);
            AppLogger.info("Found student for deletion: " + (student != null ? student.getName() + " (" + student.getStudentId() + ")" : "null"));

            if (student != null && student.getFaceData() != null) {
                AppLogger.info("Student has face data, deleting face data first");
                // faceDetection.deleteFaceData(student.getFaceData()); // Temporarily commented out for testing
            } else {
                AppLogger.info("Student has no face data to delete");
            }

            AppLogger.info("Calling repository.delete() for student ID: " + studentId);
            boolean result = studentRepository.delete(studentId);
            AppLogger.info("Repository delete operation completed. Result: " + result);

            if (result) {
                AppLogger.info("Student deletion successful for ID: " + studentId);
            } else {
                AppLogger.warn("Student deletion failed for ID: " + studentId + " - repository returned false");
            }

            return result;

        } catch (Exception e) {
            AppLogger.error("Error deleting student with ID: " + studentId, e);
            return false;
        }
    }

    public Student findStudentById(String studentId) {
        try {
            return studentRepository.findById(studentId);
        } catch (Exception e) {
            System.err.println("Error finding student: " + e.getMessage());
            return null;
        }
    }

    public List<Student> getAllStudents() {
        try {
            return studentRepository.findAll();
        } catch (Exception e) {
            System.err.println("Error getting all students: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Student> searchStudents(String searchTerm) {
        try {
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return getAllStudents();
            }
            return studentRepository.findByName(searchTerm);
        } catch (Exception e) {
            System.err.println("Error searching students: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean captureFaceImages(Student student, int numberOfImages) {
        try {
            if (numberOfImages < 10 || numberOfImages > 20) {
                System.err.println("Number of images must be between 10 and 20");
                return false;
            }

            boolean success = false; // faceDetection.captureAndStoreFaceImages(student, numberOfImages); // Temporarily commented out for testing

            if (success) {
                if (student.getFaceData().validateImages()) {
                    updateStudent(student);
                    System.out.println("Face images captured and validated for student: " + student.getName());
                    return true;
                } else {
                    System.err.println("Face validation failed - insufficient valid images");
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error capturing face images: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<Student> getStudentsWithFaceData() {
        List<Student> allStudents = getAllStudents();
        ArrayList<Student> studentsWithFaces = new ArrayList<>();

        for (Student student : allStudents) {
            if (student.getFaceData() != null && student.getFaceData().isValid()) {
                studentsWithFaces.add(student);
            }
        }

        return studentsWithFaces;
    }

    public List<Student> getStudentsWithoutFaceData() {
        List<Student> allStudents = getAllStudents();
        List<Student> studentsWithoutFaces = new ArrayList<>();

        for (Student student : allStudents) {
            if (student.getFaceData() == null || !student.getFaceData().isValid()) {
                studentsWithoutFaces.add(student);
            }
        }

        return studentsWithoutFaces;
    }

    public int getTotalStudentCount() {
        return getAllStudents().size();
    }

    public String getFaceEnrollmentStats() {
        List<Student> allStudents = getAllStudents();
        int totalStudents = allStudents.size();
        int studentsWithFaces = getStudentsWithFaceData().size();

        return String.format("Total Students: %d, With Face Data: %d, Without Face Data: %d",
                totalStudents, studentsWithFaces, (totalStudents - studentsWithFaces));
    }

    public void cleanup() {
        // if (faceDetection != null) {
        //     faceDetection.release();
        // } // Temporarily commented out for testing
    }
}
