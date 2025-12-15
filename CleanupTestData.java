import server.DatabaseManager;
import java.sql.Connection;
import java.sql.Statement;

public class CleanupTestData {
    public static void main(String[] args) {
        try {
            DatabaseManager db = new DatabaseManager();
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();

            System.out.println("Cleaning up test data...");

            // 1. Delete Results for test users
            int deletedResults = stmt.executeUpdate(
                    "DELETE FROM results WHERE user_id IN (SELECT id FROM students WHERE username LIKE 'testuser%')");
            System.out.println("Deleted " + deletedResults + " test results.");

            // 2. Delete Submissions for test users
            int deletedSubmissions = stmt.executeUpdate(
                    "DELETE FROM student_submissions WHERE student_id IN (SELECT id FROM students WHERE username LIKE 'testuser%')");
            System.out.println("Deleted " + deletedSubmissions + " test submissions.");

            // 3. Delete Questions for test subjects
            int deletedQuestions = stmt.executeUpdate(
                    "DELETE FROM questions WHERE subject_id IN (SELECT id FROM subjects WHERE access_code LIKE 'TEST%')");
            System.out.println("Deleted " + deletedQuestions + " test questions.");

            // 4. Delete Test Subjects
            int deletedSubjects = stmt.executeUpdate(
                    "DELETE FROM subjects WHERE access_code LIKE 'TEST%'");
            System.out.println("Deleted " + deletedSubjects + " test subjects.");

            // 5. Delete Test Students
            int deletedStudents = stmt.executeUpdate(
                    "DELETE FROM students WHERE username LIKE 'testuser%'");
            System.out.println("Deleted " + deletedStudents + " test students.");

            System.out.println("Cleanup Complete.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
