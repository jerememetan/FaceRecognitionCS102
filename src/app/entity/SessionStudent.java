package app.entity;

// Association class between Session and Student entities
public class SessionStudent {
    private Session session;
    private Student student;
    private String status; // e.g. "Present", "Absent", "Late"
    private String notes;

    //for new students added to session
    public SessionStudent(Session session, Student student) {
        this.session = session;
        this.student = student;
        this.status = "Pending"; // default
        this.notes = "";
    }

    public SessionStudent(Session session, Student student, String status, String notes) {
        this.session = session;
        this.student = student;
        this.status = status;
        this.notes = notes;
    }

    // Getters and setters
    public Session getSession() {
        return session;
    }
    public Student getStudent() {
        return student;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
