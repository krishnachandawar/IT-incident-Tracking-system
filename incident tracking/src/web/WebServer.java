package web;

import access.AuthenticationService;
import access.DatabaseConnections;
import access.Role;
import access.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WebServer {
    private static final int DEFAULT_PORT = 8080;
    private final AuthenticationService authService = new AuthenticationService();
    private final IncidentService incidentService = new IncidentService();
    private HttpServer server;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }

        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        new WebServer().start(port);
    }

    public void start(int port) {
        try {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            } catch (Exception e1) {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            }

            // Auth endpoints
            server.createContext("/", this::handleRoot);
            server.createContext("/api/session", this::handleSession);
            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/register", this::handleRegister);
            server.createContext("/api/logout", this::handleLogout);

            // Ticket endpoints (user/technician)
            server.createContext("/api/tickets", this::handleTickets);
            server.createContext("/api/tickets/create", this::handleCreateTicket);
            server.createContext("/api/tickets/claim", this::handleClaimTicket);
            server.createContext("/api/tickets/update", this::handleUpdateTicket);
            server.createContext("/api/tickets/reassign", this::handleReassignTicket);
            server.createContext("/api/ticket-updates", this::handleTicketUpdates);
            server.createContext("/api/activity", this::handleActivity);
            server.createContext("/api/technicians", this::handleTechnicians);
            server.createContext("/api/status", this::handleStatus);
            server.createContext("/api/priority-score", this::handlePriorityScore);

            // Admin endpoints
            server.createContext("/api/admin/stats", this::handleAdminStats);
            server.createContext("/api/admin/users", this::handleAdminUsers);
            server.createContext("/api/admin/registrations", this::handleAdminRegistrations);
            server.createContext("/api/admin/registrations/decision", this::handleAdminRegistrationDecision);
            server.createContext("/api/admin/users/role", this::handleAdminSetRole);
            server.createContext("/api/admin/tickets/close", this::handleAdminCloseTicket);
            server.createContext("/api/admin/tickets/reopen", this::handleAdminReopenTicket);
            server.createContext("/api/admin/tickets/all", this::handleAdminAllTickets);

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            String appUrl = "http://localhost:" + port + "/";
            System.out.println("==================================================================");
            System.out.println("   IT Incident Tracking System - Web Server Started");
            System.out.println("   Access via Browser: " + appUrl);
            System.out.println("   Database JDBC URL: " + DatabaseConnections.getEffectiveUrl());
            System.out.println("   Database Status:    " + (DatabaseConnections.testConnection() ? "CONNECTED" : "NOT CONNECTED (Check MySQL / Workbench setup)"));
            System.out.println("==================================================================");

            openBrowser(appUrl);
        } catch (IOException e) {
            System.err.println("Failed to start web server on port " + port + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Root
    // -------------------------------------------------------------------------
    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String html = WebUiRenderer.renderHtml();
        sendResponse(exchange, 200, html, "text/html; charset=UTF-8");
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------
    private void handleSession(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        Map<String, Object> response = new HashMap<>();
        if (account != null) {
            response.put("authenticated", true);
            response.put("user", mapUser(account));
        } else {
            response.put("authenticated", false);
        }
        sendJsonResponse(exchange, 200, response);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);
        String username = params.get("username");
        String password = params.get("password");

        try {
            UserAccount account = authService.authenticate(username, password);
            if (account == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", "Username or password is incorrect.");
                sendJsonResponse(exchange, 401, err);
                return;
            }

            String sessionId = SessionManager.createSession(account);
            exchange.getResponseHeaders().add("Set-Cookie", SessionManager.buildSetCookieHeader(sessionId));

            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("user", mapUser(account));
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalStateException e) {
            sendError(exchange, 403, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            authService.registerUser(
                    params.get("username"),
                    params.get("password"),
                    params.get("employee_id"),
                    params.get("department"),
                    params.get("designation"),
                    params.getOrDefault("requested_role", "USER"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            String requestedRole = params.getOrDefault("requested_role", "USER");
            ok.put("approval_required", !"USER".equalsIgnoreCase(requestedRole));
            ok.put("requested_role", requestedRole.toUpperCase());
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = SessionManager.getSessionIdFromExchange(exchange);
        SessionManager.invalidateSession(sessionId);
        exchange.getResponseHeaders().add("Set-Cookie", SessionManager.buildClearCookieHeader());
        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        sendJsonResponse(exchange, 200, ok);
    }

    // -------------------------------------------------------------------------
    // Tickets (User / Technician)
    // -------------------------------------------------------------------------
    private void handleTickets(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        try {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            List<Map<String, Object>> list;
            if (account.getRole() == Role.USER) {
                list = incidentService.getTicketsForUser(account.getUsername());
            } else if (account.getRole() == Role.TECHNICIAN) {
                String view = query.containsKey("view")
                        ? query.get("view") : query.getOrDefault("filter", "assigned");
                if ("open".equalsIgnoreCase(view)) {
                    list = incidentService.getOpenTicketsForTechnician();
                } else {
                    list = incidentService.getAssignedTickets(account.getEmployeeId());
                }
            } else {
                String filter = query.getOrDefault("filter", "all");
                list = "unresolved".equalsIgnoreCase(filter)
                        ? incidentService.getUnresolvedTickets()
                        : incidentService.getAllTickets();
            }
            sendJsonResponse(exchange, 200, list);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleCreateTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            String tokenId = incidentService.createTicket(
                    account,
                    params.get("issue_type"),
                    params.get("description"),
                    params.get("priority_override"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("token_id", tokenId);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleClaimTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || account.getRole() != Role.TECHNICIAN) {
            sendError(exchange, 403, "Only technicians can claim tickets.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            incidentService.claimTicket(account, params.get("token_id"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleUpdateTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            String tokenId = params.get("token_id");
            String status = params.get("status");
            int estimatedMinutes = 0;
            try {
                estimatedMinutes = Integer.parseInt(params.getOrDefault("estimated_minutes", "0"));
            } catch (NumberFormatException ignored) {
            }
            String note = params.get("note");

            if (account.getRole() == Role.TECHNICIAN) {
                incidentService.updateTicketAsTechnician(account, tokenId, status, estimatedMinutes, note);
            } else if (account.getRole().isAdminOrManager()) {
                incidentService.updateTicketAsManager(account, tokenId, status, estimatedMinutes, note);
            } else {
                sendError(exchange, 403, "Not authorized to update tickets.");
                return;
            }

            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleReassignTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Only managers/admins can reassign tickets.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            incidentService.reassignTicketAsManager(
                    account,
                    params.get("token_id"),
                    params.get("technician_id"),
                    params.get("department"),
                    params.get("note"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleTicketUpdates(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String tokenId = query.get("token_id");
        if (tokenId == null || tokenId.trim().isEmpty()) {
            sendError(exchange, 400, "token_id parameter is required.");
            return;
        }
        try {
            List<Map<String, Object>> updates = incidentService.getTicketUpdates(account, tokenId);
            sendJsonResponse(exchange, 200, updates);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleActivity(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        try {
            List<Map<String, Object>> list;
            if (account.getRole() == Role.USER) {
                list = incidentService.getUserActivity(account.getUsername());
            } else if (account.getRole() == Role.TECHNICIAN) {
                list = incidentService.getTechnicianActivity(account.getUsername());
            } else {
                list = incidentService.getManagerActivity();
            }
            sendJsonResponse(exchange, 200, list);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleTechnicians(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not authenticated.");
            return;
        }
        try {
            List<Map<String, String>> list = incidentService.getTechnicianDirectory();
            sendJsonResponse(exchange, 200, list);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("database_connected", DatabaseConnections.testConnection());
        map.put("jdbc_url", DatabaseConnections.getEffectiveUrl());
        sendJsonResponse(exchange, 200, map);
    }

    private void handlePriorityScore(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String issueType = query.getOrDefault("issue_type", "");
        String description = query.getOrDefault("description", "");
        String designation = query.getOrDefault("designation", "");
        String level = incidentService.calculatePriorityLevel(issueType, description, designation);
        int score = incidentService.calculatePriorityScore(issueType, description, designation);
        Map<String, Object> result = new HashMap<>();
        result.put("priority_level", level);
        result.put("priority_score", score);
        sendJsonResponse(exchange, 200, result);
    }

    // -------------------------------------------------------------------------
    // Admin endpoints
    // -------------------------------------------------------------------------
    private void handleAdminStats(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        try {
            Map<String, Object> stats = incidentService.adminGetStats();
            sendJsonResponse(exchange, 200, stats);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminUsers(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        try {
            List<Map<String, Object>> users = incidentService.adminGetAllUsers();
            sendJsonResponse(exchange, 200, users);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminRegistrations(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || account.getRole() != Role.ADMIN) {
            sendError(exchange, 403, "Only the main manager can review registrations.");
            return;
        }
        try {
            sendJsonResponse(exchange, 200, incidentService.adminGetPendingRegistrations());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminRegistrationDecision(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || account.getRole() != Role.ADMIN) {
            sendError(exchange, 403, "Only the main manager can approve registrations.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        Map<String, String> params = JsonUtil.parseSimpleJson(readRequestBody(exchange));
        try {
            incidentService.adminApproveRegistration(account, params.get("username"),
                    "APPROVE".equalsIgnoreCase(params.get("decision")));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminSetRole(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            incidentService.adminSetUserRole(account, params.get("username"), params.get("role"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminCloseTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            incidentService.adminCloseTicket(account, params.get("token_id"), params.get("note"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminReopenTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);

        try {
            incidentService.adminReopenTicket(account, params.get("token_id"), params.get("note"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            sendJsonResponse(exchange, 200, ok);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleAdminAllTickets(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || !account.getRole().isAdminOrManager()) {
            sendError(exchange, 403, "Admin access required.");
            return;
        }
        try {
            List<Map<String, Object>> tickets = incidentService.getAllTickets();
            sendJsonResponse(exchange, 200, tickets);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static Map<String, Object> mapUser(UserAccount acc) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", acc.getUsername());
        u.put("employee_id", acc.getEmployeeId());
        u.put("department", acc.getDepartment());
        u.put("designation", acc.getDesignation());
        u.put("role", acc.getRole().name());
        return u;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
        return map;
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, Object data) throws IOException {
        String json = JsonUtil.toJson(data);
        sendResponse(exchange, status, json, "application/json; charset=UTF-8");
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error", message);
        sendJsonResponse(exchange, status, map);
    }

    private static void sendResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
