package ticketing;

import incident.tracking.TokenID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ticketinfo {
    private static final DateTimeFormatter UPDATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final String tokenId;
    private final String employeeId;
    private final String userDepartment;
    private final String issueType;
    private final String designation;
    private final String description;
    private final EstimatedResolution estimatedResolution = new EstimatedResolution();
    private final List<String> updateHistory = new ArrayList<>();
    private String issueStatus;
    private String technicianId;
    private String technicianDepartment;

    public ticketinfo(TokenID token) {
        Objects.requireNonNull(token, "token must not be null");
        this.tokenId = token.getTokenId();
        this.employeeId = token.getEmployeeId();
        this.userDepartment = token.getDepartment();
        this.issueType = token.getIssueType();
        this.designation = token.getDesignation();
        this.description = token.getDescription();
        this.issueStatus = "Open";
        recordUpdate("Ticket created with status Open.");
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getUserDepartment() {
        return userDepartment;
    }

    public String getDesignation() {
        return designation;
    }

    public String getDescription() {
        return description;
    }

    public String getIssueStatus() {
        return issueStatus;
    }

    public String getTechnicianId() {
        return technicianId;
    }

    public String getTechnicianDepartment() {
        return technicianDepartment;
    }

    public EstimatedResolution getEstimatedResolution() {
        return estimatedResolution;
    }

    public List<String> getUpdateHistory() {
        return Collections.unmodifiableList(new ArrayList<>(updateHistory));
    }

    public void allocateTo(String technicianId, String technicianDepartment) {
        if (!"Open".equalsIgnoreCase(issueStatus)) {
            throw new IllegalStateException("Only open tickets can be allocated.");
        }
        String assignedTechnicianId = requireText(technicianId, "technicianId");
        String assignedTechnicianDepartment = requireText(
                technicianDepartment, "technicianDepartment");
        this.technicianId = assignedTechnicianId;
        this.technicianDepartment = assignedTechnicianDepartment;
        this.issueStatus = "Allocated";
        recordUpdate("Allocated to technician " + this.technicianId
                + " (" + this.technicianDepartment + ").");
    }

    public void updateByTechnician(
            String technicianId, String newStatus, int estimatedMinutes, String updateNote) {
        requireCurrentTechnician(technicianId);
        String normalizedStatus = normalizeStatus(newStatus);
        if ("Resolved".equals(normalizedStatus) || "Allocated".equals(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Technicians may report progress or request a manager resolution review.");
        }
        String note = requireText(updateNote, "updateNote");
        estimatedResolution.setEstimatedMinutes(estimatedMinutes);
        this.issueStatus = normalizedStatus;
        recordUpdate("Status updated to " + normalizedStatus + " by technician "
                + this.technicianId + "; estimated resolution: "
                + estimatedResolution.getDisplayValue() + ". Note: "
                + note);
    }

    public void managerReassignTo(
            String managerId,
            String newTechnicianId,
            String newTechnicianDepartment,
            String managerNote) {
        if ("Resolved".equalsIgnoreCase(issueStatus)) {
            throw new IllegalStateException("Resolved tickets cannot be reassigned.");
        }
        String responsibleManager = requireText(managerId, "managerId");
        String previousTechnicianId = technicianId;
        String reassignedTechnicianId = requireText(newTechnicianId, "newTechnicianId");
        String reassignedTechnicianDepartment = requireText(
                newTechnicianDepartment, "newTechnicianDepartment");
        String note = requireText(managerNote, "managerNote");
        this.technicianId = reassignedTechnicianId;
        this.technicianDepartment = reassignedTechnicianDepartment;
        this.issueStatus = "Allocated";
        recordUpdate("Ticket manager " + responsibleManager + " reassigned from technician "
                + (previousTechnicianId == null ? "unassigned" : previousTechnicianId)
                + " to " + this.technicianId + " (" + this.technicianDepartment
                + "). Note: " + note);
    }

    public void updateByManager(
            String managerId, String newStatus, int estimatedMinutes, String managerNote) {
        if ("Resolved".equalsIgnoreCase(issueStatus)) {
            throw new IllegalStateException("Resolved tickets cannot be updated.");
        }
        String responsibleManager = requireText(managerId, "managerId");
        String normalizedStatus = normalizeStatus(newStatus);
        String note = requireText(managerNote, "managerNote");
        if ("Resolved".equals(normalizedStatus) && estimatedMinutes != 0) {
            throw new IllegalArgumentException(
                    "Estimated minutes must be zero when approving resolution.");
        }
        estimatedResolution.setEstimatedMinutes(estimatedMinutes);
        this.issueStatus = normalizedStatus;
        recordUpdate("Ticket manager " + responsibleManager + " updated status to "
                + normalizedStatus + "; estimated resolution: "
                + estimatedResolution.getDisplayValue() + ". Note: " + note);
    }

    public void displayTicketInfo() {
        System.out.println("Ticket: " + tokenId);
        System.out.println("Employee ID: " + employeeId);
        System.out.println("User department: " + userDepartment);
        System.out.println("Issue type: " + issueType);
        System.out.println("Designation: " + designation);
        System.out.println("Description: " + description);
        System.out.println("Issue status: " + issueStatus);
        System.out.println("Estimated resolution: " + estimatedResolution.getDisplayValue());
        if (technicianId != null) {
            System.out.println("Allocated technician ID: " + technicianId);
            System.out.println("Technician department: " + technicianDepartment);
        }
    }

    private void requireCurrentTechnician(String technicianId) {
        if (!Objects.equals(this.technicianId, technicianId)) {
            throw new IllegalStateException("Only the assigned technician can update this ticket.");
        }
        if ("Resolved".equalsIgnoreCase(issueStatus)) {
            throw new IllegalStateException("Resolved tickets cannot be updated.");
        }
    }

    private String normalizeStatus(String status) {
        String normalizedStatus = requireText(status, "status");
        if ("allocated".equalsIgnoreCase(normalizedStatus)) {
            return "Allocated";
        }
        if ("in progress".equalsIgnoreCase(normalizedStatus)) {
            return "In Progress";
        }
        if ("waiting".equalsIgnoreCase(normalizedStatus)) {
            return "Waiting";
        }
        if ("pending manager review".equalsIgnoreCase(normalizedStatus)) {
            return "Pending Manager Review";
        }
        if ("resolved".equalsIgnoreCase(normalizedStatus)) {
            return "Resolved";
        }
        throw new IllegalArgumentException("Unsupported ticket status: " + status);
    }

    private String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty.");
        }
        return value.trim();
    }

    private void recordUpdate(String message) {
        updateHistory.add(LocalDateTime.now().format(UPDATE_TIME_FORMAT) + " - " + message);
    }
}
