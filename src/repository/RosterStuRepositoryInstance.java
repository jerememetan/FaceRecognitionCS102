package repository;


import entity.Roster;
import entity.RosterStudent;
import entity.Student;

import java.sql.*;
import java.util.*;

public class RosterStuRepositoryInstance implements RosterStuRepository {

    //base interface method not applicable for composite key relation
    @Override
    public boolean delete(String id) {
        throw new UnsupportedOperationException(
            "Use delete(SessionStudent sessStu) instead for composite key."
        );
    }
    @Override 
    public boolean delete(RosterStudent rosterStudent){
        String sql = "DELETE FROM rosterstudents WHERE rosterid = ? AND studentid = ?";
        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql) ) {
                ps.setInt(1, Integer.parseInt(rosterStudent.getRoster().getRosterId()));
                ps.setString(2, rosterStudent.getStudent().getStudentId());
                ps.executeUpdate();
            }
        catch (SQLException e){
            System.out.println("Error while deleting student from roster");
            return false;
        }
        return true;
    }
    @Override
    public boolean update(RosterStudent rosterStudent){
        throw new UnsupportedOperationException(
            "rosterstudents only consists of rosterid and studentid"
        );
    }
    public boolean save(RosterStudent rosterStudent){
        String sql = "INSERT INTO rosterstudents(rosterid, studentid) VALUES (?, ?)";
        try(
                Connection con = DBConnection.getConnection(); 
                PreparedStatement ps = con.prepareStatement(sql);) {
            ps.setInt(1, Integer.parseInt(rosterStudent.getRoster().getRosterId()));
            ps.setString(2, rosterStudent.getStudent().getStudentId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error while inserting into rosterstudents: " + e.getMessage());
            e.printStackTrace(System.err);
            System.out.flush();
            return false;
        }
        return true;
    }
    @Override
    public RosterStudent findById(String id){
        throw new UnsupportedOperationException(
            "Use findById(int rosterid, int studentid) for composite-key relations"
        );
    }
    @Override
    public RosterStudent findById(int rosterId, String studentId){
        String sql = "SELECT * FROM rosterstudents WHERE rosterid = ? AND studentid = ?";

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)){
                ps.setInt(1, rosterId);
                ps.setString(2, studentId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()){
                    Roster roster = new RosterRepositoryInstance().findById(Integer.toString(rosterId));
                    Student student = new StudentRepositoryInstance().findById(studentId);
                    return new RosterStudent(roster, student);
                }
            }
        catch(SQLException e){
            System.out.println("Error finding rosterstudent with rosterId: " + rosterId + " and studentId: " + studentId);
        }
        return null;
    }
    @Override
    public ArrayList<RosterStudent> findByRosterId(int rosterId){
        ArrayList<RosterStudent> studentRoster = new ArrayList<>();
        String sql = "SELECT * FROM rosterstudents WHERE rosterid = ?";

        try (
            Connection con =  DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql); ) {
            ps.setInt(1, rosterId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()){
                Roster roster = new RosterRepositoryInstance().findById(Integer.toString(rosterId));
                Student student = new StudentRepositoryInstance().findById(rs.getString("studentid"));
                RosterStudent rosterStudent = new RosterStudent(roster, student);
                studentRoster.add(rosterStudent);
            }
        }
        catch (SQLException e){
            System.out.println("Error finding students in roster with id: " + rosterId);
            return null;
        }
        return studentRoster;
    }

    @Override
    public ArrayList<RosterStudent> findAll(){
        ArrayList<RosterStudent> studentRoster = new ArrayList<>();
        String sql = "SELECT * FROM rosterstudents";

        try (
            Connection con =  DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql); ) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()){
                Roster roster = new RosterRepositoryInstance().findById(Integer.toString(rs.getInt("rosterid")));
                Student student = new StudentRepositoryInstance().findById(rs.getString("studentid"));
                RosterStudent rosterStudent = new RosterStudent(roster, student);
                studentRoster.add(rosterStudent);
            }
        }
        catch (SQLException e){
            System.out.println("Error finding all rosterstudents");
            return null;
        }
        return studentRoster;
    }

}
