package access;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Verifies that Connector/J can reach the same MySQL server MySQL Workbench uses.
 *
 * Run from the {@code incident tracking} folder:
 * {@code java -cp "build/classes;lib/mysql-connector-j-9.2.0.jar" access.MysqlConnectionTest}
 */
public final class MysqlConnectionTest {
    private MysqlConnectionTest() {
    }

    public static void main(String[] args) {
        System.out.println("IT Incident Tracking - MySQL connection test");
        System.out.println("Driver:     " + DatabaseConnections.getDriverStatus());
        System.out.println("Config:     " + DatabaseConnections.getLoadedPropertiesPath());
        System.out.println("JDBC URL:   " + DatabaseConnections.getEffectiveUrl());
        System.out.println("DB user:    " + DatabaseConnections.resolveUsername("AUTH"));
        System.out.println();

        try (Connection connection = DatabaseConnections.openDefaultConnection();
                Statement statement = connection.createStatement()) {
            DatabaseMetaData meta = connection.getMetaData();
            System.out.println("Connected.");
            System.out.println("Server:     " + meta.getDatabaseProductName()
                    + " " + meta.getDatabaseProductVersion());
            System.out.println("Driver JAR: " + meta.getDriverName() + " " + meta.getDriverVersion());
            System.out.println("Session:    " + queryScalar(statement, "SELECT CURRENT_USER()"));
            System.out.println("Database:   " + queryScalar(statement, "SELECT DATABASE()"));
            System.out.println();
            System.out.println("Tables in incident_tracking:");
            try (ResultSet tables = statement.executeQuery("SHOW TABLES")) {
                int count = 0;
                while (tables.next()) {
                    count++;
                    System.out.println("  - " + tables.getString(1));
                }
                if (count == 0) {
                    System.out.println("  (none) Run database inserts/"
                            + "incident_tracking_workbench_setup.sql in Workbench first.");
                }
            }
            System.out.println();
            System.out.println("Connection test succeeded. Workbench and the app can use this server.");
        } catch (Exception e) {
            System.out.println("Connection test failed.");
            System.out.println(e.getMessage());
            System.out.println();
            System.out.println("Check that:");
            System.out.println("  1. MySQL Server is running (Windows Services: MySQL80).");
            System.out.println("  2. You created a Workbench connection to localhost:3306.");
            System.out.println("  3. You executed incident_tracking_workbench_setup.sql as root.");
            System.out.println("  4. db.properties uses user incident_user and password Incident@123.");
            System.exit(1);
        }
    }

    private static String queryScalar(Statement statement, String sql) throws Exception {
        try (ResultSet results = statement.executeQuery(sql)) {
            if (results.next()) {
                return results.getString(1);
            }
            return "(empty)";
        }
    }
}
