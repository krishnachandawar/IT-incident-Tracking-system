package access;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class ActivityHistory {
    private ActivityHistory() {
    }

    static void record(
            Connection connection,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        String requesterUsername = findRequester(connection, tokenId);
        insertUserHistory(
                connection, requesterUsername, tokenId, actor, eventType, status,
                estimatedMinutes, details);
        insertManagerHistory(
                connection, tokenId, actor, eventType, status, estimatedMinutes, details);
        if (actor.getRole() == Role.TECHNICIAN) {
            insertTechnicianHistory(
                    connection, actor, tokenId, eventType, status, estimatedMinutes, details);
        }
    }

    static void recordRegistration(
            Connection connection, UserAccount account) throws SQLException {
        insertUserHistory(
                connection, account.getUsername(), null, account, "ACCOUNT_REGISTERED",
                null, null, "User account registered.");
        insertManagerHistory(
                connection, null, account, "ACCOUNT_REGISTERED",
                null, null, "User account registered.");
    }

    static void showUserHistory(Connection connection, String username) throws SQLException {
        String sql = "SELECT created_at, event_type, token_id, actor_username, actor_role, "
                + "status, estimated_minutes, details FROM user_activity_log "
                + "WHERE user_username = ? ORDER BY activity_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            printRows(statement, "No user activity has been recorded.");
        }
    }

    static void showTechnicianHistory(Connection connection, String username)
            throws SQLException {
        String sql = "SELECT created_at, event_type, token_id, actor_username, actor_role, "
                + "status, estimated_minutes, details FROM technician_activity_log "
                + "WHERE technician_username = ? ORDER BY activity_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            printRows(statement, "No technician activity has been recorded for this account.");
        }
    }

    static void showManagerHistory(Connection connection) throws SQLException {
        String sql = "SELECT created_at, event_type, token_id, actor_username, actor_role, "
                + "status, estimated_minutes, details FROM manager_activity_log "
                + "ORDER BY activity_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            printRows(statement, "No manager audit activity has been recorded.");
        }
    }

    private static String findRequester(Connection connection, String tokenId)
            throws SQLException {
        String sql = "SELECT requester_username FROM tickets WHERE token_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Ticket not found: " + tokenId);
                }
                return result.getString(1);
            }
        }
    }

    private static void insertUserHistory(
            Connection connection,
            String userUsername,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        insert(connection, "user_activity_log", "user_username", userUsername,
                tokenId, actor, eventType, status, estimatedMinutes, details);
    }

    private static void insertTechnicianHistory(
            Connection connection,
            UserAccount actor,
            String tokenId,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        insert(connection, "technician_activity_log", "technician_username",
                actor.getUsername(), tokenId, actor, eventType, status, estimatedMinutes, details);
    }

    private static void insertManagerHistory(
            Connection connection,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        insert(connection, "manager_activity_log", "manager_scope", "ALL",
                tokenId, actor, eventType, status, estimatedMinutes, details);
    }

    private static void insert(
            Connection connection,
            String table,
            String scopeColumn,
            String scopeValue,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        String sql = "INSERT INTO " + table + " (" + scopeColumn
                + ", token_id, actor_username, actor_role, event_type, status, "
                + "estimated_minutes, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scopeValue);
            statement.setString(2, tokenId);
            statement.setString(3, actor.getUsername());
            statement.setString(4, actor.getRole().name());
            statement.setString(5, eventType);
            statement.setString(6, status);
            if (estimatedMinutes == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, estimatedMinutes);
            }
            statement.setString(8, details);
            statement.executeUpdate();
        }
    }

    private static void printRows(PreparedStatement statement, String emptyMessage)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            boolean found = false;
            while (results.next()) {
                found = true;
                System.out.printf("%s | %s | ticket: %s | actor: %s (%s) | status: %s "
                                + "| ETA: %s | %s%n",
                        results.getTimestamp("created_at"),
                        results.getString("event_type"),
                        results.getString("token_id") == null
                                ? "-" : results.getString("token_id"),
                        results.getString("actor_username"),
                        results.getString("actor_role"),
                        results.getString("status") == null
                                ? "-" : results.getString("status"),
                        formatEstimate((Integer) results.getObject("estimated_minutes")),
                        results.getString("details"));
            }
            if (!found) {
                System.out.println(emptyMessage);
            }
        }
    }

    private static String formatEstimate(Integer minutes) {
        if (minutes == null) {
            return "-";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (hours == 0) {
            return remainingMinutes + " minutes";
        }
        if (remainingMinutes == 0) {
            return hours + " hours";
        }
        return hours + " hours " + remainingMinutes + " minutes";
    }
}
