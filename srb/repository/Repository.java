package repository;

import java.util.*;

public interface Repository<Type> {
    boolean save(Type entity);
    boolean update(Type entity);
    boolean delete(String id);
    Type findById(String id);
    List<Type> findAll();
}







