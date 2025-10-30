package entity;

import model.FaceData;

public class Student extends Entity {
    private String studentId;
    private String name;
    private String email;
    private String phone;
    private FaceData faceData;

    public Student(String studentId, String name) {
        super();
        this.studentId = studentId;
        this.name = name;
        this.faceData = new FaceData(studentId, name);
        this.email = null;
        this.phone = null;
    }

    public Student(String studentId, String name, String email, String phone) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.faceData = new FaceData(studentId, name);
    }

    public String getStudentId() {
        return studentId;
    }

    public String getName() {
        return name;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setFaceData(FaceData faceData) {
        this.faceData = faceData;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public FaceData getFaceData() {
        return faceData;
    }

    @Override
    public String toString() {
        return String.format("Student{id='%s', name='%s', email='%s', phone='%s'}", studentId, name, email, phone);
    }

}







