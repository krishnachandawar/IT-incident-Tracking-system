package Department;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import issuestats.userdisplay;
import ticketing.ticketinfo;
import ticketing.ticketpriority;

public final class technicians {
    private final String id;
    private final String department;

    public technicians(String id, String department) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Technician ID must not be empty.");
        }
        if (department == null || department.trim().isEmpty()) {
            throw new IllegalArgumentException("Technician department must not be empty.");
        }
        this.id = id.trim();
        this.department = department.trim();
    }

    public String getId() {
        return id;
    }

    public String getDepartment() {
        return department;
    }

    public static void runAllocation(List<ticketinfo> tickets, Scanner scanner) {
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
        List<technicians> registeredTechnicians = new ArrayList<>();
        ticketpriority priorityChecker = new ticketpriority();

        System.out.println("\nTechnician self-service allocation:");
        while (true) {
            System.out.print("Enter technician ID (or 0 to finish): ");
            String id = scanner.nextLine().trim();
            if ("0".equals(id)) {
                break;
            }
            if (id.isEmpty()) {
                System.out.println("Technician ID must not be empty.");
                continue;
            }

            System.out.print("Enter technician department: ");
            String department = scanner.nextLine().trim();
            if (department.isEmpty()) {
                System.out.println("Technician department must not be empty.");
                continue;
            }

            technicians technician = new technicians(id, department);
            registeredTechnicians.add(technician);

                System.out.println("\n1. Claim an open ticket");
                System.out.println("2. Update one of your tickets or request resolution review");
                System.out.println("0. Finish");
                System.out.print("Choose an action: ");
                int action = readChoice(scanner, 2);
                if (action == 0) {
                    break;
                }
                if (action == 1) {
                    claimTicket(tickets, issueTypes, technician, scanner, priorityChecker);
                } else {
                    manageAssignedTickets(tickets, technician, scanner);
                }
            }

            System.out.println("\nRegistered technicians:");
            for (technicians technician : registeredTechnicians) {
                System.out.println("Technician ID: " + technician.getId()
                        + ", Department: " + technician.getDepartment());
            }
    }

    private static void claimTicket(
                List<ticketinfo> tickets,
                String[] issueTypes,
                technicians technician,
                Scanner scanner,
                ticketpriority priorityChecker) {
            System.out.println("Select an issue type to view available tickets (0 to cancel):");
            for (int i = 0; i < issueTypes.length; i++) {
                System.out.printf("%d. %s%n", i + 1, issueTypes[i]);
            }
            System.out.print("Issue type: ");
            int issueTypeChoice = readChoice(scanner, issueTypes.length);
            if (issueTypeChoice == 0) {
                return;
            }

            String selectedIssueType = issueTypes[issueTypeChoice - 1];
            List<ticketinfo> availableTickets = new ArrayList<>();
            for (ticketinfo ticket : tickets) {
                if ("Open".equalsIgnoreCase(ticket.getIssueStatus())
                        && selectedIssueType.equalsIgnoreCase(ticket.getIssueType())) {
                    availableTickets.add(ticket);
                }
            }
            availableTickets = priorityChecker.prioritize(availableTickets);

            if (availableTickets.isEmpty()) {
                System.out.println("No open tickets are available for " + selectedIssueType + ".");
                return;
            }

            System.out.println("Available tickets:");
            for (int i = 0; i < availableTickets.size(); i++) {
                ticketinfo ticket = availableTickets.get(i);
                System.out.printf("%d. %s - %s (priority score: %d)%n",
                        i + 1,
                        ticket.getTokenId(),
                        ticket.getDescription(),
                        priorityChecker.getPriorityScore(ticket));
            }
            System.out.print("Select a ticket to allocate (0 to cancel): ");
            int ticketChoice = readChoice(scanner, availableTickets.size());
            if (ticketChoice == 0) {
                return;
            }

            ticketinfo selectedTicket = availableTickets.get(ticketChoice - 1);
            selectedTicket.allocateTo(technician.getId(), technician.getDepartment());
            updateTicket(selectedTicket, technician, scanner);
            System.out.println("Ticket allocated to technician " + technician.getId() + ".");
            userdisplay.displayTicketUpdates(selectedTicket);
    }

    private static void manageAssignedTickets(
                List<ticketinfo> tickets, technicians technician, Scanner scanner) {
            List<ticketinfo> assignedTickets = new ArrayList<>();
            for (ticketinfo ticket : tickets) {
                if (technician.getId().equals(ticket.getTechnicianId())
                        && !"Resolved".equalsIgnoreCase(ticket.getIssueStatus())) {
                    assignedTickets.add(ticket);
                }
            }
            if (assignedTickets.isEmpty()) {
                System.out.println("You have no unresolved assigned tickets.");
                return;
            }

            System.out.println("Your assigned tickets:");
            for (int i = 0; i < assignedTickets.size(); i++) {
                ticketinfo ticket = assignedTickets.get(i);
                System.out.printf("%d. %s - %s [%s]%n",
                        i + 1, ticket.getTokenId(), ticket.getIssueType(), ticket.getIssueStatus());
            }
            System.out.print("Select a ticket to manage (0 to cancel): ");
            int ticketChoice = readChoice(scanner, assignedTickets.size());
            if (ticketChoice == 0) {
                return;
            }

            ticketinfo selectedTicket = assignedTickets.get(ticketChoice - 1);
            System.out.println("1. Update status and estimated resolution time");
            System.out.println("0. Cancel");
            System.out.print("Choose an action: ");
            int action = readChoice(scanner, 1);
            if (action == 1) {
                updateTicket(selectedTicket, technician, scanner);
            } else {
                return;
            }
            userdisplay.displayTicketUpdates(selectedTicket);
    }

    private static void updateTicket(
                ticketinfo ticket, technicians technician, Scanner scanner) {
            System.out.println("Select the updated status:");
            System.out.println("1. In Progress");
            System.out.println("2. Waiting");
            System.out.println("3. Request manager resolution review");
            System.out.print("Status: ");
            int statusChoice = readChoice(scanner, 3);
            if (statusChoice == 0) {
                return;
            }

            System.out.print("Estimated minutes remaining: ");
            int estimatedMinutes = readNonNegativeInteger(scanner);
            System.out.print("Enter an update for the user: ");
            String updateNote = scanner.nextLine().trim();
            while (updateNote.isEmpty()) {
                System.out.print("Update must not be empty. Enter an update for the user: ");
                updateNote = scanner.nextLine().trim();
            }

            String[] statuses = {"In Progress", "Waiting", "Pending Manager Review"};
            ticket.updateByTechnician(
                    technician.getId(), statuses[statusChoice - 1], estimatedMinutes, updateNote);
    }

    private static int readNonNegativeInteger(Scanner scanner) {
            while (true) {
                String value = scanner.nextLine().trim();
                try {
                    int minutes = Integer.parseInt(value);
                    if (minutes >= 0) {
                        return minutes;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a non-negative number of minutes.");
                    continue;
                }
                System.out.println("Please enter a non-negative number of minutes.");
            }
    }

    private static int readChoice(Scanner scanner, int maximum) {
        while (true) {
            String choiceText = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(choiceText);
                if (choice >= 0 && choice <= maximum) {
                    return choice;
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number from 0 to " + maximum + ".");
                continue;
            }
            System.out.println("Please enter a number from 0 to " + maximum + ".");
        }
    }
}
