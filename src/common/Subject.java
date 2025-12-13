package common;

import java.io.Serializable;
import java.sql.Timestamp;

public class Subject implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String accessCode;
    private Timestamp startTime;
    private boolean isPublished;

    public Subject(int id, String name, String accessCode, Timestamp startTime, Timestamp endTime,
            boolean isPublished) {
        this.id = id;
        this.name = name;
        this.accessCode = accessCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isPublished = isPublished;
    }

    // Legacy constructor for backward compatibility if needed (but we updated
    // calls)
    public Subject(int id, String name, String accessCode, Timestamp startTime, Timestamp endTime) {
        this(id, name, accessCode, startTime, endTime, false);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public boolean isPublished() {
        return isPublished;
    }

    @Override
    public String toString() {
        return name;
    }
}
