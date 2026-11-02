package incident.tracking;

public class IncidentTracking {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if ("--console".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                    access.IncidentPortal.main(args);
                    return;
                }
            }
        }
        web.WebServer.main(args);
    }
}
