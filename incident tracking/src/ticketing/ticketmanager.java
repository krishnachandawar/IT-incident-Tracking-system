package ticketing;

import issuestats.userdisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public final class ticketmanager {
    private ticketmanager() {
    }

    public static void runManagement(List<ticketinfo> tickets, Scanner scanner) {
        Objects.requireNonNull(tickets, "tickets must not be null");
        Objects.requireNonNull(scanner, "scanner must not be null");

        System.out.println("\nTicket manager review:");
        System.out.print("Enter ticket manager ID (or 0 to skip): ");
        String managerId = scanner.nextLine().trim();
        if ("0".equals(managerId)) {
            return;
        }
        if (managerId.isEmpty()) {
            throw new IllegalArgumentException("Ticket manager ID must not be empty.");
        }

        while (true) {
            List<ticketinfo> unresolvedTickets = getUnresolvedTickets(tickets);
            if (unresolvedTickets.isEmpty()) {
                System.out.println("There are no unresolved tickets to manage.");
                return;
            }

            System.out.println("\nUnresolved tickets:");
            for (int i = 0; i < unresolvedTickets.size(); i++) {
                ticketinfo ticket = unresolvedTickets.get(i);
                System.out.printf("%d. %s - %s [%s, technician: %s]%n",
                        i + 1,
                        ticket.getTokenId(),
                        ticket.getIssueType(),
                        ticket.getIssueStatus(),
                        ticket.getTechnicianId() == null
                                ? "unassigned" : ticket.getTechnicianId());
            }
            System.out.println("0. Finish manager review");
            System.out.print("Select a ticket: ");
            int ticketChoice = readChoice(scanner, unresolvedTickets.size());
            if (ticketChoice == 0) {
                return;
            }

            ticketinfo selectedTicket = unresolvedTickets.get(ticketChoice - 1);
            userdisplay.displayTicketUpdates(selectedTicket);
            System.out.println("1. Review/update status or approve resolution");
            System.out.println("2. Reassign ticket");
            System.out.println("0. Back to ticket list");
            System.out.print("Choose an action: ");
            int action = readChoice(scanner, 2);
            if (action == 1) {
                reviewTicket(selectedTicket, managerId, scanner);
                userdisplay.displayTicketUpdates(selectedTicket);
            } else if (action == 2) {
                reassignTicket(selectedTicket, managerId, scanner);
                userdisplay.displayTicketUpdates(selectedTicket);
            }
        }
    }

    private static List<ticketinfo> getUnresolvedTickets(List<ticketinfo> tickets) {
        List<ticketinfo> unresolvedTickets = new ArrayList<>();
        for (ticketinfo ticket : tickets) {
            if (!"Resolved".equalsIgnoreCase(ticket.getIssueStatus())) {
                unresolvedTickets.add(ticket);
            }
        }
        return unresolvedTickets;
    }

    private static void reviewTicket(
            ticketinfo ticket, String managerId, Scanner scanner) {
        System.out.println("Select manager decision:");
        System.out.println("1. Approve as resolved");
        System.out.println("2. Not resolved - return to In Progress");
        System.out.println("3. Set to Waiting");
        System.out.println("4. Keep pending resolution review");
        System.out.println("5. Return to Allocated");
        System.out.print("Decision: ");
        int decision = readChoice(scanner, 5);
        if (decision == 0) {
            return;
        }

        String[] statuses = {
            "Resolved", "In Progress", "Waiting", "Pending Manager Review", "Allocated"
        };
        String newStatus = statuses[decision - 1];
        int estimatedMinutes = 0;
        if (!"Resolved".equals(newStatus)) {
            System.out.print("Estimated minutes remaining: ");
            estimatedMinutes = readNonNegativeInteger(scanner);
        }
        System.out.print("Enter the update for the user: ");
        String note = readRequiredText(scanner, "Update must not be empty.");
        ticket.updateByManager(managerId, newStatus, estimatedMinutes, note);
    }

    private static void reassignTicket(
            ticketinfo ticket, String managerId, Scanner scanner) {
        System.out.print("Enter new technician ID: ");
        String technicianId = readRequiredText(scanner, "Technician ID must not be empty.");
        System.out.print("Enter new technician department: ");
        String department = readRequiredText(scanner, "Department must not be empty.");
        System.out.print("Enter the reassignment update for the user: ");
        String note = readRequiredText(scanner, "Update must not be empty.");
        ticket.managerReassignTo(managerId, technicianId, department, note);
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
