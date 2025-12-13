package test;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import common.QuizService;
import java.util.HashMap;

public class TestReplication {
    public static void main(String[] args) {
        try {
            // Connect to Node 1 (1099) explicitly
            System.out.println("Connecting to Node 1 (1099)...");
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            QuizService service = (QuizService) registry.lookup("QuizService");

            // Submit
            System.out.println("Submitting score for User 1...");
            java.util.Map<Integer, String> answers = new HashMap<>();
            answers.put(1, "A");
            service.submitMockQuiz(1, answers);

            System.out.println("Submission sent. Check Node 2 console for [REPLICA] message.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
