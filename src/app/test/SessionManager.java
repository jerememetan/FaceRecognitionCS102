package app.test;

<<<<<<< HEAD
import app.entity.Session;
import app.entity.Student;
import app.entity.SessionStudent;
import app.repository.SessionRepositoryInstance;
import app.repository.SessStuRepositoryInstance;
public class SessionManager {
    private final List<Session> sessions = new ArrayList<>();
    private int nextId = 1;
    private SessionRepositoryInstance sessionDB;
    private SessStuRepositoryInstance sessStuDB;
    public SessionManager() {
        this.sessions = new LinkedHashMap<>();
        this.sessionDB = new SessionRepositoryInstance();
        this.sessStuDB = new SessStuRepositoryInstance();
    }
    public Session createNewSession(String name, LocalDate date, LocalTime startTime, LocalTime endTime, String location) {
        Session newSession = new Session(Integer.toString(nextId), name, date, startTime, endTime, location);
        sessions.put(nextId, newSession);
        sessionDB.save(newSession);
        System.out.println(nextId + " Created session: " + name);
        nextId++;
        return newSession;
    }
    //helper function to populate sessions from db
    public void populateSessions() {
        List<Session> allSessions = sessionDB.findAll();
        for(Session s : allSessions){
            sessions.put(Integer.parseInt(s.getSessionId()), s);
        }
    }
    public boolean addStudentToSession(Session session, Student student) {
        try {
            SessionStudent ss = new SessionStudent(session, student);
            boolean success = sessStuDB.save(ss);
            System.out.println("DB insert success: " + success);
            if (!success) {
                return false; // Failed to add to database
            }
            session.addNewStudent(student);
            return true;
        } catch (Exception e) {
            System.out.flush();
            System.out.print(e.getMessage());
            return false;
        } 
    }
    public boolean removeStudentFromSession(Session session, Student student) {
        session.removeStudent(student);
        SessionStudent ss = new SessionStudent(session, student);
        boolean deleted = sessStuDB.delete(ss); //deletes that student-session relation from db
        if (deleted) {
            System.out.println("Removed student " + student.getName() + " from session " + session.getName());
        } else {
            System.out.println("Failed to remove student " + student.getName() + " from session " + session.getName());
        }
        return deleted;
    }

    public boolean loadSessionRoster(Session session){
        ArrayList<SessionStudent> sessStuList = sessStuDB.findBySessionId(Integer.parseInt(session.getSessionId()));
        if(sessStuList == null){
            System.out.println("No students found for session ID " + session.getSessionId());
            return false;
        }
        session.setStudentRoster(sessStuList);
        System.out.println("Loaded " + sessStuList.size() + " students into session " + session.getName());
        return true;
    }

    public void openSession(Session session) {
        if (session != null) {
            session.open();
            sessionDB.update(session); //sets active status in db to "True"
        } else {
            System.out.println("Session not found.");
        }
    }
    public void closeSession(Session session) {
        if (session != null) {
            session.close();
            sessionDB.update(session); //sets active status in db to "False"
        } else {
            System.out.println("Session not found.");
        }
    }
    public List<Session> getAllSessions() {
        // TODO: Implement
        return java.util.Collections.emptyList();
    }

    public void openSession(int sessionId) {
        // TODO: Implement
    }
    public boolean deleteSession(int id) {
        if (sessions.containsKey(id) && sessions.get(id).isActive() == false) {
            sessions.remove(id);
            sessionDB.delete(Integer.toString(id));
            System.out.println("Deleted session ID " + id);
            return true; // Successfully deleted
        }
        return false; // Session not found
    }
}
>>>>>>> origin/JR-StudentManager
