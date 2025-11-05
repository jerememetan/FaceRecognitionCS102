package entity;

// Association class between Roster and Student entities
public class RosterStudent {
    private Roster roster;
    private Student student;

    public RosterStudent(Roster roster, Student student) {
        this.roster = roster;
        this.student = student;
    }
    public Roster getRoster() {
        return roster;
    }
    public Student getStudent() {
        return student;
    }
}
