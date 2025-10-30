package repository;

import entity.Student;
import java.util.*;

public interface StudentRepository extends Repository<Student>{
    @Override
    Student findById(String studentId);

    List<Student> findByName(String name);
    
    boolean existsByStudentId(String studentId);
}







