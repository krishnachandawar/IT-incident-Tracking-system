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

    // -------------------------------------------------------------------------
    // Priority calculation
    // -------------------------------------------------------------------------

    /**
     * Determines the priority level label ("Critical", "High", "Medium", "Low")
     * for a ticket based on issue type, description keywords, and requester
     * designation.
     */
    public String calculatePriorityLevel(String issueType, String description, String designation) {
        String type = normalize(issueType);
        String desc = normalize(description);
        String desig = normalize(designation);

        // --- Critical ---
        if (type.contains("security")
                || desc.contains("data loss")
                || desc.contains("data corruption")
                || desc.contains("system down")
                || desc.contains("production down")) {
            return "Critical";
        }

        // --- Designation forces at-minimum High ---
        boolean seniorDesignation = desig.contains("ceo") || desig.contains("cto")
                || desig.contains("vp") || desig.contains("director")
                || desig.contains("head");

        // --- High by issue type ---
        if (type.contains("hardware")
                || type.contains("backup and recovery")
                || type.contains("vpn access")) {
            return "High";
        }

        // --- High by designation rank (SDE1/SDE2 = high, CxO/VP/Director also caught above) ---
        int sdeLevel = parseSdeLevel(desig);
        if (sdeLevel == 1 || sdeLevel == 2) {
            return "High";
        }

        if (seniorDesignation) {
            return "High";
        }

        // --- Medium ---
        if (type.contains("network connectivity")
                || type.contains("database connection")
                || type.contains("company codebase")
                || type.contains("performance")) {
            return "Medium";
        }

        // --- Low: everything else ---
        return "Low";
    }

    /**
     * Numeric priority score used for secondary sorting within getOpenTickets.
     * Not the canonical priority label — use calculatePriorityLevel for that.
     */
    public int calculatePriorityScore(String issueType, String description, String designation) {
        int severity = getIssueSeverity(issueType, description);
        int rank = getDesignationRank(designation);
        return severity * 60 + rank * 40;
    }

    // -------------------------------------------------------------------------
    // Ticket creation
    // -------------------------------------------------------------------------

    /**
     * Creates a new ticket. Priority level is auto-computed unless
     * {@code priorityOverride} is non-null (must be one of "Critical", "High",
     * "Medium", "Low").
     *
     * @param user             the logged-in user
     * @param issueType        one of the recognised issue type strings
     * @param description      free-text problem description
     * @param priorityOverride if non-null, overrides the computed priority level
     * @return the new token ID
     */
    public String createTicket(UserAccount user, String issueType, String description,
            String priorityOverride) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("User must be logged in.");
        }
        if (issueType == null || issueType.trim().isEmpty()) {
            throw new IllegalArgumentException("Issue type must not be empty.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description must not be empty.");
        }

        String validIssueType = issueType.trim();
        String validDescription = description.trim();

        String priority;
        if (priorityOverride != null && !priorityOverride.trim().isEmpty()) {
            String po = priorityOverride.trim();
            if (!po.equals("Critical") && !po.equals("High")
                    && !po.equals("Medium") && !po.equals("Low")) {
                throw new IllegalArgumentException(
                        "Priority override must be Critical, High, Medium, or Low.");
            }
            priority = po;
        } else {
            priority = calculatePriorityLevel(validIssueType, validDescription,
                    user.getDesignation());
        }

        String tokenId = "TKN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        String insertTicket = "INSERT INTO tickets "
                + "(token_id, requester_username, employee_id, user_department, designation, "
                + "issue_type, description, priority_level, issue_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Open')";
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
                ticketStmt.setString(8, priority);
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

    // -------------------------------------------------------------------------
    // Ticket queries
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> getTicketsForUser(String username) throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, priority_level, issue_status, technician_id, "
                + "technician_department, estimated_minutes, reissued_count, closed_by, "
                + "created_at, updated_at "
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
                + "issue_type, description, priority_level, issue_status, technician_id, "
                + "technician_department, estimated_minutes, reissued_count, closed_by, "
                + "created_at, updated_at FROM tickets WHERE issue_status = 'Open'");
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

    /**
     * Returns all Open tickets for technicians to claim, sorted by priority
     * level (Critical → High → Medium → Low) then by creation time ascending.
     */
    public List<Map<String, Object>> getOpenTicketsForTechnician() throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, priority_level, issue_status, "
                + "technician_id, technician_department, estimated_minutes, reissued_count, "
                + "closed_by, created_at, updated_at "
                + "FROM tickets WHERE issue_status = 'Open' ORDER BY created_at ASC";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.TECHNICIAN);
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapTicketRow(rs));
            }
            // Sort: Critical(0) > High(1) > Medium(2) > Low(3), then created_at ASC
            list.sort((a, b) -> {
                int pa = priorityOrdinal((String) a.get("priority_level"));
                int pb = priorityOrdinal((String) b.get("priority_level"));
                if (pa != pb) {
                    return Integer.compare(pa, pb); // lower ordinal = higher priority
                }
                // created_at as String — ISO-format strings sort lexicographically correctly
                String ca = (String) a.get("created_at");
                String cb = (String) b.get("created_at");
                if (ca == null && cb == null) return 0;
                if (ca == null) return 1;
                if (cb == null) return -1;
                return ca.compareTo(cb);
            });
            return list;
        }
    }

    public List<Map<String, Object>> getAssignedTickets(String technicianEmployeeId)
            throws SQLException {
        String sql = "SELECT token_id, requester_username, employee_id, user_department, "
                + "designation, issue_type, description, priority_level, issue_status, "
                + "technician_id, technician_department, estimated_minutes, reissued_count, "
                + "closed_by, created_at, updated_at "
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
                + "designation, issue_type, description, priority_level, issue_status, "
                + "technician_id, technician_department, estimated_minutes, reissued_count, "
                + "closed_by, created_at, updated_at "
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
                + "designation, issue_type, description, priority_level, issue_status, "
                + "technician_id, technician_department, estimated_minutes, reissued_count, "
                + "closed_by, created_at, updated_at "
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

    public List<Map<String, Object>> getTicketUpdates(UserAccount viewer, String tokenId)
            throws SQLException {
        if (viewer == null) {
            throw new IllegalArgumentException("User must be logged in.");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT u.update_id, u.token_id, u.actor_username, u.actor_role, u.status, "
                + "u.estimated_minutes, u.message, u.created_at FROM ticket_updates u "
                + "JOIN tickets t ON t.token_id = u.token_id WHERE u.token_id = ?");
        if (viewer.getRole() == Role.USER) {
            sql.append(" AND t.requester_username = ?");
        }
        sql.append(" ORDER BY u.update_id ASC");
        try (Connection connection = DatabaseConnections.openDefaultConnection();
             PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            stmt.setString(parameter++, tokenId);
            if (viewer.getRole() == Role.USER) {
                stmt.setString(parameter, viewer.getUsername());
            }
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

    // -------------------------------------------------------------------------
    // Technician actions
    // -------------------------------------------------------------------------

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
                recordTicketUpdate(connection, tokenId, technician, status, estimatedMinutes,
                        note.trim());
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

    // -------------------------------------------------------------------------
    // Manager actions
    // -------------------------------------------------------------------------

    public void updateTicketAsManager(
            UserAccount manager,
            String tokenId,
            String status,
            int estimatedMinutes,
            String note) throws SQLException {
        if (manager == null || !manager.getRole().isAdminOrManager()) {
            throw new IllegalArgumentException("Only managers or admins can perform this action.");
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
        if (manager == null || !manager.getRole().isAdminOrManager()) {
            throw new IllegalArgumentException("Only managers or admins can reassign tickets.");
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
                recordActivity(connection, tokenId, manager, "ISSUE_REASSIGNED", "Allocated", null,
                        message);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Admin actions
    // -------------------------------------------------------------------------

    /**
     * Force-closes a ticket as an admin or manager, bypassing the normal
     * Pending Manager Review workflow.
     */
    public void adminCloseTicket(UserAccount admin, String tokenId, String note)
            throws SQLException {
        if (admin == null || !admin.getRole().isAdminOrManager()) {
            throw new IllegalArgumentException("Only admins or managers can force-close tickets.");
        }
        if (tokenId == null || tokenId.trim().isEmpty()) {
            throw new IllegalArgumentException("Token ID must not be empty.");
        }

        String validNote = (note == null) ? "" : note.trim();

        String updateSql = "UPDATE tickets SET issue_status = 'Resolved', "
                + "closed_by = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE token_id = ? AND issue_status <> 'Resolved'";
        String auditSql = "INSERT INTO admin_audit_log "
                + "(admin_username, action, token_id, target_username, details) "
                + "VALUES (?, 'CLOSE_TICKET', ?, NULL, ?)";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, admin.getUsername());
                    stmt.setString(2, tokenId);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Ticket not found or already resolved: " + tokenId);
                    }
                }
                recordTicketUpdate(connection, tokenId, admin, "Resolved", null, validNote);
                recordActivity(connection, tokenId, admin, "ADMIN_CLOSED", "Resolved", null,
                        "Admin force-closed ticket. " + validNote);
                try (PreparedStatement auditStmt = connection.prepareStatement(auditSql)) {
                    auditStmt.setString(1, admin.getUsername());
                    auditStmt.setString(2, tokenId);
                    auditStmt.setString(3, "Force-closed ticket. " + validNote);
                    auditStmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Reopens a resolved ticket as an admin or manager, resetting it to Open
     * and clearing technician assignment.
     */
    public void adminReopenTicket(UserAccount admin, String tokenId, String note)
            throws SQLException {
        if (admin == null || !admin.getRole().isAdminOrManager()) {
            throw new IllegalArgumentException("Only admins or managers can reopen tickets.");
        }
        if (tokenId == null || tokenId.trim().isEmpty()) {
            throw new IllegalArgumentException("Token ID must not be empty.");
        }

        String validNote = (note == null) ? "" : note.trim();
        String updateMessage = "Ticket reopened/reissued by admin: " + validNote;

        String updateSql = "UPDATE tickets SET issue_status = 'Open', "
                + "technician_id = NULL, technician_department = NULL, "
                + "estimated_minutes = NULL, closed_by = NULL, "
                + "reissued_count = reissued_count + 1, "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE token_id = ? AND issue_status = 'Resolved'";
        String auditSql = "INSERT INTO admin_audit_log "
                + "(admin_username, action, token_id, target_username, details) "
                + "VALUES (?, 'REOPEN_TICKET', ?, NULL, ?)";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, tokenId);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "Ticket not found or not in Resolved status: " + tokenId);
                    }
                }
                recordTicketUpdate(connection, tokenId, admin, "Open", null, updateMessage);
                recordActivity(connection, tokenId, admin, "ADMIN_REOPENED", "Open", null,
                        updateMessage);
                try (PreparedStatement auditStmt = connection.prepareStatement(auditSql)) {
                    auditStmt.setString(1, admin.getUsername());
                    auditStmt.setString(2, tokenId);
                    auditStmt.setString(3, updateMessage);
                    auditStmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Returns all user accounts ordered by creation date descending.
     */
    public List<Map<String, Object>> adminGetAllUsers() throws SQLException {
        String sql = "SELECT username, employee_id, department, designation, role, created_at "
                + "FROM app_users ORDER BY created_at DESC";
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("username", rs.getString("username"));
                row.put("employee_id", rs.getString("employee_id"));
                row.put("department", rs.getString("department"));
                row.put("designation", rs.getString("designation"));
                row.put("role", rs.getString("role"));
                row.put("created_at", rs.getString("created_at"));
                list.add(row);
            }
            return list;
        }
    }

    /**
     * Changes the role of a user account, logging the action to admin_audit_log.
     */
    public void adminSetUserRole(UserAccount admin, String targetUsername, String newRole)
            throws SQLException {
        if (admin == null || !admin.getRole().isAdminOrManager()) {
            throw new IllegalArgumentException("Only admins or managers can change user roles.");
        }
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username must not be empty.");
        }
        if (newRole == null || newRole.trim().isEmpty()) {
            throw new IllegalArgumentException("New role must not be empty.");
        }
        String validRole = newRole.trim().toUpperCase(Locale.ROOT);
        if (!validRole.equals("USER") && !validRole.equals("TECHNICIAN")
                && !validRole.equals("MANAGER") && !validRole.equals("ADMIN")) {
            throw new IllegalArgumentException(
                    "Role must be one of USER, TECHNICIAN, MANAGER, ADMIN.");
        }
        String validTarget = targetUsername.trim();

        String updateSql = "UPDATE app_users SET role = ? WHERE username = ?";
        String auditSql = "INSERT INTO admin_audit_log "
                + "(admin_username, action, token_id, target_username, details) "
                + "VALUES (?, 'SET_USER_ROLE', NULL, ?, ?)";

        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, validRole);
                    stmt.setString(2, validTarget);
                    int rows = stmt.executeUpdate();
                    if (rows != 1) {
                        throw new IllegalArgumentException(
                                "User not found: " + validTarget);
                    }
                }
                try (PreparedStatement auditStmt = connection.prepareStatement(auditSql)) {
                    auditStmt.setString(1, admin.getUsername());
                    auditStmt.setString(2, validTarget);
                    auditStmt.setString(3, "Role changed to " + validRole);
                    auditStmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Returns aggregate statistics about the ticket system.
     */
    public Map<String, Object> adminGetStats() throws SQLException {
        try (Connection connection = DatabaseConnections.openRoleConnection(Role.MANAGER)) {
            Map<String, Object> stats = new HashMap<>();

            // --- By status ---
            String byStatusSql = "SELECT issue_status, COUNT(*) AS cnt FROM tickets GROUP BY issue_status";
            Map<String, Integer> byStatus = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(byStatusSql)) {
                while (rs.next()) {
                    byStatus.put(rs.getString("issue_status"), rs.getInt("cnt"));
                }
            }
            stats.put("by_status", byStatus);

            // --- By priority ---
            String byPrioritySql = "SELECT priority_level, COUNT(*) AS cnt FROM tickets GROUP BY priority_level";
            Map<String, Integer> byPriority = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(byPrioritySql)) {
                while (rs.next()) {
                    byPriority.put(rs.getString("priority_level"), rs.getInt("cnt"));
                }
            }
            stats.put("by_priority", byPriority);

            // --- By issue type ---
            String byTypeSql = "SELECT issue_type, COUNT(*) AS cnt FROM tickets GROUP BY issue_type";
            Map<String, Integer> byType = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(byTypeSql)) {
                while (rs.next()) {
                    byType.put(rs.getString("issue_type"), rs.getInt("cnt"));
                }
            }
            stats.put("by_issue_type", byType);

            // --- Total open ---
            String totalOpenSql = "SELECT COUNT(*) AS cnt FROM tickets WHERE issue_status = 'Open'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(totalOpenSql)) {
                stats.put("total_open", rs.next() ? rs.getInt("cnt") : 0);
            }

            // --- Total resolved today ---
            String resolvedTodaySql = "SELECT COUNT(*) AS cnt FROM tickets "
                    + "WHERE issue_status = 'Resolved' AND updated_at >= CURDATE()";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(resolvedTodaySql)) {
                stats.put("total_resolved_today", rs.next() ? rs.getInt("cnt") : 0);
            }

            // --- Total technicians ---
            String totalTechSql = "SELECT COUNT(*) AS cnt FROM app_users WHERE role = 'TECHNICIAN'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(totalTechSql)) {
                stats.put("total_technicians", rs.next() ? rs.getInt("cnt") : 0);
            }

            // --- Total users ---
            String totalUsersSql = "SELECT COUNT(*) AS cnt FROM app_users WHERE role = 'USER'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(totalUsersSql)) {
                stats.put("total_users", rs.next() ? rs.getInt("cnt") : 0);
            }

            return stats;
        }
    }

    // -------------------------------------------------------------------------
    // Activity logs
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Technician directory
    // -------------------------------------------------------------------------

    /** Returns the technician directory. Accessible to any authenticated user. */
    public List<Map<String, String>> getTechnicianDirectory() throws SQLException {
        String sql = "SELECT employee_id, username, department FROM technician_directory ORDER BY employee_id";
        try (Connection connection = DatabaseConnections.openDefaultConnection();
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int priorityOrdinal(String priorityLevel) {
        if (priorityLevel == null) return 3;
        switch (priorityLevel) {
            case "Critical": return 0;
            case "High":     return 1;
            case "Medium":   return 2;
            default:         return 3; // Low
        }
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

    /** Returns the numeric SDE level (1–5) or -1 if not an SDE designation. */
    private int parseSdeLevel(String normalizedDesignation) {
        int idx = normalizedDesignation.indexOf("sde");
        if (idx >= 0 && idx + 3 < normalizedDesignation.length()) {
            char c = normalizedDesignation.charAt(idx + 3);
            boolean onlyOneDigit = !Character.isDigit(c)
                    || idx + 4 == normalizedDesignation.length()
                    || !Character.isDigit(normalizedDesignation.charAt(idx + 4));
            if (Character.isDigit(c) && onlyOneDigit) {
                int level = Character.digit(c, 10);
                if (level >= 1 && level <= 5) {
                    return level;
                }
            }
        }
        return -1;
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
        map.put("priority_level", rs.getString("priority_level"));
        map.put("issue_status", rs.getString("issue_status"));
        map.put("technician_id", rs.getString("technician_id"));
        map.put("technician_department", rs.getString("technician_department"));
        map.put("estimated_minutes", rs.getObject("estimated_minutes"));
        map.put("reissued_count", rs.getInt("reissued_count"));
        map.put("closed_by", rs.getString("closed_by"));
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
                    actor.getUsername(), tokenId, actor, eventType, status, estimatedMinutes,
                    details);
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
