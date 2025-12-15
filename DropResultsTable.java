import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DropResultsTable {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/distributed_quiz";
        String user = "root";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password);
                Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS results");
            System.out.println("Dropped 'results' table successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
