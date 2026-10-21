package issuestats;

import java.util.List;
import ticketing.ticketinfo;

public final class userdisplay {
    private userdisplay() {
    }

    public static void displayTicketsForUser(List<ticketinfo> tickets, String employeeId) {
        boolean foundTicket = false;
        System.out.println("\nTicket updates for employee " + employeeId + ":");
        for (ticketinfo ticket : tickets) {
            if (employeeId.equalsIgnoreCase(ticket.getEmployeeId())) {
                displayTicketUpdates(ticket);
                foundTicket = true;
            }
        }
        if (!foundTicket) {
            System.out.println("No tickets found for this employee.");
        }
    }

    public static void displayTicketUpdates(ticketinfo ticket) {
        System.out.println("\nTicket: " + ticket.getTokenId());
        System.out.println("Issue type: " + ticket.getIssueType());
        System.out.println("Status: " + ticket.getIssueStatus());
        System.out.println("Assigned technician: "
                + (ticket.getTechnicianId() == null
                        ? "Not assigned"
                        : ticket.getTechnicianId() + " (" + ticket.getTechnicianDepartment() + ")"));
        System.out.println("Estimated time to resolve: "
                + ticket.getEstimatedResolution().getDisplayValue());
        System.out.println("Update history:");
        for (String update : ticket.getUpdateHistory()) {
            System.out.println("- " + update);
        }
    }
}
