package incident.tracking;

public class Incident {
    private String employeeID;
    private String department;
    private String designation;

    public void incidentDetails(String employeeID, String department, String designation) {
        this.employeeID = employeeID;
        this.department = department;
        this.designation = designation;
    }

    public String getEmployeeID() {
        return employeeID;
    }

    public String getDepartment() {
        return department;
    }

    public String getDesignation() {
        return designation;
    }

    public void displayIncidentDetails() {
        System.out.println("Employee ID: " + employeeID);
        System.out.println("Department: " + department);
        System.out.println("Designation: " + designation);
    }
}
class main {
    public static void main(String[] args) {
        issue.runIssueIntake();
    }
}