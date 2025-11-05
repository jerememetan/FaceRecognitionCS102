package repository;

import java.sql.*;
import java.util.*;
import entity.Roster;
import java.time.*;

public class RosterRepositoryInstance implements RosterRepository {
    @Override
    public boolean delete(String rosterId) {
        String sql = "DELETE FROM rosters WHERE rosterid = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(rosterId));
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while deleting from rosters: " + e.getMessage());
            return false;
        }
    }
    @Override
    public boolean update(Roster roster) {
        String sql = "UPDATE rosters SET coursecode = ?, starttime = ? , endtime = ? , location = ? WHERE rosterid = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setString(1, roster.getCourseCode());
            ps.setTime(2, Time.valueOf(roster.getStartTime()));
            ps.setTime(3, Time.valueOf(roster.getEndTime()));
            ps.setString(4, roster.getLocation());
            ps.setInt(5, Integer.parseInt(roster.getRosterId()));
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while updating rosters: " + e.getMessage());
            return false;
        }
    }
    @Override
    public boolean save(Roster roster) {
        String sql = "INSERT INTO rosters (rosterid, coursecode, location, starttime, endtime) VALUES (?, ?, ?, ?, ?)";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(roster.getRosterId()));
            ps.setString(2, roster.getCourseCode());
            ps.setString(3, roster.getLocation());
            ps.setTime(4, Time.valueOf(roster.getStartTime()));
            ps.setTime(5, Time.valueOf(roster.getEndTime()));
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error while saving to rosters: " + e.getMessage());
            return false;
        }
    }
    @Override
    public Roster findById(String rosterId) {
        String sql = "SELECT * FROM rosters WHERE rosterid = ?";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(rosterId));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String courseCode = rs.getString("coursecode");
                String location = rs.getString("location");
                LocalTime startTime = rs.getTime("starttime").toLocalTime();
                LocalTime endTime = rs.getTime("endtime").toLocalTime();
                return new Roster(rosterId, courseCode, startTime, endTime, location);
            }
        } catch (SQLException e) {
            System.out.println("Error while finding roster by ID: " + e.getMessage());
        }
        return null;
    }
    @Override
    public List<Roster> findAll() {
        List<Roster> rosters = new ArrayList<>();
        String sql = "SELECT * FROM rosters";
        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery();) {
            while (rs.next()) {
                String rosterId = Integer.toString(rs.getInt("rosterid"));
                String courseCode = rs.getString("coursecode");
                String location = rs.getString("location");
                LocalTime startTime = rs.getTime("starttime").toLocalTime();
                LocalTime endTime = rs.getTime("endtime").toLocalTime();
                Roster roster = new Roster(rosterId, courseCode, startTime, endTime, location);
                rosters.add(roster);
            }
        } catch (SQLException e) {
            System.out.println("Error while finding all rosters: " + e.getMessage());
        }
        return rosters;
    }
}
