package access;

public final class UserAccount {
    private final String username;
    private final String employeeId;
    private final String department;
    private final String designation;
    private final Role role;

    UserAccount(
            String username,
            String employeeId,
            String department,
            String designation,
            Role role) {
        this.username = username;
        this.employeeId = employeeId;
        this.department = department;
        this.designation = designation;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getDepartment() {
        return department;
    }

    public String getDesignation() {
        return designation;
    }

    public Role getRole() {
        return role;
    }
}
