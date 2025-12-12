package server;

import common.Question;
import common.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3300/distributed_quiz";
    private static final String USER = "root";
    private static final String PASS = ""; // Default XAMPP/MySQL password often empty

    private Connection connection;
    private boolean useMock = false;

    public DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // First connect without DB to check/create it
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3300/", USER, PASS);
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS distributed_quiz");
            stmt.close();
            connection.close();

            // Now connect to the DB
            connection = DriverManager.getConnection(URL, USER, PASS);
            createTablesIfNotExist();
            System.out.println("Connected to Database (Auto-Created if needed).");
        } catch (Exception e) {
            System.err.println("Database connection failed (" + e.getMessage() + "). Using MOCK mode.");
            useMock = true;
        }
    }

    private void createTablesIfNotExist() {
        try (Statement stmt = connection.createStatement()) {
            // Users
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "role ENUM('STUDENT', 'TEACHER') DEFAULT 'STUDENT')");

            // Questions
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS questions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "question_text TEXT NOT NULL, " +
                    "option_a VARCHAR(255) NOT NULL, " +
                    "option_b VARCHAR(255) NOT NULL, " +
                    "option_c VARCHAR(255) NOT NULL, " +
                    "option_d VARCHAR(255) NOT NULL, " +
                    "correct_option CHAR(1) NOT NULL)");

            // Seed Data Check
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users");
            if (rs.next() && rs.getInt(1) == 0) {
                seedData(stmt);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedData(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "INSERT INTO users (username, password, role) VALUES ('admin', 'admin123', 'TEACHER'), ('student', 'pass123', 'STUDENT')");
        stmt.executeUpdate(
                "INSERT INTO questions (question_text, option_a, option_b, option_c, option_d, correct_option) VALUES "
                        +
                        "('What is the capital of France?', 'London', 'Berlin', 'Paris', 'Madrid', 'C'), " +
                        "('Which protocol is used for RMI?', 'HTTP', 'JRMP', 'FTP', 'SMTP', 'B'), " +
                        "('What does JDBC stand for?', 'Java Database Connectivity', 'Java Data Control', 'Just Do Basic Code', 'None', 'A')");
        System.out.println("Seeded initial data.");
    }

    public User authenticate(String username, String password) {
        if (useMock) {
            if ("student".equals(username) && "pass".equals(password)) {
                return new User(1, "student", "STUDENT");
            }
            if ("teacher".equals(username) && "admin".equals(password)) {
                return new User(2, "teacher", "TEACHER");
            }
            return null;
        }

        try {
            PreparedStatement ps = connection
                    .prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?");
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Question> getQuestions() {
        List<Question> list = new ArrayList<>();
        if (useMock) {
            list.add(new Question(1, "What is the capital of France?", "London", "Berlin", "Paris", "Madrid"));
            list.add(new Question(2, "What is 2 + 2?", "3", "4", "5", "6"));
            list.add(new Question(3, "Which is a distributed system concept?", "RMI", "HTML", "CSS", "Photoshop"));
            return list;
        }

        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM questions");
            while (rs.next()) {
                list.add(new Question(
                        rs.getInt("id"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public int calculateScore(Map<Integer, String> answers) {
        int score = 0;

        // Mock answers
        Map<Integer, String> correctAnswers = new HashMap<>();
        if (useMock) {
            correctAnswers.put(1, "C"); // Paris is 3rd option? usually A=0, B=1, C=2. Let's assume input is "A", "B",
                                        // "C"...
            correctAnswers.put(2, "B");
            correctAnswers.put(3, "A");
        } else {
            try {
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, correct_option FROM questions");
                while (rs.next()) {
                    correctAnswers.put(rs.getInt("id"), rs.getString("correct_option"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        for (Map.Entry<Integer, String> entry : answers.entrySet()) {
            String correct = correctAnswers.get(entry.getKey());
            if (correct != null && correct.equalsIgnoreCase(entry.getValue())) {
                score++;
            }
        }
        return score;
    }
}
