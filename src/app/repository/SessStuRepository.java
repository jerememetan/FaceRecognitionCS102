package app.repository;
import app.entity.SessionStudent;
import java.util.ArrayList;

public interface SessStuRepository extends Repository<SessionStudent> { 

    boolean delete(SessionStudent sessStu);

    boolean update(SessionStudent sessStu);

    boolean save(SessionStudent sessStu);

    SessionStudent findById(int sessionId, String studentId);

    ArrayList<SessionStudent> findBySessionId(int sessionId);

    ArrayList<SessionStudent> findAll();
}
