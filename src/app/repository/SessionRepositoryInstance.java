package app.repository;

import java.sql.*;
import java.sql.Date;
import java.util.*;
import app.service.DBConnection;
import app.entity.Session;
import java.time.LocalDate;
import java.time.LocalTime;

public class SessionRepositoryInstance implements SessionRepository {
    @Override
    public boolean save(Session session) {
        String sql = "INSERT INTO sessions(id, sessionname, sessiondate, starttime, endtime, location, active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(session.getSessionId()));
            ps.setString(2, session.getName());
            ps.setDate(3, Date.valueOf(session.getDate()));
            ps.setTime(4, Time.valueOf(session.getStartTime()));
            ps.setTime(5, Time.valueOf(session.getEndTime()));
            ps.setString(6, session.getLocation());
            ps.setBoolean(7, false);
            ps.executeUpdate();
            System.out.println("Inserted session ID " + session.getSessionId() + " into database.");
            return true;

        } catch (SQLException e) {
            System.out.println("Error while inserting into sessions: " + e.getMessage());
            return false;
        }
    }
    @Override
    public boolean update(Session session) {
        String sql = "UPDATE sessions SET sessionname = ?, sessiondate = ?, starttime = ?, endtime = ?, location = ?, active = ? WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, session.getName());
            ps.setDate(2, Date.valueOf(session.getDate()));
            ps.setTime(3, Time.valueOf(session.getStartTime()));
            ps.setTime(4, Time.valueOf(session.getEndTime()));
            ps.setString(5, session.getLocation());
            ps.setBoolean(6, session.isActive());
            ps.setString(7, session.getSessionId());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while updating sessions: " + e.getMessage());
            return false;
        }
    }
    @Override
    public boolean delete(String sessionId) {
        String sql = "DELETE FROM sessions WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, sessionId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while deleting from sessions: " + e.getMessage());
            return false;
        }
    }

    @Override
    public ArrayList<Session> findAll() {
        String sql = "SELECT * FROM sessions";
        ArrayList<Session> sessions = new ArrayList<>();
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int sessionId = rs.getInt("id");
                String name = rs.getString("sessionName");
                LocalDate date = rs.getDate("sessionDate").toLocalDate();
                LocalTime startTime = rs.getTime("startTime").toLocalTime();
                LocalTime endTime = rs.getTime("endTime").toLocalTime();
                String location = rs.getString("location");
                boolean active = rs.getBoolean("active");

                Session session = new Session(Integer.toString(sessionId), name, date, startTime, endTime, location);
                if (active) {
                    session.open();
                } else {
                    session.close();
                }
                sessions.add(session);
            }
            return sessions;

        } catch (SQLException e) {
            System.out.println("Error while querying sessions: " + e.getMessage());
            return sessions;
        }
    }

    public Session findById(String sessionId) {
        String sql = "SELECT * FROM sessions WHERE id = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(sessionId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("sessionName");
                    LocalDate date = rs.getDate("sessionDate").toLocalDate();
                    LocalTime startTime = rs.getTime("startTime").toLocalTime();
                    LocalTime endTime = rs.getTime("endTime").toLocalTime();
                    String location = rs.getString("location");
                    boolean active = rs.getBoolean("active");

                    Session session = new Session(sessionId, name, date, startTime, endTime, location);
                    if (active) {
                        session.open();
                    } else {
                        session.close();
                    }
                    return session;
                } else {
                    return null;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error while querying sessions: " + e.getMessage());
            return null;
        }
    }
}
