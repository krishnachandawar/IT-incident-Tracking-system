package access;

public enum Role {
    USER,
    TECHNICIAN,
    MANAGER;

    public static Role fromDatabaseValue(String value) {
        return Role.valueOf(value);
    }
}
