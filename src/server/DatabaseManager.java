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

    public void createTablesIfNotExist() {
        try (Statement stmt = connection.createStatement()) {
            // DETECT & FIX OLD SCHEMA
            try {
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

            // TEACHERS TABLE (Exam Creator)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS teachers (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "full_name VARCHAR(100))");

            // STUDENT TABLE
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS students (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "full_name VARCHAR(100), " +
                    "department VARCHAR(100), " +
                    "score INT DEFAULT 0, " +
                    "has_submitted BOOLEAN DEFAULT FALSE)");

            // STUDENT SUBMISSIONS (Strict Check)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS student_submissions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "student_id INT, " +
                    "subject_id INT, " +
                    "score INT, " +
                    "submission_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (student_id) REFERENCES students(id), " +
                    "FOREIGN KEY (subject_id) REFERENCES subjects(id))");

            // SUBJECTS TABLE
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS subjects (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "access_code VARCHAR(50) NOT NULL UNIQUE, " +
                    "start_time TIMESTAMP NULL, " +
                    "end_time TIMESTAMP NULL)");

            // UPGRADE SUBJECTS TABLE
            try {
                // Ensure columns exist (Idempotent approach using simple ADD inside try/catch
                // if column missing is harder standard SQL,
                // but for this env we assume it might fail if exists. Better: Check columns.)
                // Given the limitation, we rely on the fact that if it fails, it prints stack
                // trace but continues?
                // No, I need to be careful.
                // Safest: Just run ALTER IGNORE or catch.
                stmt.executeUpdate("ALTER TABLE subjects ADD COLUMN is_published BOOLEAN DEFAULT FALSE");
                stmt.executeUpdate("ALTER TABLE subjects ADD COLUMN created_by INT DEFAULT NULL");
            } catch (SQLException e) {
                // Ignore "Duplicate column name" error
            }

            // QUESTIONS TABLE
            // Drop old questions table to ensure schema match
            stmt.executeUpdate("DROP TABLE IF EXISTS questions");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS questions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "subject_id INT, " +
                    "question_text TEXT NOT NULL, " +
                    "option_a VARCHAR(255) NOT NULL, " +
                    "option_b VARCHAR(255) NOT NULL, " +
                    "option_c VARCHAR(255) NOT NULL, " +
                    "option_d VARCHAR(255) NOT NULL, " +
                    "correct_option CHAR(1) NOT NULL, " +
                    "FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE)");

            // Seed Data (Safe to run always, checks inside)
            seedData(stmt);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedData(Statement stmt) {
        // Admin
        try {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM admins");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(
                        "INSERT INTO admins (username, password, full_name) VALUES ('admin', 'admin123', 'System Administrator')");
                System.out.println("Seeded Admins.");
            }
        } catch (SQLException e) {
            System.out.println("Skipping Admin seed (Msg: " + e.getMessage() + ")");
        }

        // Teachers (Creator)
        try {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM teachers");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(
                        "INSERT INTO teachers (username, password, full_name) VALUES ('creator', 'pass123', 'Exam Creator')");
                System.out.println("Seeded Teachers.");
            }
        } catch (SQLException e) {
            System.out.println("Skipping Teacher seed (Msg: " + e.getMessage() + ")");
        }

        // Students
        try {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM students");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO students (username, password, full_name, department) VALUES " +
                        "('S101', 'pass123', 'Alice Smith', 'Computer Science'), " +
                        "('S102', 'pass123', 'Bob Jones', 'Engineering')");
                System.out.println("Seeded Students.");
            }
        } catch (SQLException e) {
            System.out.println("Skipping Student seed (Msg: " + e.getMessage() + ")");
        }

        // Subjects (Requested by User)
        try {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM subjects");
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("Seeding Subjects...");
                stmt.executeUpdate("INSERT INTO subjects (name, access_code, start_time, end_time) VALUES " +
                        "('Web Programming (PHP)', 'PHP101', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR)), " +
                        "('Compiler Design', 'COMP303', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR)), " +
                        "('Distributed System', 'DIST404', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR)), " +
                        "('Software Architecture Design', 'ARCH505', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR)), " +
                        "('Artificial Intelligence', 'AI606', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR)), " +
                        "('Project Management', 'PM707', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR))");
                System.out.println("Seeded Subjects.");
            }
        } catch (SQLException e) {
            System.err.println("Error Seeding Subjects: " + e.getMessage());
        }

        // Questions
        try {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM questions");
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("Seeding Questions...");
                // 1: PHP
                stmt.executeUpdate(
                        "INSERT INTO questions (subject_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES "
                                +
                                "(1, 'What does PHP stand for?', 'Private Home Page', 'Personal Hypertext Processor', 'PHP: Hypertext Preprocessor', 'Public Hosting Platform', 'C'), "
                                +
                                "(1, 'Which symbol starts a variable in PHP?', '@', '$', '#', '&', 'B')");

                // 2: Compiler Design
                stmt.executeUpdate(
                        "INSERT INTO questions (subject_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES "
                                +
                                "(2, 'What is the first phase of compilation?', 'Syntax Analysis', 'Lexical Analysis', 'Code Generation', 'Optimization', 'B'), "
                                +
                                "(2, 'What tool is used for lexical analysis?', 'YACC', 'Bison', 'Lex', 'GDB', 'C')");

                // 3: Distributed System
                stmt.executeUpdate(
                        "INSERT INTO questions (subject_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES "
                                +
                                "(3, 'Which is NOT a characteristic of distributed systems?', 'Concurrency', 'Scalability', 'Single Point of Failure', 'Transparency', 'C'), "
                                +
                                "(3, 'RMI stands for?', 'Remote Method Invocation', 'Remote Memory Interface', 'Random Method Interaction', 'Real Machine Instruction', 'A')");

                // 4: Software Architecture
                stmt.executeUpdate(
                        "INSERT INTO questions (subject_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES "
                                +
                                "(4, 'Which pattern is used for separating concerns?', 'Singleton', 'MVC', 'Factory', 'Observer', 'B'), "
                                +
                                "(4, 'What does UML stand for?', 'Unified Modeling Language', 'Universal Machine Logic', 'Unique Model Link', 'User Mode Linux', 'A')");

                System.out.println("Seeded Questions.");
            }
        } catch (SQLException e) {
            System.err.println("Error Seeding Questions: " + e.getMessage());
        }
    }

    public common.Subject getSubjectByCode(String code, int studentId) throws Exception {
        if (useMock)
            return new common.Subject(1, "Mock Subject", code, new java.sql.Timestamp(System.currentTimeMillis()),
                    new java.sql.Timestamp(System.currentTimeMillis() + 3600000));

        try {
            // 1. Check if Subject Exists
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM subjects WHERE access_code = ?");
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                common.Subject sub = new common.Subject(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("access_code"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"));

                boolean isPublished = rs.getBoolean("is_published");
                if (!isPublished) {
                    throw new Exception("This exam is currently a DRAFT and has not been published.");
                }

                // 2. Check strict submission (Student Submission Table)
                PreparedStatement psCheck = connection.prepareStatement(
                        "SELECT count(*) FROM student_submissions WHERE student_id = ? AND subject_id = ?");
                psCheck.setInt(1, studentId);
                psCheck.setInt(2, sub.getId());
                ResultSet rsCheck = psCheck.executeQuery();
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    // Strict check
                    throw new Exception("You have already submitted this quiz. Contact Admin to retake.");
                }

                return sub;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User authenticate(String username, String password) {
        if (useMock)
            return new User(1, username, "STUDENT", "Mock User", "CS");

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

            // Check Teacher (Creator)
            PreparedStatement psTeach = connection
                    .prepareStatement("SELECT * FROM teachers WHERE username = ? AND password = ?");
            psTeach.setString(1, username);
            psTeach.setString(2, password);
            ResultSet rsT = psTeach.executeQuery();
            if (rsT.next()) {
                // Role CREATOR
                return new User(rsT.getInt("id"), rsT.getString("username"), "CREATOR", rsT.getString("full_name"),
                        "Exam Creator");
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
                // u.setScore(...) - Score is now per subject, so this legacy global score is
                // less relevant but we can keep it
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
                // Simple score fetch for display (sum of all?) or legacy
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
            // Delete submissions for this student allow retake of ALL
            PreparedStatement ps = connection.prepareStatement("DELETE FROM student_submissions WHERE student_id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();

            // Also reset legacy
            PreparedStatement ps2 = connection
                    .prepareStatement("UPDATE students SET has_submitted = FALSE, score = 0 WHERE id = ?");
            ps2.setInt(1, id);
            return ps2.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int calculateScore(int studentId, int subjectId, Map<Integer, String> answers) {
        int score = 0;
        Map<Integer, String> correctAnswers = new HashMap<>();

        // Fetch Correct Answers for Subject
        try {
            PreparedStatement ps = connection
                    .prepareStatement("SELECT id, correct_option FROM questions WHERE subject_id = ?");
            ps.setInt(1, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                correctAnswers.put(rs.getInt("id"), rs.getString("correct_option"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (Map.Entry<Integer, String> entry : answers.entrySet()) {
            String correct = correctAnswers.get(entry.getKey());
            if (correct != null && correct.equalsIgnoreCase(entry.getValue())) {
                score++;
            }
        }

        // SAVE TO DB (student_submissions)
        if (!useMock) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO student_submissions (student_id, subject_id, score) VALUES (?, ?, ?)");
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                ps.setInt(3, score);
                ps.executeUpdate();

                // Legacy Update
                PreparedStatement psLegacy = connection
                        .prepareStatement("UPDATE students SET score = score + ?, has_submitted = TRUE WHERE id = ?");
                psLegacy.setInt(1, score);
                psLegacy.setInt(2, studentId);
                psLegacy.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return score;
    }

    // New Creator Methods
    public boolean addSubject(String name, String code, Timestamp start, Timestamp end, int creatorId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO subjects (name, access_code, start_time, end_time, created_by, is_published) VALUES (?, ?, ?, ?, ?, FALSE)");
            ps.setString(1, name);
            ps.setString(2, code);
            ps.setTimestamp(3, start);
            ps.setTimestamp(4, end);
            ps.setInt(5, creatorId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean publishSubject(int subjectId) {
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE subjects SET is_published = TRUE WHERE id = ?");
            ps.setInt(1, subjectId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean addQuestion(int subjectId, String text, String a, String b, String c, String d, String correct) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO questions (subject_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES (?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, subjectId);
            ps.setString(2, text);
            ps.setString(3, a);
            ps.setString(4, b);
            ps.setString(5, c);
            ps.setString(6, d);
            ps.setString(7, correct);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Question> getQuestions(int subjectId) {
        List<Question> list = new ArrayList<>();
        if (useMock) {
            list.add(new Question(1, "What is the capital of France?", "London", "Berlin", "Paris", "Madrid"));
            return list;
        }

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM questions WHERE subject_id = ?");
            ps.setInt(1, subjectId);
            ResultSet rs = ps.executeQuery();
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
    }

    public List<common.Subject> getAllSubjects() {
        List<common.Subject> list = new ArrayList<>();
        if (useMock)
            return list;
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM subjects");
            while (rs.next()) {
                // Assuming Subject model update: id, name, code, start, end, isPublished
                list.add(new common.Subject(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("access_code"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getBoolean("is_published")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<common.Subject> getSubjectsByCreator(int creatorId) {
        List<common.Subject> list = new ArrayList<>();
        if (useMock) return list;
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM subjects WHERE created_by = ?");
            ps.setInt(1, creatorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new common.Subject(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("access_code"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getBoolean("is_published")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
