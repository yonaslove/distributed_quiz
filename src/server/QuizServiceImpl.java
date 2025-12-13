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
    public List<Question> getQuestions() throws RemoteException {
        System.out.println("Fetching questions...");
        return dbManager.getQuestions();
    }

    public int submitMockQuiz(int userId, Map<Integer, String> answers) throws RemoteException {
        System.out.println("User " + userId + " submitted quiz.");
        int score = dbManager.calculateScore(userId, answers);
        logResultToFile(userId, score);

        // Broadcast to Replicas
        if (serverContext != null) {
            serverContext.broadcastReplication(userId, score);
        }

        return score;
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
    public boolean resetStudentSubmission(int studentId) throws RemoteException {
        System.out.println("Admin resetting submission for Student ID: " + studentId);
        return dbManager.resetStudent(studentId);
    }

    @Override
    public java.util.List<String> getAllResults() throws RemoteException {
        return new java.util.ArrayList<>(); // Legacy method stub
    }
}
