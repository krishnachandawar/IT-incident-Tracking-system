package web;

import access.DatabaseConnections;
import access.Role;
import access.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class IncidentService {
    public static final String[] ISSUE_TYPES = {
        "Operating system", "File system", "Application", "Software", "Account access",
        "Security", "Email connectivity", "Network connectivity", "Company codebase",
        "Database connection", "Hardware", "Printer", "VPN access", "Performance",
        "Backup and recovery", "Permissions", "Other"
    };

    public String createTicket(UserAccount user, String issueType, String description)
            throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("User must be logged in.");
        }
        if (issueType == null || issueType.trim().isEmpty()) {
            throw new IllegalArgumentException("Issue type must not be empty.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description must not be empty.");
        }

        String tokenId = "TKN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String validIssueType = issueType.trim();
        String validDescription = description.trim();

        String insertTicket = "INSERT INTO tickets "
                + "(token_id, requester_username, employee_id, user_department, designation, "
                + "issue_type, description, issue_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Open')";
        String insertUpdate = "INSERT INTO ticket_updates "
                + "(token_id, actor_username, actor_role, status, estimated_minutes, message) "
                + "VALUES (?, ?, 'USER', 'Open', NULL, 'Issue registered')";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.USER)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ticketStmt = connection.prepareStatement(insertTicket);
                 PreparedStatement updateStmt = connection.prepareStatement(insertUpdate)) {
                ticketStmt.setString(1, tokenId);
                ticketStmt.setString(2, user.getUsername());
                ticketStmt.setString(3, user.getEmployeeId());
                ticketStmt.setString(4, user.getDepartment());
                ticketStmt.setString(5, user.getDesignation());
                ticketStmt.setString(6, validIssueType);
                ticketStmt.setString(7, validDescription);
                ticketStmt.executeUpdate();

                updateStmt.setString(1, tokenId);
                updateStmt.setString(2, user.getUsername());
                updateStmt.executeUpdate();

                recordActivity(connection, tokenId, user, "ISSUE_REGISTERED", "Open", null,
                        "Issue registered: " + validIssueType + ".");

                connection.commit();
                return tokenId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public List<Map<String, Object>> getTicketsForUser(String username) throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, issue_status, technician_id, "
                + "technician_department, estimated_minutes, created_at, updated_at "
                + "FROM tickets WHERE requester_username = ? ORDER BY created_at DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.USER);
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapTicketRow(rs));
                }
                return list;
            }
        }
    }

    public List<Map<String, Object>> getOpenTickets(String issueTypeFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT token_id, requester_username, employee_id, user_department, designation, "
                + "issue_type, description, issue_status, technician_id, technician_department, "
                + "estimated_minutes, created_at, updated_at FROM tickets WHERE issue_status = 'Open'");
        if (issueTypeFilter != null && !issueTypeFilter.trim().isEmpty()
                && !"ALL".equalsIgnoreCase(issueTypeFilter.trim())) {
            sql.append(" AND issue_type = ?");
        }
        sql.append(" ORDER BY created_at ASC");

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN);
             PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            if (issueTypeFilter != null && !issueTypeFilter.trim().isEmpty()
                    && !"ALL".equalsIgnoreCase(issueTypeFilter.trim())) {
                stmt.setString(1, issueTypeFilter.trim());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> ticket = mapTicketRow(rs);
                    int priority = calculatePriorityScore(
                            (String) ticket.get("issue_type"),
                            (String) ticket.get("description"),
                            (String) ticket.get("designation"));
                    ticket.put("priority_score", priority);
                    list.add(ticket);
                }
                // Sort descending by priority score
                list.sort((a, b) -> Integer.compare(
                        (Integer) b.get("priority_score"),
                        (Integer) a.get("priority_score")));
                return list;
            }
        }
    }

    public List<Map<String, Object>> getAssignedTickets(String technicianEmployeeId) throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, issue_status, technician_id, "
                + "technician_department, estimated_minutes, created_at, updated_at "
                + "FROM tickets WHERE technician_id = ? AND issue_status <> 'Resolved' "
                + "ORDER BY created_at ASC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN);
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, technicianEmployeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapTicketRow(rs));
                }
                return list;
            }
        }
    }

    public List<Map<String, Object>> getUnresolvedTickets() throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, issue_status, technician_id, "
                + "technician_department, estimated_minutes, created_at, updated_at "
                + "FROM tickets WHERE issue_status <> 'Resolved' ORDER BY created_at ASC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapTicketRow(rs));
            }
            return list;
        }
    }

    public List<Map<String, Object>> getAllTickets() throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, issue_status, technician_id, "
                + "technician_department, estimated_minutes, created_at, updated_at "
                + "FROM tickets ORDER BY created_at DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapTicketRow(rs));
            }
            return list;
        }
    }

    public List<Map<String, Object>> getTicketUpdates(String tokenId) throws SQLException {
        String sql = "SELECT update_id, token_id, actor_username, actor_role, status, "
                + "estimated_minutes, message, created_at FROM ticket_updates "
                + "WHERE token_id = ? ORDER BY update_id ASC";
        try (Connection connection = DatabaseConnections.openDefaultConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tokenId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("update_id", rs.getLong("update_id"));
                    map.put("token_id", rs.getString("token_id"));
                    map.put("actor_username", rs.getString("actor_username"));
                    map.put("actor_role", rs.getString("actor_role"));
                    map.put("status", rs.getString("status"));
                    map.put("estimated_minutes", rs.getObject("estimated_minutes"));
                    map.put("message", rs.getString("message"));
                    map.put("created_at", rs.getString("created_at"));
                    list.add(map);
                }
                return list;
            }
        }
    }

    public void claimTicket(UserAccount technician, String tokenId) throws SQLException {
        if (technician == null || technician.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException("Only technicians can claim tickets.");
        }
        String sql = "UPDATE tickets SET technician_id = ?, technician_department = ?, "
                + "issue_status = 'Allocated' WHERE token_id = ? AND issue_status = 'Open'";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, technician.getEmployeeId());
                    stmt.setString(2, technician.getDepartment());
                    stmt.setString(3, tokenId);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Ticket not found or it is no longer Open: " + tokenId);
                    }
                }
                recordTicketUpdate(connection, tokenId, technician, "Allocated", null,
                        "Claimed by technician " + technician.getEmployeeId()
                                + " (" + technician.getDepartment() + ").");
                recordActivity(connection, tokenId, technician, "ISSUE_CLAIMED", "Allocated", null,
                        "Claimed by technician " + technician.getEmployeeId()
                                + " (" + technician.getDepartment() + ").");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public void updateTicketAsTechnician(
            UserAccount technician,
            String tokenId,
            String status,
            int estimatedMinutes,
            String note) throws SQLException {
        if (technician == null || technician.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException("Only technicians can perform this action.");
        }
        if (!"In Progress".equals(status) && !"Waiting".equals(status)
                && !"Pending Manager Review".equals(status)) {
            throw new IllegalArgumentException(
                    "Technicians can only set status to In Progress, Waiting, or Pending Manager Review.");
        }
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("Update note must not be empty.");
        }

        String sql = "UPDATE tickets SET issue_status = ?, estimated_minutes = ? "
                + "WHERE token_id = ? AND technician_id = ? AND issue_status <> 'Resolved'";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, status);
                    stmt.setInt(2, estimatedMinutes);
                    stmt.setString(3, tokenId);
                    stmt.setString(4, technician.getEmployeeId());
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Ticket not found, resolved, or not assigned to you: " + tokenId);
                    }
                }
                recordTicketUpdate(connection, tokenId, technician, status, estimatedMinutes, note.trim());
                recordActivity(connection, tokenId, technician, "STATUS_UPDATED", status,
                        estimatedMinutes, note.trim());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public void updateTicketAsManager(
            UserAccount manager,
            String tokenId,
            String status,
            int estimatedMinutes,
            String note) throws SQLException {
        if (manager == null || manager.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Only managers can perform this action.");
        }
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("Manager update note must not be empty.");
        }

        boolean isResolving = "Resolved".equalsIgnoreCase(status);
        if (isResolving && estimatedMinutes != 0) {
            throw new IllegalArgumentException("Estimated minutes must be 0 when resolving a ticket.");
        }

        String sql = "UPDATE tickets SET issue_status = ?, estimated_minutes = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE token_id = ? AND issue_status <> 'Resolved'"
                + (isResolving ? " AND issue_status = 'Pending Manager Review'" : "");

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, isResolving ? "Resolved" : status);
                    if (isResolving) {
                        stmt.setNull(2, java.sql.Types.INTEGER);
                    } else {
                        stmt.setInt(2, estimatedMinutes);
                    }
                    stmt.setString(3, tokenId);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Cannot update ticket. If approving resolution, ticket must be in Pending Manager Review.");
                    }
                }
                recordTicketUpdate(connection, tokenId, manager, isResolving ? "Resolved" : status,
                        isResolving ? 0 : estimatedMinutes, note.trim());
                recordActivity(connection, tokenId, manager, "STATUS_UPDATED",
                        isResolving ? "Resolved" : status,
                        isResolving ? null : estimatedMinutes, note.trim());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public void reassignTicketAsManager(
            UserAccount manager,
            String tokenId,
            String newTechnicianId,
            String newDepartment,
            String note) throws SQLException {
        if (manager == null || manager.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Only managers can reassign tickets.");
        }
        if (newTechnicianId == null || newTechnicianId.trim().isEmpty()) {
            throw new IllegalArgumentException("Technician ID must not be empty.");
        }
        if (newDepartment == null || newDepartment.trim().isEmpty()) {
            throw new IllegalArgumentException("Technician department must not be empty.");
        }
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("Reassignment note must not be empty.");
        }

        String validTechId = newTechnicianId.trim();
        String validDept = newDepartment.trim();
        String validNote = note.trim();

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            // Verify technician exists in directory
            String checkSql = "SELECT 1 FROM technician_directory WHERE employee_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, validTechId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Technician ID " + validTechId
                                + " is not registered in the technician directory.");
                    }
                }
            }

            String updateSql = "UPDATE tickets SET technician_id = ?, technician_department = ?, "
                    + "issue_status = 'Allocated', updated_at = CURRENT_TIMESTAMP "
                    + "WHERE token_id = ? AND issue_status <> 'Resolved'";

            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, validTechId);
                    stmt.setString(2, validDept);
                    stmt.setString(3, tokenId);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Ticket not found or it is already resolved: " + tokenId);
                    }
                }
                String message = "Reassigned to technician " + validTechId
                        + " (" + validDept + "). " + validNote;
                recordTicketUpdate(connection, tokenId, manager, "Allocated", null, message);
                recordActivity(connection, tokenId, manager, "ISSUE_REASSIGNED", "Allocated", null, message);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public List<Map<String, Object>> getUserActivity(String username) throws SQLException {
        String sql = "SELECT activity_id, user_username, token_id, actor_username, actor_role, "
                + "event_type, status, estimated_minutes, details, created_at "
                + "FROM user_activity_log WHERE user_username = ? ORDER BY activity_id DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.USER);
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return mapActivityList(rs);
            }
        }
    }

    public List<Map<String, Object>> getTechnicianActivity(String technicianUsername)
            throws SQLException {
        String sql = "SELECT activity_id, technician_username, token_id, actor_username, actor_role, "
                + "event_type, status, estimated_minutes, details, created_at "
                + "FROM technician_activity_log WHERE technician_username = ? ORDER BY activity_id DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN);
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, technicianUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                return mapActivityList(rs);
            }
        }
    }

    public List<Map<String, Object>> getManagerActivity() throws SQLException {
        String sql = "SELECT activity_id, manager_scope, token_id, actor_username, actor_role, "
                + "event_type, status, estimated_minutes, details, created_at "
                + "FROM manager_activity_log ORDER BY activity_id DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return mapActivityList(rs);
        }
    }

    public List<Map<String, String>> getTechnicianDirectory() throws SQLException {
        String sql = "SELECT employee_id, username, department FROM technician_directory ORDER BY employee_id";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, String>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, String> item = new HashMap<>();
                item.put("employee_id", rs.getString("employee_id"));
                item.put("username", rs.getString("username"));
                item.put("department", rs.getString("department"));
                list.add(item);
            }
            return list;
        }
    }

    public int calculatePriorityScore(String issueType, String description, String designation) {
        int severity = getIssueSeverity(issueType, description);
        int rank = getDesignationRank(designation);
        return severity * 60 + rank * 40;
    }

    private int getIssueSeverity(String issueType, String description) {
        String type = normalize(issueType);
        String desc = normalize(description);

        if (type.contains("security")) {
            return 5;
        }
        if (desc.contains("data loss") || desc.contains("data lost")
                || desc.contains("lost data") || desc.contains("data corruption")) {
            return 4;
        }
        if (type.contains("backup and recovery")) {
            return 4;
        }
        if (type.contains("hardware")) {
            return 3;
        }
        if (type.contains("network connectivity") || type.contains("database connection")) {
            return 2;
        }
        return 1;
    }

    private int getDesignationRank(String designation) {
        String norm = normalize(designation);
        int sdeIndex = norm.indexOf("sde");
        if (sdeIndex >= 0 && sdeIndex + 3 < norm.length()) {
            char levelChar = norm.charAt(sdeIndex + 3);
            boolean hasOneDigit = !Character.isDigit(levelChar)
                    || sdeIndex + 4 == norm.length()
                    || !Character.isDigit(norm.charAt(sdeIndex + 4));
            if (Character.isDigit(levelChar) && hasOneDigit) {
                int level = Character.digit(levelChar, 10);
                if (level >= 1 && level <= 5) {
                    return 6 - level;
                }
            }
        }
        return 0;
    }

    private String normalize(String val) {
        return val == null ? "" : val.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> mapTicketRow(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("token_id", rs.getString("token_id"));
        map.put("requester_username", rs.getString("requester_username"));
        map.put("employee_id", rs.getString("employee_id"));
        map.put("user_department", rs.getString("user_department"));
        map.put("designation", rs.getString("designation"));
        map.put("issue_type", rs.getString("issue_type"));
        map.put("description", rs.getString("description"));
        map.put("issue_status", rs.getString("issue_status"));
        map.put("technician_id", rs.getString("technician_id"));
        map.put("technician_department", rs.getString("technician_department"));
        map.put("estimated_minutes", rs.getObject("estimated_minutes"));
        map.put("created_at", rs.getString("created_at"));
        map.put("updated_at", rs.getString("updated_at"));
        return map;
    }

    private List<Map<String, Object>> mapActivityList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> item = new HashMap<>();
            item.put("activity_id", rs.getLong("activity_id"));
            item.put("token_id", rs.getString("token_id"));
            item.put("actor_username", rs.getString("actor_username"));
            item.put("actor_role", rs.getString("actor_role"));
            item.put("event_type", rs.getString("event_type"));
            item.put("status", rs.getString("status"));
            item.put("estimated_minutes", rs.getObject("estimated_minutes"));
            item.put("details", rs.getString("details"));
            item.put("created_at", rs.getString("created_at"));
            list.add(item);
        }
        return list;
    }

    private void recordTicketUpdate(
            Connection connection,
            String tokenId,
            UserAccount account,
            String status,
            Integer estimatedMinutes,
            String message) throws SQLException {
        String sql = "INSERT INTO ticket_updates "
                + "(token_id, actor_username, actor_role, status, estimated_minutes, message) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tokenId);
            stmt.setString(2, account.getUsername());
            stmt.setString(3, account.getRole().name());
            stmt.setString(4, status);
            if (estimatedMinutes == null) {
                stmt.setNull(5, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(5, estimatedMinutes);
            }
            stmt.setString(6, message);
            stmt.executeUpdate();
        }
    }

    private void recordActivity(
            Connection connection,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        String requesterUsername = findRequester(connection, tokenId);
        insertActivityRow(connection, "user_activity_log", "user_username", requesterUsername,
                tokenId, actor, eventType, status, estimatedMinutes, details);
        insertActivityRow(connection, "manager_activity_log", "manager_scope", "ALL",
                tokenId, actor, eventType, status, estimatedMinutes, details);
        if (actor.getRole() == Role.TECHNICIAN) {
            insertActivityRow(connection, "technician_activity_log", "technician_username",
                    actor.getUsername(), tokenId, actor, eventType, status, estimatedMinutes, details);
        }
    }

    private String findRequester(Connection connection, String tokenId) throws SQLException {
        String sql = "SELECT requester_username FROM tickets WHERE token_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tokenId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return "system";
            }
        }
    }

    private void insertActivityRow(
            Connection connection,
            String table,
            String scopeCol,
            String scopeVal,
            String tokenId,
            UserAccount actor,
            String eventType,
            String status,
            Integer estimatedMinutes,
            String details) throws SQLException {
        String sql = "INSERT INTO " + table + " (" + scopeCol
                + ", token_id, actor_username, actor_role, event_type, status, "
                + "estimated_minutes, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, scopeVal);
            stmt.setString(2, tokenId);
            stmt.setString(3, actor.getUsername());
            stmt.setString(4, actor.getRole().name());
            stmt.setString(5, eventType);
            stmt.setString(6, status);
            if (estimatedMinutes == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, estimatedMinutes);
            }
            stmt.setString(8, details);
            stmt.executeUpdate();
        }
    }
}
