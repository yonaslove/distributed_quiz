package server;

import common.Question;
import common.QuizService;
import common.User;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;

public class QuizServiceImpl extends UnicastRemoteObject implements QuizService {
    private DatabaseManager dbManager;
    private QuizServer serverContext;

    public QuizServiceImpl(QuizServer serverContext) throws RemoteException {
        super();
        this.serverContext = serverContext;
        this.dbManager = new DatabaseManager();
    }

    @Override
    public User login(String username, String password) throws RemoteException {
        System.out.println("Login attempt: " + username);
        return dbManager.authenticate(username, password);
    }

    @Override
    public common.Subject validateSubjectCode(String code, int studentId) throws RemoteException {
        System.out.println("Validating Subject Code: " + code + " for Student: " + studentId);
        try {
            common.Subject subject = dbManager.getSubjectByCode(code, studentId);
            if (subject == null) {
                // If null, it means code invalid OR generic error.
                // Using exception message from DB would be better, but getSubjectByCode returns
                // null or throws.
                // My DB Update in Step 96 THROWS Exception for "Draft" or "Already Submitted".
                // So I need to catch that.
                throw new RemoteException("Invalid Subject Code.");
            }

            long now = System.currentTimeMillis();
            if (subject.getStartTime() != null) {
                long start = subject.getStartTime().getTime();
                if (now < start) {
                    throw new RemoteException("Exam has not started yet.\nStarts at: " + subject.getStartTime());
                }
            }
            if (subject.getEndTime() != null) {
                long end = subject.getEndTime().getTime();
                if (now > end) {
                    throw new RemoteException("Exam has ended.\nEnded at: " + subject.getEndTime());
                }
            }

            return subject;
        } catch (Exception e) {
            // Propagate the specific message (Draft / Already Submitted)
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public List<Question> getQuestions(int subjectId) throws RemoteException {
        // ... same ...
        System.out.println("Fetching questions for Subject ID: " + subjectId);
        return dbManager.getQuestions(subjectId);
    }

    @Override
    public int submitMockQuiz(int userId, int subjectId, Map<Integer, String> answers) throws RemoteException {
        System.out.println("User " + userId + " submitted quiz for Subject " + subjectId);
        int score = 0;
        try {
            score = dbManager.calculateScore(userId, subjectId, answers);
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Submission Failed: " + e.getMessage());
        }

        logResultToFile(userId, score);

        // Broadcast to Replicas
        if (serverContext != null) {
            serverContext.broadcastReplication(userId, score);
        }

        return score;
    }

    @Override
    public boolean addSubject(String name, String code, java.sql.Timestamp start, java.sql.Timestamp end, int creatorId)
            throws RemoteException {
        return dbManager.addSubject(name, code, start, end, creatorId);
    }

    @Override
    public boolean publishSubject(int subjectId) throws RemoteException {
        return dbManager.publishSubject(subjectId);
    }

    @Override
    public boolean addQuestion(int subjectId, String text, String a, String b, String c, String d, String correct)
            throws RemoteException {
        return dbManager.addQuestion(subjectId, text, a, b, c, d, correct);
    }

    @Override
    public void replicateSubmission(int studentId, int score) throws RemoteException {
        System.out.println("[REPLICA] Received update: Student " + studentId + " scored " + score);
        // In a real system, we might update a local cache or standby DB here.
        // For now, we log it to prove consistency.
        try (java.io.FileWriter fw = new java.io.FileWriter("replica_log.txt", true);
                java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
            pw.println("REPLICA SYNC | Student: " + studentId + " | Score: " + score);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getServerTime() throws RemoteException {
        return System.currentTimeMillis();
    }

    // Mutual Exclusion for file writing (Phase 3 requirement)
    private synchronized void logResultToFile(int userId, int score) {
        try (java.io.FileWriter fw = new java.io.FileWriter("results_log.txt", true);
                java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {

            pw.println("Time: " + System.currentTimeMillis() + " | UserID: " + userId + " | Score: " + score);
            System.out.println("Result logged to file (Thread-Safe).");

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public common.ShuffleStrategy getShuffleStrategy() throws RemoteException {
        return new SmartShuffler();
    }

    @Override
    public java.util.List<common.User> getAllStudents() throws RemoteException {
        System.out.println("Fetching all student records for Admin...");
        return dbManager.getAllStudents();
    }

    @Override
    public boolean resetStudentSubmission(int studentId, int subjectId) throws RemoteException {
        System.out.println("Admin resetting submission for Student ID: " + studentId + " Subject ID: " + subjectId);
        return dbManager.resetStudentSubmissionForSubject(studentId, subjectId);
    }

    @Override
    public List<common.Subject> getAllSubjects() throws RemoteException {
        return dbManager.getAllSubjects();
    }

    @Override
    public List<common.Subject> getSubjectsByCreator(int creatorId) throws RemoteException {
        return dbManager.getSubjectsByCreator(creatorId);
    }

    @Override
    public java.util.List<String> getAllResults() throws RemoteException {
        return new java.util.ArrayList<>(); // Legacy method stub
    }

    @Override
    public java.util.List<String> getClusterTopology() throws RemoteException {
        // Return the list of "IP:Port" strings loaded by the server
        List<String> nodes = new java.util.ArrayList<>();
        // Iterate over Config Property keys starting with "node."?
        // Or access the 'allNodes' from QuizServer if exposed?
        // Let's modify QuizServer to expose the raw string list or Properties.
        // For now, let's fetch from Properties via QuizServer helper or just re-read?
        // Better: Add a method to QuizServer to get the raw topology strings.
        return serverContext.getTopologyStrings();
    }

    // New Admin Review Workflow Methods
    @Override
    public boolean approveExamDraft(int subjectId) throws RemoteException {
        return dbManager.approveExamDraft(subjectId);
    }

    @Override
    public List<common.Subject> getPendingExams() throws RemoteException {
        return dbManager.getPendingExams();
    }

    @Override
    public int getQuestionCount(int subjectId) throws RemoteException {
        return dbManager.getQuestionCount(subjectId);
    }

    @Override
    public boolean deleteSubject(int subjectId) throws RemoteException {
        return dbManager.deleteSubject(subjectId);
    }

    @Override
    public List<common.User> getStudentSubmissionsForExam(int subjectId) throws RemoteException {
        return dbManager.getStudentSubmissionsForExam(subjectId);
    }

    // New Admin User Management
    @Override
    public boolean addTeacher(String username, String password, String fullName, String department)
            throws RemoteException {
        System.out.println("Admin adding teacher: " + username);
        return dbManager.addTeacher(username, password, fullName, department);
    }

    @Override
    public boolean addReviewer(String username, String password, String fullName) throws RemoteException {
        System.out.println("Admin adding reviewer: " + username);
        return dbManager.addReviewer(username, password, fullName);
    }

    @Override
    public boolean addStudent(String username, String password, String fullName, String department, String gender)
            throws RemoteException {
        System.out.println("Admin adding student: " + username);
        return dbManager.addStudent(username, password, fullName, department, gender);
    }
}
