package entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import config.*;

public class Session extends Entity{
    private String sessionId;
    private String name;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private boolean active;
    private ArrayList<SessionStudent> studentRoster;

    public Session(String nextId, String name, LocalDate date, LocalTime startTime, LocalTime endTime, String location) {
        this.sessionId = nextId;
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.active = false;
        this.studentRoster = new ArrayList<>();
    }
    public void open(){
        this.active = true;
        AppLogger.info("Session " + this.name + " is now open.");
    }
    public void close(){
        this.active = false;
        AppLogger.info("Session " + this.name + " is now closed.");
    }
    public void addNewStudent(Student student){
        this.studentRoster.add(new SessionStudent(this, student));
    }
    public void removeStudent(Student student){
        this.studentRoster.removeIf(ss -> ss.getStudent().equals(student));
    }
    public ArrayList<SessionStudent> getStudentRoster(){
        return this.studentRoster;
    }
    public void setStudentRoster(ArrayList<SessionStudent> roster){
        this.studentRoster = roster;
    }
    public boolean isActive() {
        return active;
    }
    public String getSessionId() {
        return sessionId;
    }
    public String getName() {
        return name;
    }
    public LocalDate getDate() {
        return date;
    }
    public LocalTime getStartTime() {
        return startTime;
    }
    public LocalTime getEndTime() {
        return endTime;
    }
    public String getLocation() {
        return location;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }  
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
    public void setLocation(String location) {
        this.location = location;
    }
}







