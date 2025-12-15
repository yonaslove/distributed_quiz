import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class FixSubmission {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/distributed_quiz";
        String user = "root";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // 1. Update student_submissions
            // Move submission 20 from Subject 23 (Civics) to Subject 13 (AI) and set Score
            // to 1
            String sqlSub = "UPDATE student_submissions SET subject_id = 13, score = 1 WHERE id = 20";
            try (PreparedStatement ps = conn.prepareStatement(sqlSub)) {
                int rows = ps.executeUpdate();
                System.out.println("Updated student_submissions: " + rows + " rows.");
            }

            // 2. Update students table (Global Score)
            // Assuming previous score was 0, now it is 1. So add 1.
            String sqlStud = "UPDATE students SET score = score + 1 WHERE id = 3422";
            try (PreparedStatement ps = conn.prepareStatement(sqlStud)) {
                int rows = ps.executeUpdate();
                System.out.println("Updated students global score: " + rows + " rows.");
            }

            System.out.println("Fix applied successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
