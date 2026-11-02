package access;

public enum Role {
    USER,
    TECHNICIAN,
    MANAGER,
    ADMIN;

    public static Role fromDatabaseValue(String value) {
        return Role.valueOf(value);
    }

    /** Returns true for roles that can manage other users and close/reopen tickets. */
    public boolean isAdminOrManager() {
        return this == ADMIN || this == MANAGER;
    }
}
