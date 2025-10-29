package app.repository;

import java.sql.*;
import java.util.*;
import app.service.DBConnection;
import app.entity.SessionStudent;
import app.entity.Session;
import app.entity.Student;

public class SessStuRepositoryInstance implements SessStuRepository {

    //base interface method not applicable for composite key relation
    @Override
    public boolean delete(String id) {
        throw new UnsupportedOperationException(
            "Use delete(SessionStudent sessStu) instead for composite key."
        );
    }

    //override method for composite key relations   
    @Override
    public boolean delete(SessionStudent sessStu) {
        String sql = "DELETE FROM sessionstudents WHERE sessionid = ? AND studentid = ?";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(sessStu.getSession().getSessionId()));
            ps.setString(2, sessStu.getStudent().getStudentId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Error while deleting from sessionstudents: " + e.getMessage());
            return false;
        }
        return true; 
    }

    @Override
    public boolean update(SessionStudent sessStu) {
        String sql = "UPDATE sessionstudents SET status = ?, notes = ? WHERE sessionid = ? AND studentid = ?";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, sessStu.getStatus());
            ps.setString(2, sessStu.getNotes());
            ps.setInt(3, Integer.parseInt(sessStu.getSession().getSessionId()));
            ps.setString(4, sessStu.getStudent().getStudentId());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while updating sessionstudents: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean save(SessionStudent sessStu) {
        String sql = "INSERT INTO sessionstudents(sessionid, studentid, status, notes) VALUES (?, ?, ?, ?)";
        try(
                Connection con = DBConnection.getConnection(); 
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(sessStu.getSession().getSessionId()));
            ps.setString(2, sessStu.getStudent().getStudentId());
            ps.setString(3, sessStu.getStatus());
            ps.setString(4, sessStu.getNotes());
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("Error while inserting into sessionstudents: " + e.getMessage());
            e.printStackTrace(System.err);
            System.out.flush();
            return false;
        }
    }

    //base interface method not applicable for composite key
    @Override
    public SessionStudent findById(String id) {
        throw new UnsupportedOperationException(
            "Use findById(int sessionId, int studentId) instead for composite key."
        );
    }


    //override method for composite key relations
    @Override
    public SessionStudent findById(int sessionId, String studentId) {
        String sql = "SELECT * FROM sessionstudents WHERE sessionid = ? AND studentid = ?";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, sessionId);
            ps.setString(2, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Session session = new SessionRepositoryInstance().findById(Integer.toString(sessionId));
                Student student = new StudentRepositoryInstance().findById(studentId);
                return new SessionStudent(session, student);
            }
        } catch (SQLException e) {
            System.out.println("Error while finding sessionstudent by ID: " + e.getMessage());
        }
        return null;
    }
    @Override
    public ArrayList<SessionStudent> findBySessionId(int sessionId) {
        ArrayList<SessionStudent> sessStuList = new ArrayList<>();
        String sql = "SELECT * FROM sessionstudents WHERE sessionid = ?";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Session session = new SessionRepositoryInstance().findById(Integer.toString(sessionId));
                Student student = new StudentRepositoryInstance().findById(rs.getString("studentid"));
                SessionStudent sessStu = new SessionStudent(session, student);
                sessStu.setStatus(rs.getString("status"));
                sessStu.setNotes(rs.getString("notes"));
                sessStuList.add(sessStu);
            }
        } catch (SQLException e) {
            System.out.println("Error while retrieving sessionstudents by session ID: " + e.getMessage());
        }
        return sessStuList;
    }
    @Override
    public ArrayList<SessionStudent> findAll() {
        ArrayList<SessionStudent> sessStuList = new ArrayList<>();
        String sql = "SELECT * FROM sessionstudents";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();) {
            while (rs.next()) {
                Session session = new SessionRepositoryInstance().findById(Integer.toString(rs.getInt("sessionid")));
                Student student = new StudentRepositoryInstance().findById(rs.getString("studentid"));
                SessionStudent sessStu = new SessionStudent(session, student);
                sessStu.setStatus(rs.getString("status"));
                sessStu.setNotes(rs.getString("notes"));
                sessStuList.add(sessStu);
            }
        } catch (SQLException e) {
            System.out.println("Error while retrieving all sessionstudents: " + e.getMessage());
        }
        return sessStuList;
    }
}
