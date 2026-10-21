package access;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseConnections {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/incident_tracking?serverTimezone=UTC";

    private DatabaseConnections() {
    }

    public static Connection openAuthenticationConnection() throws SQLException {
        return openConnection("AUTH");
    }

    public static Connection openRoleConnection(Role role) throws SQLException {
        Objects.requireNonNull(role, "role must not be null");
        return openConnection(role.name());
    }

    private static Connection openConnection(String accountType) throws SQLException {
        String url = environment("INCIDENT_DB_URL", DEFAULT_URL);
        String username = requiredEnvironment("INCIDENT_DB_" + accountType + "_USER");
        String password = requiredEnvironment("INCIDENT_DB_" + accountType + "_PASSWORD");
        return DriverManager.getConnection(url, username, password);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Required database environment variable is not set: " + name);
        }
        return value;
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
