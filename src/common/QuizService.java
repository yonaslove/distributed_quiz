package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface QuizService extends Remote {
    User login(String username, String password) throws RemoteException;

    List<Question> getQuestions() throws RemoteException;

    /**
     * Submits answers.
     * 
     * @param userId  the user ID.
     * @param answers Map of QuestionID -> SelectedOptionIndex (0=A, 1=B, 2=C, 3=D)
     * @return the score
     */
    int submitMockQuiz(int userId, Map<Integer, String> answers) throws RemoteException;

    long getServerTime() throws RemoteException;

    // Code Migration: Server sends the sorting logic object to client
    ShuffleStrategy getShuffleStrategy() throws RemoteException;

    // Admin/Teacher Feature (Old Log based)
    java.util.List<String> getAllResults() throws RemoteException;

    // Modern Admin Features (Database Based)
    java.util.List<User> getAllStudents() throws RemoteException; // For ranking and display

    boolean resetStudentSubmission(int studentId) throws RemoteException; // Allow retry

    // Distributed Consistency
    void replicateSubmission(int studentId, int score) throws RemoteException;
}
