package repository;

import entity.Roster;
import java.util.List;
public interface RosterRepository extends Repository<Roster>{
    @Override
    boolean delete (String rosterId);

    boolean update (Roster roster);
    
    boolean save(Roster roster);

    Roster findById(String rosterId);

    List<Roster> findAll();
}
