package app.repository;

import app.entity.Session;
import java.util.*;
public interface SessionRepository extends Repository<Session>{
    @Override

    boolean delete (String sessionId);

    boolean update (Session session);
    
    boolean save(Session session);

    ArrayList<Session> findAll();

    Session findById(String sessionId);

    
}