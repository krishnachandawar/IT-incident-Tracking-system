package web;

import access.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private static final String COOKIE_NAME = "INCIDENT_SESSION";
    private static final Map<String, UserAccount> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static String createSession(UserAccount account) {
        String sessionId = UUID.randomUUID().toString();
        SESSIONS.put(sessionId, account);
        return sessionId;
    }

    public static UserAccount getAccount(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        return SESSIONS.get(sessionId.trim());
    }

    public static void invalidateSession(String sessionId) {
        if (sessionId != null) {
            SESSIONS.remove(sessionId.trim());
        }
    }

    public static UserAccount getAccountFromExchange(HttpExchange exchange) {
        String sessionId = getSessionIdFromExchange(exchange);
        return getAccount(sessionId);
    }

    public static String getSessionIdFromExchange(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && COOKIE_NAME.equalsIgnoreCase(parts[0].trim())) {
                return parts[1].trim();
            }
        }
        return null;
    }

    public static String buildSetCookieHeader(String sessionId) {
        return COOKIE_NAME + "=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400";
    }

    public static String buildClearCookieHeader() {
        return COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
    }
}
