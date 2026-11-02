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
            server.createContext("/", this::handleRoot);
            server.createContext("/api/session", this::handleSession);
            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/register", this::handleRegister);
            server.createContext("/api/logout", this::handleLogout);
            server.createContext("/api/tickets", this::handleTickets);
            server.createContext("/api/tickets/create", this::handleCreateTicket);
            server.createContext("/api/tickets/claim", this::handleClaimTicket);
            server.createContext("/api/tickets/update", this::handleUpdateTicket);
            server.createContext("/api/tickets/reassign", this::handleReassignTicket);
            server.createContext("/api/ticket-updates", this::handleTicketUpdates);
            server.createContext("/api/activity", this::handleActivity);
            server.createContext("/api/technicians", this::handleTechnicians);
            server.createContext("/api/status", this::handleStatus);

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

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String html = WebUiRenderer.renderHtml();
        sendResponse(exchange, 200, html, "text/html; charset=UTF-8");
    }

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
                    params.get("designation"));
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
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

    private void handleTickets(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not logged in.");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String filter = query.getOrDefault("filter", "my");
        String type = query.get("type");

        try {
            List<Map<String, Object>> tickets;
            if ("open".equalsIgnoreCase(filter)) {
                tickets = incidentService.getOpenTickets(type);
            } else if ("assigned".equalsIgnoreCase(filter)) {
                tickets = incidentService.getAssignedTickets(account.getEmployeeId());
            } else if ("unresolved".equalsIgnoreCase(filter)) {
                tickets = incidentService.getUnresolvedTickets();
            } else if ("all".equalsIgnoreCase(filter)) {
                tickets = incidentService.getAllTickets();
            } else {
                tickets = incidentService.getTicketsForUser(account.getUsername());
            }
            sendJsonResponse(exchange, 200, tickets);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleCreateTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not logged in.");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);
        try {
            String tokenId = incidentService.createTicket(
                    account, params.get("issue_type"), params.get("description"));
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("token_id", tokenId);
            sendJsonResponse(exchange, 200, res);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleClaimTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || account.getRole() != Role.TECHNICIAN) {
            sendError(exchange, 403, "Technician access required.");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);
        String tokenId = params.get("token_id");
        try {
            incidentService.claimTicket(account, tokenId);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            sendJsonResponse(exchange, 200, res);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleUpdateTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not logged in.");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);
        String tokenId = params.get("token_id");
        String status = params.get("status");
        int minutes = 0;
        try {
            if (params.containsKey("estimated_minutes")) {
                minutes = Integer.parseInt(params.get("estimated_minutes"));
            }
        } catch (NumberFormatException ignored) {
        }
        String note = params.get("note");

        try {
            if (account.getRole() == Role.TECHNICIAN) {
                incidentService.updateTicketAsTechnician(account, tokenId, status, minutes, note);
            } else if (account.getRole() == Role.MANAGER) {
                incidentService.updateTicketAsManager(account, tokenId, status, minutes, note);
            } else {
                sendError(exchange, 403, "Staff privileges required to update status.");
                return;
            }
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            sendJsonResponse(exchange, 200, res);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleReassignTicket(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null || account.getRole() != Role.MANAGER) {
            sendError(exchange, 403, "Manager privileges required to reassign tickets.");
            return;
        }
        String body = readRequestBody(exchange);
        Map<String, String> params = JsonUtil.parseSimpleJson(body);
        String tokenId = params.get("token_id");
        String techId = params.get("technician_id");
        String dept = params.get("department");
        String note = params.get("note");

        try {
            incidentService.reassignTicketAsManager(account, tokenId, techId, dept, note);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            sendJsonResponse(exchange, 200, res);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleTicketUpdates(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String token = query.get("token");
        if (token == null || token.trim().isEmpty()) {
            sendError(exchange, 400, "token parameter is required.");
            return;
        }
        try {
            List<Map<String, Object>> list = incidentService.getTicketUpdates(token.trim());
            sendJsonResponse(exchange, 200, list);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleActivity(HttpExchange exchange) throws IOException {
        UserAccount account = SessionManager.getAccountFromExchange(exchange);
        if (account == null) {
            sendError(exchange, 401, "Not logged in.");
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
            byte[] buf = new byte[1024];
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
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
