// StudentData & its Builder: used to carry data from sampleData.txt
// TODO: Project brief mentions #include and #exclude for builders, dk what this means
// TODO: convert to try-with for Scanners

package reportingandexport;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class StudentData {
    public static List<StudentData> SampleStudentData = new ArrayList<>();
    private Integer StudentID;
    private String Name;
    private String Status;
    private String Timestamp;
    private double Confidence;
    private String Method;
    private String Notes;

    // Private constructor to support Builder usage
    private StudentData(Builder builder) {
        this.StudentID = builder.StudentID;
        this.Name = builder.Name;
        this.Status = builder.Status;
        this.Timestamp = builder.Timestamp;
        this.Confidence = builder.Confidence;
        this.Method = builder.Method;
        this.Notes = builder.Notes;
    }

    // Getters for fields
    public Integer getStudentID() { return StudentID; }
    public String getName() { return Name; }
    public String getStatus() { return Status; }
    public String getTimestamp() { return Timestamp; }
    public double getConfidence() { return Confidence; }
    public String getMethod() { return Method; }
    public String getNotes() { return Notes; }

    // Builder to construct the initial values for Student Data
    public static class Builder {
        private Integer StudentID = null;
        private String Name = null;
        private String Status = null;
        private String Timestamp = null;
        private double Confidence = 0.0;
        private String Method = null;
        private String Notes = null;

        public Builder setStudentID(Integer StudentID) {
            this.StudentID = StudentID;
            return this;
        }

        public Builder setName(String Name) {
            this.Name = Name;
            return this;
        }

        public Builder setStatus(String Status) {
            this.Status = Status;
            return this;
        }

        public Builder setTimestamp(String Timestamp) {
            this.Timestamp = Timestamp;
            return this;
        }
        
        public Builder setConfidence(double Confidence) {
            this.Confidence = Confidence;
            return this;
        }

        public Builder setMethod(String Method) {
            this.Method = Method;
            return this;
        }

        public Builder setNotes(String Notes) {
            this.Notes = Notes;
            return this;
        }

        public StudentData build() {
            return new StudentData(this);
        }
    }

    // Method called with argument sampleData.txt to load Student Data
    public static void loadSampleDataFromFile(String fileName) {
        File file = new File(fileName);

        try {
            Scanner scLine = new Scanner(file);
            scLine.useDelimiter("\\n");
            while (scLine.hasNext()) {
                String line = scLine.next();
                Scanner scData = new Scanner(line);
                scData.useDelimiter(",");
                Integer StudentID = Integer.parseInt(scData.next());
                String Name = scData.next();
                String Status = scData.next();
                String Timestamp = scData.next();
                double Confidence = Double.parseDouble(scData.next());
                String Method = scData.next();
                String Notes = scData.next();
                StudentData data = new StudentData.Builder()
                                                    .setStudentID(StudentID)
                                                    .setName(Name)
                                                    .setStatus(Status)
                                                    .setTimestamp(Timestamp)
                                                    .setConfidence(Confidence)
                                                    .setMethod(Method)
                                                    .setNotes(Notes)
                                                    .build();
                StudentData.SampleStudentData.add(data);
                scData.close();
            }
            scLine.close();
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + fileName);
        }
    }
}
