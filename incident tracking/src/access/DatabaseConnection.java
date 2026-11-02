package access;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Fill these in after you send the Workbench / MySQL server details.
    private static final String url = "jdbc:mysql://YOUR_HOST:YOUR_PORT/YOUR_DATABASE_NAME";
    private static final String user = "YOUR_USERNAME";
    private static final String password = "YOUR_PASSWORD";

    public static void main(String[] args) {
        System.out.println("Connecting to database...");
        System.out.println("URL:  " + url);
        System.out.println("User: " + user);

        if (url.contains("YOUR_HOST") || "YOUR_USERNAME".equals(user) || "YOUR_PASSWORD".equals(password)) {
            System.err.println("Credentials are still placeholders.");
            System.err.println("Send host, port, database name, username, and password so they can be put here.");
            return;
        }

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            System.out.println("Database connected successfully!");
        } catch (SQLException e) {
            System.err.println("Connection failed!");
            e.printStackTrace();
        }
    }
}
