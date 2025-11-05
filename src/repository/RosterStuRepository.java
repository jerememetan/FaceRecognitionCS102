package repository;
import java.util.List;
import entity.RosterStudent;
public interface RosterStuRepository extends Repository<RosterStudent> {
    @Override
    boolean save(RosterStudent rs);

    boolean delete(RosterStudent rs);

    boolean update(RosterStudent rs);

    RosterStudent findById(int rosterId, String studentId);

    List<RosterStudent> findByRosterId(int rosterId);

    List<RosterStudent> findAll();
}
