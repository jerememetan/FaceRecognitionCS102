package app.test;
import java.util.*;
import java.time.*;
import app.entity.Session;
public class SessionManager {
    private Map<Integer, Session> sessions = new LinkedHashMap<>();
    private int nextId = 1;
    public SessionManager() {
        this.sessions = new LinkedHashMap<>();
    }
    public Session createSession(String name, LocalDate date, LocalTime startTime, LocalTime endTime, String location) {
        Session newSession = new Session(Integer.toString(nextId++), name, date, startTime, endTime, location);
        sessions.put(nextId, newSession);
        System.out.println("Created session: " + name);
        return newSession;
    }
    public void openSession(int id) {
        Session session = sessions.get(id);
        if (session != null) {
            session.open();
        } else {
            System.out.println("Session with ID " + id + " not found.");
        }
    }
    public void closeSession(int id) {
        Session session = sessions.get(id);
        if (session != null) {
            session.close();
        } else {
            System.out.println("Session with ID " + id + " not found.");
        }
    }
    public List<Session> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }
    public Session getSessionById(int id) {
        return sessions.get(id);
    }
}
