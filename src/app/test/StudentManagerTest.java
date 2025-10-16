package app.test;

import app.entity.*;
import app.model.*;
import app.repository.*;
import app.service.*;
import app.util.*;

public class StudentManagerTest {
    public static void main(String[] args) {
        Student stu = new Student("123", "John", "john@mail.com", "995");
        FaceDetection fd = new FaceDetection();
        StudentRepositoryInstance repo = new StudentRepositoryInstance();
        repo.save(stu);
        repo.update(new Student("123", "Jack"));

        boolean success = fd.captureAndStoreFaceImages(stu, 10);
        System.out.println(success);
    }
}
