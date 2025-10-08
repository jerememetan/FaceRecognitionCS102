package app.repository;

import java.util.*;
import app.entity.Student;

public interface StudentRepository extends Repository<Student>{
    @Override
    Student findById(String studentId);

    List<Student> findByName(String name);
    
    boolean existsByStudentId(String studentId);
}
