package app.entity;

// Association class between Session and Student entities
public class SessionStudent {
    private Student student;
    private String status; // e.g. "Present", "Absent", "Late"
    private String notes;

    public SessionStudent(Student student) {
        this.student = student;
        this.status = "Pending"; // default
        this.notes = "";
    }

    // Getters and setters
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
