import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import in.alphagroup.portal.policy.PasswordPolicy;
import in.alphagroup.portal.policy.PhoneNumbers;
import in.alphagroup.portal.policy.PolicyResult;
import in.alphagroup.portal.policy.Rule;
import in.alphagroup.portal.util.Emails;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sandbox demo harness — NOT the Spring Boot application.
 *
 * The Spring app cannot start in this sandbox: Maven Central is unreachable, so
 * the Spring/Jakarta jars that 29 of the 34 source files import cannot be
 * resolved. What is real here is the decision-making code: PasswordPolicy,
 * PolicyResult and Rule are the repo's own compiled classes, called exactly the
 * way PasswordCheckController calls them while the browser types.
 *
 * Serves the rendered login/reset pages and implements the one JSON endpoint
 * the page talks to, plus the static assets.
 */
public final class PortalHarness {

    /** Directory holding the rendered pages; args[1] overrides it. */
    private static Path site = Path.of("/tmp/site");

    /** Accounts taken during this run, so a duplicate behaves like the real one. */
    private static final java.util.Set<String> REGISTERED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String CSRF = "b1a6c0d2-7e4f-4a91-9c3b-5d8e2f7a1c60";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        if (args.length > 1) {
            site = Path.of(args[1]);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/api/password-check", PortalHarness::check);
        server.createContext("/signup", PortalHarness::signup);
        server.createContext("/healthz", ex -> json(ex, 200, "{\"status\":\"ok\"}"));
        server.createContext("/", PortalHarness::statics);

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("harness listening on http://0.0.0.0:" + port
                + "  (pages from " + site + ")");
        System.out.println("policy: min " + PasswordPolicy.MIN_LENGTH + " chars, "
                + PasswordPolicy.MIN_UPPERCASE + " upper, " + PasswordPolicy.MIN_LOWERCASE + " lower, "
                + PasswordPolicy.MIN_DIGITS + " digits, " + PasswordPolicy.MIN_SPECIAL + " special");
        System.out.println("rules: " + PasswordPolicy.RULES.stream().map(Rule::key).toList());
    }

    /* ---- the endpoint auth.js polls while you type ----------------------- */

    private static void check(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"ok\":false,\"code\":\"method\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(ex.getRequestHeaders().getFirst("Content-Type"), body);

        String password = form.getOrDefault("password", "");
        String email = form.getOrDefault("email", "").trim();
        String name = form.getOrDefault("name", "").trim();

        PolicyResult result = PasswordPolicy.evaluate(password, email, name);

        // Same envelope as PasswordCheckController.check().
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"ok\":true,\"valid\":").append(result.valid());
        sb.append(",\"empty\":").append(password.isEmpty());
        sb.append(",\"checks\":{");
        boolean first = true;
        for (var e : result.checks().entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("},\"violations\":[");
        for (int i = 0; i < result.violations().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(result.violations().get(i))).append('"');
        }
        sb.append("],\"score\":").append(result.score());
        sb.append(",\"strength\":\"").append(result.strength()).append('"');
        sb.append(",\"csrfToken\":\"").append(CSRF).append("\"}");

        // Log the verdict, never the password itself.
        System.out.printf(Locale.ROOT,
                "POST /api/password-check  len=%2d  valid=%-5s score=%3d  strength=%-6s  failed=%s%n",
                password.length(), result.valid(), result.score(), result.strength(), result.violations());

        json(ex, 200, sb.toString());
    }

    /* ---- sign-up: the repo's own PhoneNumbers / Emails / PasswordPolicy --- */
    /*                                                                        */
    /* AuthController.signup() runs the same checks in the same order. The    */
    /* Spring plumbing around it (binding, CSRF, JPA, sessions) is what is    */
    /* missing here, not the validation.                                     */

    private static void signup(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            statics(ex);
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(ex.getRequestHeaders().getFirst("Content-Type"), body);

        String name = form.getOrDefault("name", "").trim();
        String email = form.getOrDefault("email", "").trim();
        String country = form.getOrDefault("country_code", "").trim();
        String phone = form.getOrDefault("phone", "").trim();
        String password = form.getOrDefault("password", "");
        String confirm = form.getOrDefault("confirm_password", "");

        Map<String, String> errors = new LinkedHashMap<>();

        if (name.length() < 2) {
            errors.put("name", "Enter your full name (at least 2 characters).");
        }
        if (email.isEmpty()) {
            errors.put("email", "Enter your e-mail address.");
        } else if (!Emails.isValid(email)) {
            errors.put("email", "Enter a valid e-mail address, e.g. name@company.com.");
        }

        PhoneNumbers.Result number = PhoneNumbers.validate(country, phone);
        if (!number.valid()) {
            errors.put("phone", number.error());
        }

        if (password.isEmpty()) {
            errors.put("password", "Enter a password.");
        } else {
            PolicyResult policy = PasswordPolicy.evaluate(password, email, name);
            if (!policy.valid()) {
                errors.put("password", policy.violations().get(0));
            }
        }
        if (confirm.isEmpty()) {
            errors.put("confirm_password", "Re-enter your password.");
        } else if (!confirm.equals(password)) {
            errors.put("confirm_password", "Passwords do not match.");
        }

        System.out.printf(Locale.ROOT,
                "POST /signup  country=%-2s digits=%2s -> %-32s errors=%s%n",
                country.isEmpty() ? "??" : country,
                number.national().isEmpty() ? "-" : String.valueOf(number.national().length()),
                number.valid() ? number.e164() : "(rejected: " + number.error() + ")",
                errors.isEmpty() ? "none" : errors.keySet());

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"ok\":false,\"code\":\"validation\",\"message\":\"")
              .append(escape(errors.values().iterator().next())).append("\",\"errors\":{");
            boolean first = true;
            for (var e : errors.entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(e.getKey()).append("\":\"").append(escape(e.getValue())).append('"');
                first = false;
            }
            sb.append("},\"field\":\"").append(errors.keySet().iterator().next()).append("\"}");
            json(ex, 422, sb.toString());
            return;
        }

        if (!REGISTERED.add(email.toLowerCase(Locale.ROOT))) {
            String taken = "That e-mail address is already registered. Sign in instead.";
            System.out.printf(Locale.ROOT, "POST /signup  %s -> 409 user_exists%n", email);
            json(ex, 409, "{\"ok\":false,\"code\":\"user_exists\",\"message\":\"" + escape(taken)
                    + "\",\"errors\":{\"email\":\"" + escape(taken)
                    + "\"},\"field\":\"email\"}");
            return;
        }

        System.out.printf(Locale.ROOT, "POST /signup  %s -> 201 registered%n", email);
        json(ex, 200, "{\"ok\":true,\"code\":\"registered\","
                + "\"message\":\"Account created. Welcome to Alpha Group of India.\","
                + "\"redirect\":\"/dashboard?welcome=1\"}");
    }

    /* ---- static files ---------------------------------------------------- */

    private static void statics(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();
        String file = switch (path) {
            case "/dashboard" -> query != null && query.contains("welcome=1")
                    ? "dashboard-welcome.html"
                    : "dashboard.html";
            case "/", "/login" -> "login.html";
            case "/signup" -> "signup.html";
            case "/account" -> "dashboard.html";
            case "/reset-password" -> "reset-password.html";
            default -> path.replaceFirst("^/", "");
        };
        Path target = site.resolve(file).normalize();
        if (!target.startsWith(site) || !Files.isRegularFile(target)) {
            byte[] nf = "not found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, nf.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(nf); }
            return;
        }
        byte[] data = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", contentType(file));
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(data); }
    }

    private static String contentType(String file) {
        if (file.endsWith(".css")) return "text/css; charset=utf-8";
        if (file.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (file.endsWith(".png")) return "image/png";
        if (file.endsWith(".jpg") || file.endsWith(".jpeg")) return "image/jpeg";
        if (file.endsWith(".svg")) return "image/svg+xml";
        return "text/html; charset=utf-8";
    }

    private static void json(HttpExchange ex, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(data); }
    }

    /* ---- tiny multipart / urlencoded reader ------------------------------ */

    private static Map<String, String> parseForm(String contentType, String body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (contentType == null) return out;

        if (contentType.startsWith("application/x-www-form-urlencoded")) {
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
                }
            }
            return out;
        }
        if (!contentType.startsWith("multipart/form-data")) return out;

        String boundary = null;
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.startsWith("boundary=")) boundary = p.substring("boundary=".length());
        }
        if (boundary == null) return out;

        for (String chunk : body.split("--" + java.util.regex.Pattern.quote(boundary))) {
            int nameAt = chunk.indexOf("name=\"");
            if (nameAt < 0) continue;
            int nameEnd = chunk.indexOf('"', nameAt + 6);
            if (nameEnd < 0) continue;
            String name = chunk.substring(nameAt + 6, nameEnd);
            int headerEnd = chunk.indexOf("\r\n\r\n");
            if (headerEnd < 0) continue;
            String value = chunk.substring(headerEnd + 4);
            if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
            out.put(name, value);
        }
        return out;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private PortalHarness() {
    }
}
