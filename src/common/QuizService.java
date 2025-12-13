package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface QuizService extends Remote {
    // Auth
    User login(String username, String password) throws RemoteException;

    // Subjects
    // Updated validity check to include Student ID for strict checking
    Subject validateSubjectCode(String code, int studentId) throws RemoteException;

    // Exam Creator / Admin
    boolean addSubject(String name, String code, java.sql.Timestamp start, java.sql.Timestamp end, int creatorId)
            throws RemoteException;

    boolean publishSubject(int subjectId) throws RemoteException;

    boolean addQuestion(int subjectId, String text, String a, String b, String c, String d, String correct)
            throws RemoteException;

    // Questions (Scoped by Subject)
    List<Question> getQuestions(int subjectId) throws RemoteException;

    /**
     * Submits answers.
     * 
     * @param userId  the user ID.
     * @param answers Map of QuestionID -> SelectedOptionIndex (0=A, 1=B, 2=C, 3=D)
     * @return the score
     */
    int submitMockQuiz(int userId, int subjectId, Map<Integer, String> answers) throws RemoteException;

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
