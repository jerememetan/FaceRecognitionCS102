package entity;
import java.util.ArrayList;
import java.time.LocalTime;

public class Roster extends Entity{
    private String rosterId;
    private String courseCode;
    private String location;    //location,startTime,endTime,students for autofill functionality when creating sessions
    private LocalTime startTime;
    private LocalTime endTime;
    private ArrayList<RosterStudent> students;

    public Roster(String rosterId, String courseCode, LocalTime startTime, LocalTime endTime, String location) {
        this.rosterId = rosterId;
        this.courseCode = courseCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.students = new ArrayList<>();
    }
    public String getRosterId() {
        return rosterId;
    }
    public String getCourseCode() {
        return courseCode;
    }
    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }
    public String getLocation() {
        return location;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    public LocalTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }
    public LocalTime getEndTime() {
        return endTime;
    }
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
    public ArrayList<RosterStudent> getStudents() {
        return students;
    }
    public void setStudents(ArrayList<RosterStudent> students) {
        this.students = students;
    }
    public void addNewStudent(Student student){
        this.students.add(new RosterStudent(this, student));
    }
    public void removeStudent(Student student){
        this.students.removeIf(rs -> rs.getStudent().equals(student));
    }
}

