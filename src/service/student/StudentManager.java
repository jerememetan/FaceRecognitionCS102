package service.student;

import config.AppLogger;
import entity.Student;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import model.FaceData;
import repository.StudentRepository;
import repository.StudentRepositoryInstance;

public class StudentManager {
    private StudentRepository studentRepository;
    // private FaceDetection faceDetection; // Temporarily commented out for testing

    public StudentManager() {
        this.studentRepository = new StudentRepositoryInstance();
        // this.faceDetection = new FaceDetection(); // Temporarily commented out for testing
    }

    private boolean validateStudentData(Student student) {
        if (student == null) {
            AppLogger.info("Student cannot be null");
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
                AppLogger.info("Student ID already exists: " + student.getStudentId());
                return false;
            }

            boolean success = studentRepository.save(student);
            return success;

        } catch (Exception e) {
            AppLogger.info("Error enrolling student: " + e.getMessage());
        }
        return false;
    }

    public boolean updateStudent(Student student) {
        try {
            if (!validateStudentData(student)) {
                return false;
            }

            Student existingStudent = studentRepository.findById(student.getStudentId());
            if (existingStudent == null) {
                AppLogger.warn("Cannot update student; no existing record for ID: " + student.getStudentId());
                return false;
            }

            FaceData oldFaceData = existingStudent.getFaceData();
            FaceData newFaceData = student.getFaceData();
            if (newFaceData == null) {
                newFaceData = new FaceData(student.getStudentId(), student.getName());
                student.setFaceData(newFaceData);
            }

            Path oldPath = getFaceDataPath(oldFaceData);
            Path newPath = getFaceDataPath(newFaceData);
            boolean renameNeeded = oldPath != null && newPath != null && !pathsEquivalent(oldPath, newPath);

            if (renameNeeded) {
                if (!moveFaceDataDirectory(oldPath, newPath, student.getStudentId())) {
                    AppLogger.error("Aborting update; failed to relocate face data for student " + student.getStudentId());
                    return false;
                }

                // Refresh face data reference so it points at the relocated folder and loads existing images
                student.setFaceData(new FaceData(student.getStudentId(), student.getName()));
            }

            boolean updated = studentRepository.update(student);
            if (!updated && renameNeeded) {
                // Attempt to move the face data back to its original location to keep filesystem consistent
                if (!moveFaceDataDirectory(newPath, oldPath, student.getStudentId())) {
                    AppLogger.error("Failed to restore face data directory after unsuccessful update for student " + student.getStudentId());
                }
            }

            return updated;

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
                boolean faceDataDeleted = deleteFaceDataDirectory(student != null ? student.getFaceData() : null, studentId);
                if (!faceDataDeleted) {
                    AppLogger.warn("Student deletion completed in database but face data directory could not be removed for ID: " + studentId);
                    return false;
                }
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
                    AppLogger.info("Face images captured and validated for student: " + student.getName());
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

    private Path getFaceDataPath(FaceData faceData) {
        if (faceData == null) {
            return null;
        }
        try {
            String folderPath = faceData.getFolderPath();
            if (folderPath == null || folderPath.trim().isEmpty()) {
                return null;
            }
            return Paths.get(folderPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            AppLogger.warn("Unable to resolve face data path: " + e.getMessage());
            return null;
        }
    }

    private boolean pathsEquivalent(Path first, Path second) {
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(first.normalize(), second.normalize());
    }

    private boolean moveFaceDataDirectory(Path source, Path target, String studentId) {
        if (source == null || target == null) {
            return true;
        }

        if (!Files.exists(source)) {
            AppLogger.info("No existing face data directory to move for student " + studentId + " (source: " + source + ")");
            return true;
        }

        if (pathsEquivalent(source, target)) {
            return true;
        }

        try {
            if (Files.exists(target)) {
                AppLogger.warn("Target face data directory already exists for student " + studentId + ": " + target);
                return false;
            }

            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }

            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                // Fallback for filesystems that do not support atomic move
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            AppLogger.info("Moved face data directory for student " + studentId + " from " + source + " to " + target);
            return true;
        } catch (IOException moveException) {
            AppLogger.error("Failed to move face data directory for student " + studentId + ": " + moveException.getMessage(), moveException);
            return false;
        }
    }

    private boolean deleteFaceDataDirectory(FaceData faceData, String studentId) {
        Path folder = getFaceDataPath(faceData);
        if (folder == null) {
            AppLogger.info("No face data directory configured for student " + studentId);
            return true;
        }

        if (!Files.exists(folder)) {
            AppLogger.info("Face data directory already absent for student " + studentId + ": " + folder);
            return true;
        }

        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException deletionException) {
                    throw new RuntimeException(deletionException);
                }
            });
            AppLogger.info("Deleted face data directory for student " + studentId + ": " + folder);
            return true;
        } catch (RuntimeException | IOException deletionFailure) {
            Throwable cause = deletionFailure instanceof RuntimeException && deletionFailure.getCause() != null
                ? deletionFailure.getCause()
                : deletionFailure;
            AppLogger.error("Failed to delete face data directory for student " + studentId + ": " + cause.getMessage(), cause);
            return false;
        }
    }
}







