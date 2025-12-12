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

    public QuizServiceImpl() throws RemoteException {
        super();
        dbManager = new DatabaseManager();
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
        int score = dbManager.calculateScore(answers);
        logResultToFile(userId, score);
        return score;
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
    public java.util.List<String> getAllResults() throws RemoteException {
        java.util.List<String> results = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("results_log.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                results.add(line);
            }
        } catch (java.io.FileNotFoundException e) {
            results.add("No results log found yet.");
        } catch (java.io.IOException e) {
            e.printStackTrace();
            results.add("Error reading log file.");
        }
        return results;
    }
}
