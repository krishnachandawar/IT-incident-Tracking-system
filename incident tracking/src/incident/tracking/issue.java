package incident.tracking;

import Department.technicians;
import issuestats.userdisplay;
import ticketing.ticketinfo;
import ticketing.ticketmanager;
import ticketing.ticketpriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class issue {
    private final String issueId;
    private final String issueType;
    private String description;

    public issue(String issueId, String issueType) {
        this.issueId = issueId;
        this.issueType = issueType;
    }

    public String getIssueId() {
        return issueId;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static void runIssueIntake() {
        String[] issueTypes = {
            "Operating system",
            "File system",
            "Application",
            "Software",
            "Account access",
            "Security",
            "Email connectivity",
            "Network connectivity",
            "Company codebase",
            "Database connection",
            "Hardware",
            "Printer",
            "VPN access",
            "Performance",
            "Backup and recovery",
            "Permissions",
            "Other"
        };
        List<issue> issues = new ArrayList<>();
        List<ticketinfo> tickets = new ArrayList<>();

        try (Scanner scanner = new Scanner(System.in)) {
            Incident incident = new Incident();
            System.out.print("Enter employee ID: ");
            String employeeID = scanner.nextLine();
            System.out.print("Enter department: ");
            String department = scanner.nextLine();
            System.out.print("Enter designation: ");
            String designation = scanner.nextLine();
            incident.incidentDetails(employeeID, department, designation);

            while (true) {
                System.out.println("\nSelect an issue type (0 to finish):");
                for (int i = 0; i < issueTypes.length; i++) {
                    System.out.printf("%d. %s%n", i + 1, issueTypes[i]);
                }
                System.out.print("Choice: ");

                String choiceText = scanner.nextLine().trim();
                int choice;
                try {
                    choice = Integer.parseInt(choiceText);
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a number from 0 to " + issueTypes.length + ".");
                    continue;
                }

                if (choice == 0) {
                    break;
                }
                if (choice < 1 || choice > issueTypes.length) {
                    System.out.println("Please select a listed issue type.");
                    continue;
                }

                String issueId = String.format("ISSUE%03d", issues.size() + 1);
                issue newIssue = new issue(issueId, issueTypes[choice - 1]);
                System.out.print("Enter a description: ");
                newIssue.setDescription(scanner.nextLine());
                issues.add(newIssue);
                TokenID token = new TokenID(incident, newIssue, issues.size());
                System.out.println("Issue recorded: " + issueId);
                incident.displayIncidentDetails();
                ticketinfo ticket = new ticketinfo(token);
                tickets.add(ticket);
                ticket.displayTicketInfo();
            }
            technicians.runAllocation(tickets, scanner);
            ticketmanager.runManagement(tickets, scanner);
            userdisplay.displayTicketsForUser(tickets, employeeID);
        }

        System.out.println("\nTicket priority queue:");
        ticketpriority priorityChecker = new ticketpriority();
        for (ticketinfo ticket : priorityChecker.prioritize(tickets)) {
            System.out.println("Priority score: " + priorityChecker.getPriorityScore(ticket));
            ticket.displayTicketInfo();
        }

        System.out.println("\nRecorded issues:");
        for (issue recordedIssue : issues) {
            System.out.println("Issue ID: " + recordedIssue.getIssueId());
            System.out.println("Type: " + recordedIssue.getIssueType());
            System.out.println("Description: " + recordedIssue.getDescription());
        }
    }
}
