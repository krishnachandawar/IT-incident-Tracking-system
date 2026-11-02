package access;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public final class AuthenticationService {
    public void registerUser(
            String username,
            String password,
            String employeeId,
            String department,
            String designation) throws SQLException {
        registerUser(username, password, employeeId, department, designation, "USER");
    }

    public void registerUser(
            String username,
            String password,
            String employeeId,
            String department,
            String designation,
            String requestedRole) throws SQLException {
        String validUsername = requireText(username, "Username");
        String validPassword = requirePassword(password);
        String validEmployeeId = requireText(employeeId, "Employee ID");
        String validDepartment = requireText(department, "Department");
        String validDesignation = requireText(designation, "Designation");
        String role = requireRole(requestedRole);
        String approvalStatus = "USER".equals(role) ? "APPROVED" : "PENDING";
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(validPassword, salt);
        String sql = "INSERT INTO app_users "
                + "(username, password_salt, password_hash, employee_id, department, "
                + "designation, role, approval_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnections.openAuthenticationConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                statement.setString(1, validUsername);
                statement.setString(2, salt);
                statement.setString(3, hash);
                statement.setString(4, validEmployeeId);
                statement.setString(5, validDepartment);
                statement.setString(6, validDesignation);
                statement.setString(7, role);
                statement.setString(8, approvalStatus);
                statement.executeUpdate();
                ActivityHistory.recordRegistration(connection,
                        new UserAccount(validUsername, validEmployeeId, validDepartment,
                                validDesignation, Role.fromDatabaseValue(role)));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                throw new IllegalArgumentException(
                        "That username or employee ID is already registered.", e);
            }
            throw e;
        }
    }

    public UserAccount authenticate(String username, String password) throws SQLException {
        String validUsername = requireText(username, "Username");
        String validPassword = requirePassword(password);
        String sql = "SELECT username, password_salt, password_hash, employee_id, "
                + "department, designation, role, approval_status FROM app_users WHERE username = ?";

        try (Connection connection = DatabaseConnections.openAuthenticationConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, validUsername);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !PasswordHasher.matches(
                                validPassword,
                                result.getString("password_salt"),
                                result.getString("password_hash"))
                        || !"APPROVED".equals(result.getString("approval_status"))) {
                    return null;
                }

                return new UserAccount(
                        result.getString("username"),
                        result.getString("employee_id"),
                        result.getString("department"),
                        result.getString("designation"),
                        Role.fromDatabaseValue(result.getString("role")));
            }

        }
    }

    private static String requireRole(String requestedRole) {
        String role = requestedRole == null ? "USER" : requestedRole.trim().toUpperCase();
        if (!role.equals("USER") && !role.equals("TECHNICIAN") && !role.equals("MANAGER")) {
            throw new IllegalArgumentException(
                    "Registration role must be USER, TECHNICIAN, or MANAGER.");
        }
        return role;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty.");
        }
        return trimmed;
    }

    private static String requirePassword(String password) {
        Objects.requireNonNull(password, "Password must not be null");
        if (password.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters long.");
        }
        return password;
    }
}
