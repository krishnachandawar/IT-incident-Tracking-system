package access;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public final class IncidentPortal {
    private static final String[] ISSUE_TYPES = {
        "Operating system", "File system", "Application", "Software", "Account access",
        "Security", "Email connectivity", "Network connectivity", "Company codebase",
        "Database connection", "Hardware", "Printer", "VPN access", "Performance",
        "Backup and recovery", "Permissions", "Other"
    };
    private final AuthenticationService authenticationService = new AuthenticationService();

    private IncidentPortal() {
    }

    public static void main(String[] args) {
        new IncidentPortal().run();
    }

    private void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nIncident tracking portal");
                System.out.println("1. Register as a user");
                System.out.println("2. Log in");
                System.out.println("0. Exit");
                System.out.print("Choose an option: ");
                int choice = readChoice(scanner, 2);
                if (choice == 0) {
                    return;
                }
                try {
                    if (choice == 1) {
                        register(scanner);
                    } else {
                        login(scanner);
                    }
                } catch (SQLException e) {
                    System.out.println("Database operation failed: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                } catch (IllegalStateException e) {
                    System.out.println("Application configuration error: " + e.getMessage());
                }
            }
        }
    }

    private void register(Scanner scanner) throws SQLException {
        System.out.println("\nCreate a user account (technician and manager accounts are assigned by an administrator).");
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password (at least 12 characters): ");
        String password = scanner.nextLine();
        System.out.print("Employee ID: ");
        String employeeId = scanner.nextLine().trim();
        System.out.print("Department: ");
        String department = scanner.nextLine().trim();
        System.out.print("Designation: ");
        String designation = scanner.nextLine().trim();
        authenticationService.registerUser(
                username, password, employeeId, department, designation);
        System.out.println("Account created. Log in to register and track issues.");
    }

    private void login(Scanner scanner) throws SQLException {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        UserAccount account = authenticationService.authenticate(username, password);
        if (account == null) {
            System.out.println("Username or password is incorrect.");
            return;
        }

        try (Connection connection = DatabaseConnections.openRoleConnection(account.getRole())) {
            System.out.println("Logged in as " + account.getRole() + ".");
            switch (account.getRole()) {
                case USER:
                    userMenu(connection, account, scanner);
                    break;
                case TECHNICIAN:
                    technicianMenu(connection, account, scanner);
                    break;
                case MANAGER:
                    managerMenu(connection, account, scanner);
                    break;
                default:
                    throw new IllegalStateException("Unsupported account role.");
            }
        }
    }

    private void userMenu(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        while (true) {
            System.out.println("\n1. Register an issue");
            System.out.println("2. View my tickets and updates");
            System.out.println("3. View my user activity history");
            System.out.println("0. Log out");
            System.out.print("Choose an option: ");
            int choice = readChoice(scanner, 3);
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                submitIssue(connection, account, scanner);
            } else if (choice == 2) {
                listMyTickets(connection, account.getUsername());
            } else {
                ActivityHistory.showUserHistory(connection, account.getUsername());
            }
        }
    }

    private void submitIssue(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        System.out.println("Select issue type:");
        for (int i = 0; i < ISSUE_TYPES.length; i++) {
            System.out.printf("%d. %s%n", i + 1, ISSUE_TYPES[i]);
        }
        System.out.print("Issue type: ");
        int typeChoice = readChoice(scanner, ISSUE_TYPES.length);
        if (typeChoice == 0) {
            return;
        }
        System.out.print("Description: ");
        String description = readRequiredText(scanner, "Description must not be empty.");
        String tokenId = "TKN-" + UUID.randomUUID().toString().replace("-", "");
        String insertTicket = "INSERT INTO tickets "
                + "(token_id, requester_username, employee_id, user_department, designation, "
                + "issue_type, description, issue_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Open')";
        String insertUpdate = "INSERT INTO ticket_updates "
                + "(token_id, actor_username, actor_role, status, estimated_minutes, message) "
                + "VALUES (?, ?, 'USER', 'Open', NULL, 'Issue registered')";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ticketStatement = connection.prepareStatement(insertTicket);
                PreparedStatement updateStatement = connection.prepareStatement(insertUpdate)) {
            ticketStatement.setString(1, tokenId);
            ticketStatement.setString(2, account.getUsername());
            ticketStatement.setString(3, account.getEmployeeId());
            ticketStatement.setString(4, account.getDepartment());
            ticketStatement.setString(5, account.getDesignation());
            ticketStatement.setString(6, ISSUE_TYPES[typeChoice - 1]);
            ticketStatement.setString(7, description);
            ticketStatement.executeUpdate();
            updateStatement.setString(1, tokenId);
            updateStatement.setString(2, account.getUsername());
            updateStatement.executeUpdate();
            ActivityHistory.record(
                    connection, tokenId, account, "ISSUE_REGISTERED", "Open", null,
                    "Issue registered: " + ISSUE_TYPES[typeChoice - 1] + ".");
            connection.commit();
            System.out.println("Issue registered. Your token is " + tokenId + ".");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void technicianMenu(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        while (true) {
            System.out.println("\n1. View open issues");
            System.out.println("2. Claim an open issue");
            System.out.println("3. Update an assigned issue or request resolution review");
            System.out.println("4. View my technician activity history");
            System.out.println("0. Log out");
            System.out.print("Choose an option: ");
            int choice = readChoice(scanner, 4);
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                listOpenIssues(connection);
            } else if (choice == 2) {
                claimIssue(connection, account, scanner);
            } else if (choice == 3) {
                technicianUpdate(connection, account, scanner);
            } else {
                ActivityHistory.showTechnicianHistory(connection, account.getUsername());
            }
        }
    }

    private void claimIssue(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        System.out.println("Select an issue type:");
        for (int i = 0; i < ISSUE_TYPES.length; i++) {
            System.out.printf("%d. %s%n", i + 1, ISSUE_TYPES[i]);
        }
        System.out.print("Issue type: ");
        int issueTypeChoice = readChoice(scanner, ISSUE_TYPES.length);
        if (issueTypeChoice == 0) {
            return;
        }
        String issueType = ISSUE_TYPES[issueTypeChoice - 1];
        String sql = "SELECT token_id, issue_type, description, issue_status, "
                + "technician_id, estimated_minutes FROM tickets "
                + "WHERE issue_status = 'Open' AND issue_type = ? ORDER BY created_at";
        List<String> availableTokens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, issueType);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    printTicket(results);
                    availableTokens.add(results.getString("token_id"));
                }
            }
        }
        if (availableTokens.isEmpty()) {
            System.out.println("No open tickets are available for " + issueType + ".");
            return;
        }
        System.out.print("Enter token to claim (0 to cancel): ");
        String tokenId = scanner.nextLine().trim();
        if ("0".equals(tokenId)) {
            return;
        }
        if (!availableTokens.contains(tokenId)) {
            System.out.println("That token is not in the selected issue type's open queue.");
            return;
        }

        String claimSql = "UPDATE tickets SET technician_id = ?, technician_department = ?, "
                + "issue_status = 'Allocated' WHERE token_id = ? AND issue_status = 'Open'";
        runTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(claimSql)) {
                statement.setString(1, account.getEmployeeId());
                statement.setString(2, account.getDepartment());
                statement.setString(3, tokenId);
                requireOneUpdatedRow(statement.executeUpdate(), tokenId);
            }
            recordUpdate(connection, tokenId, account, "Allocated", null,
                    "Claimed by technician " + account.getEmployeeId()
                            + " (" + account.getDepartment() + ").");
            ActivityHistory.record(connection, tokenId, account, "ISSUE_CLAIMED",
                    "Allocated", null,
                    "Claimed by technician " + account.getEmployeeId()
                            + " (" + account.getDepartment() + ").");
        });
        System.out.println("Issue claimed and assigned to you.");
    }

    private void listOpenIssues(Connection connection) throws SQLException {
        String sql = "SELECT token_id, issue_type, description, issue_status, "
                + "technician_id, estimated_minutes FROM tickets "
                + "WHERE issue_status <> 'Resolved' ORDER BY created_at";
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            printTicketRows(results);
        }
    }

    private void technicianUpdate(
            Connection connection, UserAccount account, Scanner scanner) throws SQLException {
        listAssignedIssues(connection, account);
        System.out.print("Enter token to update (0 to cancel): ");
        String tokenId = scanner.nextLine().trim();
        if ("0".equals(tokenId)) {
            return;
        }
        if (!isAssignedTo(connection, tokenId, account.getEmployeeId())) {
            System.out.println("You can only update issues assigned to you.");
            return;
        }
        System.out.println("1. In Progress");
        System.out.println("2. Waiting");
        System.out.println("3. Request manager resolution review");
        System.out.print("Status: ");
        int statusChoice = readChoice(scanner, 3);
        if (statusChoice == 0) {
            return;
        }
        String[] statuses = {"In Progress", "Waiting", "Pending Manager Review"};
        int estimate = readNonNegativeInteger(scanner, "Estimated minutes remaining: ");
        System.out.print("Update for the user: ");
        String note = readRequiredText(scanner, "Update must not be empty.");
        updateTicket(connection, tokenId, account,
                statuses[statusChoice - 1], estimate, note, true);
    }

    private void listAssignedIssues(Connection connection, UserAccount account)
            throws SQLException {
        String sql = "SELECT token_id, issue_type, description, issue_status, "
                + "technician_id, estimated_minutes FROM tickets "
                + "WHERE technician_id = ? AND issue_status <> 'Resolved' ORDER BY created_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getEmployeeId());
            try (ResultSet results = statement.executeQuery()) {
                printTicketRows(results);
            }
        }
    }

    private boolean isAssignedTo(Connection connection, String tokenId, String technicianId)
            throws SQLException {
        String sql = "SELECT 1 FROM tickets WHERE token_id = ? AND technician_id = ? "
                + "AND issue_status <> 'Resolved'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenId);
            statement.setString(2, technicianId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void managerMenu(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        while (true) {
            System.out.println("\n1. View unresolved issues");
            System.out.println("2. Update status or approve resolution");
            System.out.println("3. Reassign an issue");
            System.out.println("4. View manager audit history");
            System.out.println("0. Log out");
            System.out.print("Choose an option: ");
            int choice = readChoice(scanner, 4);
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                listUnresolvedIssues(connection);
            } else if (choice == 2) {
                managerUpdate(connection, account, scanner);
            } else if (choice == 3) {
                managerReassign(connection, account, scanner);
            } else {
                ActivityHistory.showManagerHistory(connection);
            }
        }
    }

    private void listUnresolvedIssues(Connection connection) throws SQLException {
        String sql = "SELECT token_id, issue_type, description, issue_status, "
                + "technician_id, estimated_minutes FROM tickets "
                + "WHERE issue_status <> 'Resolved' ORDER BY created_at";
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            printTicketRows(results);
        }
    }

    private void managerUpdate(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        listUnresolvedIssues(connection);
        System.out.print("Enter token to review (0 to cancel): ");
        String tokenId = scanner.nextLine().trim();
        if ("0".equals(tokenId)) {
            return;
        }
        System.out.println("1. Approve as Resolved");
        System.out.println("2. Not resolved - In Progress");
        System.out.println("3. Waiting");
        System.out.println("4. Pending Manager Review");
        System.out.println("5. Allocated");
        System.out.print("Decision: ");
        int decision = readChoice(scanner, 5);
        if (decision == 0) {
            return;
        }
        String[] statuses = {
            "Resolved", "In Progress", "Waiting", "Pending Manager Review", "Allocated"
        };
        String status = statuses[decision - 1];
        int estimate = "Resolved".equals(status)
                ? 0 : readNonNegativeInteger(scanner, "Estimated minutes remaining: ");
        System.out.print("Update for the user: ");
        String note = readRequiredText(scanner, "Update must not be empty.");
        updateTicket(connection, tokenId, account, status, estimate, note, false);
    }

    private void managerReassign(Connection connection, UserAccount account, Scanner scanner)
            throws SQLException {
        listUnresolvedIssues(connection);
        System.out.print("Enter token to reassign (0 to cancel): ");
        String tokenId = scanner.nextLine().trim();
        if ("0".equals(tokenId)) {
            return;
        }
        System.out.print("New technician ID: ");
        String technicianId = readRequiredText(scanner, "Technician ID must not be empty.");
        System.out.print("New technician department: ");
        String department = readRequiredText(scanner, "Department must not be empty.");
        if (!isRegisteredTechnician(connection, technicianId)) {
            System.out.println("That employee ID is not a registered technician.");
            return;
        }
        System.out.print("Update for the user: ");
        String note = readRequiredText(scanner, "Update must not be empty.");
        String sql = "UPDATE tickets SET technician_id = ?, technician_department = ?, "
                + "issue_status = 'Allocated' WHERE token_id = ? AND issue_status <> 'Resolved'";
        runTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, technicianId);
                statement.setString(2, department);
                statement.setString(3, tokenId);
                requireOneUpdatedRow(statement.executeUpdate(), tokenId);
            }
            recordUpdate(connection, tokenId, account, "Allocated", null,
                    "Reassigned to technician " + technicianId + " (" + department + "). " + note);
            ActivityHistory.record(connection, tokenId, account, "ISSUE_REASSIGNED",
                    "Allocated", null,
                    "Reassigned to technician " + technicianId + " (" + department + "). " + note);
        });
        System.out.println("Issue reassigned.");
    }

    private boolean isRegisteredTechnician(Connection connection, String employeeId)
            throws SQLException {
        String sql = "SELECT 1 FROM technician_directory WHERE employee_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, employeeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void updateTicket(
            Connection connection,
            String tokenId,
            UserAccount account,
            String status,
            int estimatedMinutes,
            String note,
            boolean requireAssignedTechnician) throws SQLException {
        String sql = "UPDATE tickets SET issue_status = ?, estimated_minutes = ?"
                + (requireAssignedTechnician ? "" : ", updated_at = CURRENT_TIMESTAMP")
                + " WHERE token_id = ? AND issue_status <> 'Resolved'"
                + ("Resolved".equals(status)
                        ? " AND issue_status = 'Pending Manager Review'" : "")
                + (requireAssignedTechnician ? " AND technician_id = ?" : "");
        runTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status);
                if ("Resolved".equals(status)) {
                    statement.setNull(2, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(2, estimatedMinutes);
                }
                statement.setString(3, tokenId);
                if (requireAssignedTechnician) {
                    statement.setString(4, account.getEmployeeId());
                }
                requireOneUpdatedRow(statement.executeUpdate(), tokenId);
            }
            recordUpdate(connection, tokenId, account, status, estimatedMinutes, note);
            ActivityHistory.record(connection, tokenId, account, "STATUS_UPDATED",
                    status, "Resolved".equals(status) ? null : estimatedMinutes, note);
        });
        System.out.println("Ticket updated.");
    }

    private void runTransaction(Connection connection, SqlOperation operation)
            throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            operation.run();
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void recordUpdate(
            Connection connection,
            String tokenId,
            UserAccount account,
            String status,
            Integer estimatedMinutes,
            String message) throws SQLException {
        String sql = "INSERT INTO ticket_updates "
                + "(token_id, actor_username, actor_role, status, estimated_minutes, message) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenId);
            statement.setString(2, account.getUsername());
            statement.setString(3, account.getRole().name());
            statement.setString(4, status);
            if (estimatedMinutes == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, estimatedMinutes);
            }
            statement.setString(6, message);
            statement.executeUpdate();
        }
    }

    private void listMyTickets(Connection connection, String username) throws SQLException {
        String sql = "SELECT token_id, issue_type, description, issue_status, "
                + "technician_id, estimated_minutes FROM tickets "
                + "WHERE requester_username = ? ORDER BY created_at";
        List<String> tokens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    printTicket(results);
                    tokens.add(results.getString("token_id"));
                }
            }
        }
        if (tokens.isEmpty()) {
            System.out.println("No tickets found.");
        }
        for (String token : tokens) {
            printTicketHistory(connection, token);
        }
    }

    private void printTicketHistory(Connection connection, String tokenId) throws SQLException {
        String sql = "SELECT created_at, actor_username, actor_role, status, "
                + "estimated_minutes, message FROM ticket_updates "
                + "WHERE token_id = ? ORDER BY update_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Integer minutes = (Integer) results.getObject("estimated_minutes");
                    String estimate = minutes == null
                            ? "Not provided" : new EstimatedResolutionDisplay(minutes).toString();
                    System.out.printf("  %s | %s (%s) | %s | ETA: %s | %s%n",
                            results.getTimestamp("created_at"),
                            results.getString("actor_username"),
                            results.getString("actor_role"),
                            results.getString("status"),
                            estimate,
                            results.getString("message"));
                }
            }
        }
    }

    private void printTicketRows(ResultSet results) throws SQLException {
        boolean found = false;
        while (results.next()) {
            printTicket(results);
            found = true;
        }
        if (!found) {
            System.out.println("No matching issues found.");
        }
    }

    private void printTicket(ResultSet result) throws SQLException {
        System.out.printf("%s | %s | %s | %s | technician: %s | ETA: %s%n",
                result.getString("token_id"),
                result.getString("issue_type"),
                result.getString("description"),
                result.getString("issue_status"),
                result.getString("technician_id") == null
                        ? "unassigned" : result.getString("technician_id"),
                result.getObject("estimated_minutes") == null
                        ? "Not provided"
                        : new EstimatedResolutionDisplay(
                                result.getInt("estimated_minutes")).toString());
    }

    private static int readChoice(Scanner scanner, int maximum) {
        while (true) {
            String value = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(value);
                if (choice >= 0 && choice <= maximum) {
                    return choice;
                }
            } catch (NumberFormatException e) {
                System.out.println("Enter a number from 0 to " + maximum + ".");
                continue;
            }
            System.out.println("Enter a number from 0 to " + maximum + ".");
        }
    }

    private static int readNonNegativeInteger(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                int minutes = Integer.parseInt(value);
                if (minutes >= 0) {
                    return minutes;
                }
            } catch (NumberFormatException e) {
                System.out.println("Enter a non-negative number.");
                continue;
            }
            System.out.println("Enter a non-negative number.");
        }
    }

    private static String readRequiredText(Scanner scanner, String errorMessage) {
        while (true) {
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println(errorMessage);
        }
    }

    private static void requireOneUpdatedRow(int rows, String tokenId) {
        if (rows != 1) {
            throw new IllegalArgumentException(
                    "Ticket not found or it is already resolved: " + tokenId);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private static final class EstimatedResolutionDisplay {
        private final int minutes;

        private EstimatedResolutionDisplay(int minutes) {
            this.minutes = minutes;
        }

        @Override
        public String toString() {
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
}
