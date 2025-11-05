package repository;

import config.AppLogger;
import entity.Student;
import java.sql.*;
import java.util.*;
import config.*;
public class StudentRepositoryInstance implements StudentRepository {

    @Override
    public boolean save(Student student) {
        String sql = "INSERT INTO students(id, name, email, phone) VALUES (?, ?, ?, ?)";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, student.getStudentId());
            ps.setString(2, student.getName());
            ps.setString(3, student.getEmail());
            ps.setString(4, student.getPhone());
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            AppLogger.error("Error while inserting into students: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean update(Student student) {
        String sql = "UPDATE students SET name = ?, email = ?, phone = ? WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(4, student.getStudentId());
            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPhone());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            AppLogger.error("Error while updating students: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(String studentId) {
        AppLogger.info("Repository: Attempting to delete student with ID: " + studentId);
        String sql = "DELETE FROM students WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, studentId);
            int rowsAffected = ps.executeUpdate();
            AppLogger.info("Repository: Delete SQL executed for student ID " + studentId + ", rows affected: " + rowsAffected);
            return rowsAffected > 0;

        } catch (SQLException e) {
            AppLogger.error("Repository: Error deleting student with ID " + studentId + " from database", e);
            return false;
        }
    }

    @Override
    public Student findById(String studentId) {
        String sql = "SELECT * FROM students WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");

                return new Student(studentId, name, email, phone);
            }

        } catch (SQLException e) {
            AppLogger.error("Error while finding from students: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Student> findByName(String searchName) {
        ArrayList<Student> results = new ArrayList<>();
        String sql = "SELECT * FROM students WHERE name LIKE ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, "%" + searchName + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String studentId = rs.getString("id");

                results.add(new Student(studentId, name, email, phone));
            }

        } catch (SQLException e) {
            AppLogger.error("Error while searching from students: " + e.getMessage());
        }
        return results;
    }

    @Override
    public boolean existsByStudentId(String studentId) {
        String sql = "SELECT 1 FROM students WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }

        } catch (SQLException e) {
            AppLogger.error("Error while checking student existence: " + e.getMessage());
        }
        return false;
    }

    @Override
    public ArrayList<Student> findAll() {
        ArrayList<Student> results = new ArrayList<>();
        String sql = "SELECT * FROM students";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String studentId = rs.getString("id");

                results.add(new Student(studentId, name, email, phone));
            }

        } catch (SQLException e) {
            AppLogger.error("Error while getting all students: " + e.getMessage());
        }
        return results;
    }
}







