package service.roster;

import java.util.*;
import java.time.*;
import entity.Roster;
import entity.Student;
import entity.RosterStudent;
import repository.RosterRepositoryInstance;
import repository.RosterStuRepositoryInstance;


public class RosterManager {
    private Map<Integer, Roster> rosters;
    private int nextId = 1;
    private RosterRepositoryInstance rosterDB;
    private RosterStuRepositoryInstance rosterStuDB;
    public RosterManager() {
        this.rosterDB = new RosterRepositoryInstance();
        this.rosterStuDB = new RosterStuRepositoryInstance();
        this.rosters = new LinkedHashMap<>();
    }

    public Roster createNewRoster(String courseCode, LocalTime startTime, LocalTime endTime, String location) {
        Roster newRoster = new Roster(Integer.toString(nextId), courseCode, startTime, endTime, location);
        rosters.put(nextId, newRoster);
        rosterDB.save(newRoster);
        nextId++;
        System.out.println("Created roster: " + courseCode);
        return newRoster;
    }
    public boolean updateRoster(Roster roster){
        try{
            boolean success = rosterDB.update(roster);
            System.out.println("DB update " + success);
            return success;
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            return false;
        }

    }
    public boolean addStudentToRoster(Roster roster, Student student){
        try{
            RosterStudent rosterStudent = new RosterStudent(roster, student);
            boolean success = rosterStuDB.save(rosterStudent);
            System.out.println("DB Insert" + success);
            if (!success){
                return false;
            }
            roster.addNewStudent(student);
            return true;
        }
        catch (Exception e) {
            System.out.flush();
            System.out.print(e.getMessage());
            return false;
        }
    }
    public boolean removeStudentFromRoster(Roster roster, Student student){
        RosterStudent rosterStudent =  new RosterStudent(roster, student);
        boolean deleted = rosterStuDB.delete(rosterStudent);

        if (deleted){
            System.out.println("Removed student " + student.getName() + " from roster " + roster.getCourseCode());
            roster.removeStudent(student);
        }
        else{
            System.out.println("Failed to remove student " + student.getName() + " from roster " + roster.getCourseCode());
        }
        return deleted;
    }
    public boolean deleteRoster(int rosterId){
        if (!rosters.containsKey(rosterId)){
            System.out.println("Roster ID not found: " + rosterId);
            return false;
        }
        boolean dbDeleted = rosterDB.delete(Integer.toString(rosterId));
        if (dbDeleted) {
            rosters.remove(rosterId);
            System.out.println("Deleted roster ID: " + rosterId);
            return true;
        } else {
            System.out.println("Failed to delete roster ID from DB: " + rosterId);
            return false;
        }
    }
    public void populateRosters() {
        List<Roster> allRosters = rosterDB.findAll();
        for(Roster r : allRosters){
            rosters.put(Integer.parseInt(r.getRosterId()), r);
        }
        nextId = Integer.parseInt(allRosters.get(allRosters.size() - 1).getRosterId()) + 1;
    }
    public List<Roster> getAllRosters() {
        return new ArrayList<>(rosters.values());
    }
    public boolean loadRosterStudent(Roster roster){
        ArrayList<RosterStudent> rosterStuList = rosterStuDB.findByRosterId(Integer.parseInt(roster.getRosterId()));
        if (rosterStuList == null){
            System.out.println("No students found for roster ID " + roster.getRosterId());
            return false;
        }
        roster.setStudents(rosterStuList);
        System.out.println("Loaded " + rosterStuList.size() + " students into roster " + roster.getCourseCode());
        return true;
    }
}
