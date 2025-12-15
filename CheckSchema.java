import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckSchema {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/distributed_quiz";
        String user = "root";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password);
                Statement stmt = conn.createStatement()) {

            System.out.println("=== Checking Indexes on student_submissions ===");
            try (ResultSet rs = stmt.executeQuery("SHOW INDEX FROM student_submissions")) {
                while (rs.next()) {
                    System.out.println("Index: " + rs.getString("Key_name") +
                            " | Column: " + rs.getString("Column_name") +
                            " | Non_unique: " + rs.getInt("Non_unique"));
                }
            }

            System.out.println("\n=== Checking for Duplicate Submissions ===");
            String checkDupNum = "SELECT student_id, subject_id, COUNT(*) as cnt, MAX(score) as max_score " +
                    "FROM student_submissions " +
                    "GROUP BY student_id, subject_id " +
                    "HAVING cnt > 1";
            try (ResultSet rs = stmt.executeQuery(checkDupNum)) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("DUPLICATE FOUND: Student %d | Subject %d | Count: %d | MaxScore: %d%n",
                            rs.getInt("student_id"), rs.getInt("subject_id"), rs.getInt("cnt"), rs.getInt("max_score"));
                }
                if (!found)
                    System.out.println("No duplicates found.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
