package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.io.File;

public class StudentImporter {

    public static void main(String[] args) {
        String csvFile = "students.csv"; // Assuming run from project root
        if (args.length > 0) {
            csvFile = args[0];
        }

        System.out.println("Starting import from: " + csvFile);

        File f = new File(csvFile);
        if (!f.exists()) {
            System.err.println("File not found: " + f.getAbsolutePath());
            return;
        }

        DatabaseManager dbManager = new DatabaseManager();
        Connection conn = dbManager.getConnection();

        if (conn == null) {
            System.err.println("Failed to connect to database.");
            return;
        }

        String sql = "INSERT INTO students (username, password, full_name, department) VALUES (?, ?, ?, ?)";
        int count = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile));
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                // Skip comments or empty lines
                if (line.trim().isEmpty() || line.startsWith("//")) {
                    continue;
                }

                String[] parts = line.split("\t");

                // Header check (heuristic: see if 'Default User Name' is present)
                if (parts.length > 0 && parts[0].equalsIgnoreCase("Default User Name")) {
                    continue;
                }

                if (parts.length < 9) {
                    System.err.println("Skipping malformed line " + lineNum + ": " + line);
                    continue;
                }

                String username = parts[0].trim();
                String password = parts[1].trim();
                String firstName = parts[2].trim();
                String fatherName = parts[3].trim();
                String gFatherName = parts[4].trim();
                String fullName = firstName + " " + fatherName + " " + gFatherName;
                String department = parts[8].trim();

                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.setString(3, fullName);
                pstmt.setString(4, department);

                try {
                    pstmt.executeUpdate();
                    count++;
                    // System.out.println("Imported: " + username);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1062) { // Duplicate entry
                        System.out.println("Skipping duplicate: " + username);
                    } else {
                        System.err.println("Error importing " + username + ": " + e.getMessage());
                    }
                }
            }
            System.out.println("Import completed. Total records added: " + count);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
