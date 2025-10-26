package app.test;

import app.entity.Session;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minimal in-memory SessionManager to satisfy SessionViewer usage.
 * Replace with full implementation later if needed.
 */
public class SessionManager {
    private final List<Session> sessions = new ArrayList<>();
    private int nextId = 1;

    public SessionManager() {
        // initialize with a couple of demo sessions
        createSession("Math Workshop", LocalDate.now(), LocalTime.of(9,0), LocalTime.of(11,0), "Room 101");
        createSession("AI Seminar", LocalDate.now(), LocalTime.of(13,0), LocalTime.of(15,0), "Lab A");
    }

    public List<Session> getAllSessions() {
        return new ArrayList<>(sessions);
    }

    public Session createSession(String name, LocalDate date, LocalTime startTime, LocalTime endTime, String location) {
        String id = String.valueOf(nextId++);
        Session s = new Session(id, name, date, startTime, endTime, location);
        sessions.add(s);
        return s;
    }

    public void openSession(int sessionId) {
        String idStr = String.valueOf(sessionId);
        for (Session s : sessions) {
            if (s.getSessionId().equals(idStr)) {
                s.open();
                break;
            }
        }
    }

    public boolean deleteSession(int sessionId) {
        String idStr = String.valueOf(sessionId);
        Iterator<Session> it = sessions.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            if (s.getSessionId().equals(idStr)) {
                it.remove();
                return true;
            }
        }
        return false;
    }
}
