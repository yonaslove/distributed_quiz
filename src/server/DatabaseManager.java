package server;

import common.Question;
import common.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/distributed_quiz";
    private static final String USER = "root";
    private static final String PASS = "";

    private Connection connection;

    public Connection getConnection() {
        return connection;
    }

    private boolean useMock = false;

    public DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Connect to server (no DB) to create DB if not exists
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/", USER, PASS);
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS distributed_quiz");
            stmt.close();
            connection.close();

            connection = DriverManager.getConnection(URL, USER, PASS);
            createTablesIfNotExist();
            System.out.println("Connected to Database (Modern Schema).");
        } catch (Exception e) {
            System.err.println("Database connection failed (" + e.getMessage() + "). Using MOCK mode.");
            useMock = true;
        }
    }

    private void createTablesIfNotExist() {
        try (Statement stmt = connection.createStatement()) {
            // DETECT & FIX OLD SCHEMA
            try {
                // If 'users' table exists, drop it to migrate to new schema
                ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'users'");
                if (rs.next()) {
                    System.out.println("Migrating Config: Dropping legacy 'users' table...");
                    stmt.executeUpdate("DROP TABLE users");
                }
            } catch (SQLException ignored) {
            }

            // ADMIN TABLE
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admins (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "full_name VARCHAR(100) DEFAULT 'Administrator')");

            // STUDENT TABLE
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS students (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "full_name VARCHAR(100), " +
                    "department VARCHAR(100), " +
                    "score INT DEFAULT 0, " +
                    "has_submitted BOOLEAN DEFAULT FALSE)");

            // DROP Old 'users' table if conflicting (Use only if you want to force
            // migration, otherwise leave it)
            // stmt.executeUpdate("DROP TABLE IF EXISTS users");

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
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM admins");
            if (rs.next() && rs.getInt(1) == 0) {
                seedData(stmt);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedData(Statement stmt) throws SQLException {
        // Admin
        stmt.executeUpdate(
                "INSERT INTO admins (username, password, full_name) VALUES ('admin', 'admin123', 'System Administrator')");

        // Students
        stmt.executeUpdate("INSERT INTO students (username, password, full_name, department) VALUES " +
                "('S101', 'pass123', 'Alice Smith', 'Computer Science'), " +
                "('S102', 'pass123', 'Bob Jones', 'Engineering')");

        // Questions
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
            if ("admin".equals(username) && "admin123".equals(password)) {
                return new User(1, "admin", "TEACHER", "Admin Mock", "IT");
            }
            return new User(2, username, "STUDENT", "Student Mock", "CS");
        }

        try {
            // Check Admin
            PreparedStatement psAdmin = connection
                    .prepareStatement("SELECT * FROM admins WHERE username = ? AND password = ?");
            psAdmin.setString(1, username);
            psAdmin.setString(2, password);
            ResultSet rsA = psAdmin.executeQuery();
            if (rsA.next()) {
                return new User(rsA.getInt("id"), rsA.getString("username"), "TEACHER", rsA.getString("full_name"),
                        "Admin Dept");
            }

            // Check Student
            PreparedStatement psStud = connection
                    .prepareStatement("SELECT * FROM students WHERE username = ? AND password = ?");
            psStud.setString(1, username);
            psStud.setString(2, password);
            ResultSet rsS = psStud.executeQuery();
            if (rsS.next()) {
                User u = new User(rsS.getInt("id"), rsS.getString("username"), "STUDENT", rsS.getString("full_name"),
                        rsS.getString("department"));
                u.setScore(rsS.getInt("score"));
                u.setHasSubmitted(rsS.getBoolean("has_submitted"));
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<User> getAllStudents() {
        List<User> list = new ArrayList<>();
        if (useMock)
            return list;
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM students");
            while (rs.next()) {
                User u = new User(rs.getInt("id"), rs.getString("username"), "STUDENT", rs.getString("full_name"),
                        rs.getString("department"));
                u.setScore(rs.getInt("score"));
                u.setHasSubmitted(rs.getBoolean("has_submitted"));
                list.add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean resetStudent(int id) {
        if (useMock)
            return true;
        try {
            PreparedStatement ps = connection
                    .prepareStatement("UPDATE students SET has_submitted = FALSE, score = 0 WHERE id = ?");
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int calculateScore(int studentId, Map<Integer, String> answers) {
        int score = 0;
        Map<Integer, String> correctAnswers = new HashMap<>();

        // Fetch Correct Answers
        if (!useMock) {
            try {
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, correct_option FROM questions");
                while (rs.next()) {
                    correctAnswers.put(rs.getInt("id"), rs.getString("correct_option"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            // Mock data
            correctAnswers.put(1, "C");
            correctAnswers.put(2, "B");
            correctAnswers.put(3, "A");
        }

        for (Map.Entry<Integer, String> entry : answers.entrySet()) {
            String correct = correctAnswers.get(entry.getKey());
            if (correct != null && correct.equalsIgnoreCase(entry.getValue())) {
                score++;
            }
        }

        // SAVE TO DB
        if (!useMock) {
            try {
                PreparedStatement ps = connection
                        .prepareStatement("UPDATE students SET score = ?, has_submitted = TRUE WHERE id = ?");
                ps.setInt(1, score);
                ps.setInt(2, studentId);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return score;
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
}
