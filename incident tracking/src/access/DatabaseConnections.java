package access;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public final class DatabaseConnections {
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/incident_tracking"
                    + "?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DEFAULT_USER = "incident_user";
    private static final String DEFAULT_PASSWORD = "Incident@123";

    private static final Properties PROPERTIES = new Properties();
    private static String loadedPropertiesPath = "(defaults / environment only)";
    private static String driverStatus = "not loaded";

    static {
        loadDriver();
        loadProperties();
    }

    private DatabaseConnections() {
    }

    private static void loadDriver() {
        try {
            Class.forName(JDBC_DRIVER);
            driverStatus = JDBC_DRIVER + " loaded";
        } catch (ClassNotFoundException e) {
            driverStatus = "MISSING: add lib/mysql-connector-j-9.2.0.jar to the classpath";
        }
    }

    private static void loadProperties() {
        File[] candidates = new File[] {
            new File("db.properties"),
            new File("incident tracking/db.properties"),
            new File("../db.properties"),
            new File("incident tracking/../db.properties")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                try (InputStream in = new FileInputStream(candidate)) {
                    PROPERTIES.load(in);
                    loadedPropertiesPath = candidate.getAbsolutePath();
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        try (InputStream in = DatabaseConnections.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                PROPERTIES.load(in);
                loadedPropertiesPath = "classpath:/db.properties";
            }
        } catch (Exception ignored) {
        }
    }

    public static Connection openAuthenticationConnection() throws SQLException {
        return openConnection("AUTH");
    }

    public static Connection openRoleConnection(Role role) throws SQLException {
        Objects.requireNonNull(role, "role must not be null");
        return openConnection(role.name());
    }

    public static Connection openDefaultConnection() throws SQLException {
        return openConnection("AUTH");
    }

    private static Connection openConnection(String accountType) throws SQLException {
        ensureDriverPresent();
        String url = getEffectiveUrl();
        String username = resolveUsername(accountType);
        String password = resolvePassword(accountType);
        return DriverManager.getConnection(url, username, password);
    }

    private static void ensureDriverPresent() throws SQLException {
        if (driverStatus.startsWith("MISSING")) {
            throw new SQLException(
                    "MySQL Connector/J is not on the classpath. "
                            + "Place mysql-connector-j-9.2.0.jar in incident tracking/lib "
                            + "and rebuild the project.");
        }
    }

    public static String getEffectiveUrl() {
        String url = getEnvOrProp("INCIDENT_DB_URL", "db.url");
        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_URL;
        }
        return url.trim();
    }

    public static String resolveUsername(String accountType) {
        String envRoleUser = System.getenv("INCIDENT_DB_" + accountType + "_USER");
        if (envRoleUser != null && !envRoleUser.trim().isEmpty()) {
            return envRoleUser.trim();
        }

        String propRoleUser = PROPERTIES.getProperty("db." + accountType.toLowerCase() + ".user");
        if (propRoleUser != null && !propRoleUser.trim().isEmpty()) {
            return propRoleUser.trim();
        }

        String generalEnvUser = System.getenv("INCIDENT_DB_USER");
        if (generalEnvUser == null || generalEnvUser.trim().isEmpty()) {
            generalEnvUser = System.getenv("DB_USER");
        }
        if (generalEnvUser != null && !generalEnvUser.trim().isEmpty()) {
            return generalEnvUser.trim();
        }

        String generalPropUser = PROPERTIES.getProperty("db.user");
        if (generalPropUser != null && !generalPropUser.trim().isEmpty()) {
            return generalPropUser.trim();
        }

        return DEFAULT_USER;
    }

    public static String resolvePassword(String accountType) {
        String envRolePass = System.getenv("INCIDENT_DB_" + accountType + "_PASSWORD");
        if (envRolePass != null) {
            return envRolePass;
        }

        String propRolePass = PROPERTIES.getProperty("db." + accountType.toLowerCase() + ".password");
        if (propRolePass != null) {
            return propRolePass;
        }

        String generalEnvPass = System.getenv("INCIDENT_DB_PASSWORD");
        if (generalEnvPass == null) {
            generalEnvPass = System.getenv("DB_PASSWORD");
        }
        if (generalEnvPass != null) {
            return generalEnvPass;
        }

        String generalPropPass = PROPERTIES.getProperty("db.password");
        if (generalPropPass != null) {
            return generalPropPass;
        }

        return DEFAULT_PASSWORD;
    }

    private static String getEnvOrProp(String envName, String propName) {
        String val = System.getenv(envName);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        return PROPERTIES.getProperty(propName);
    }

    public static String getDriverStatus() {
        return driverStatus;
    }

    public static String getLoadedPropertiesPath() {
        return loadedPropertiesPath;
    }

    public static boolean testConnection() {
        try (Connection conn = openDefaultConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }
}
