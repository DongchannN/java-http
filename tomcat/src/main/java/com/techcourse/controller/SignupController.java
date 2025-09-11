package com.techcourse.controller;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.catalina.Session;
import org.apache.catalina.SessionManager;
import org.apache.catalina.controller.AbstractController;
import org.apache.coyote.http11.HttpCookie;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignupController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(SignupController.class);
    private static final String INDEX_PAGE = "/index.html";
    private static final String JSESSIONID = "JSESSIONID";
    private static final String USER_ATTRIBUTE = "user";

    @Override
    protected HttpResponse doGet(HttpRequest request) throws Exception {
        User currentUser = getCurrentUser(request);
        if (currentUser != null) {
            return buildRedirectResponse(INDEX_PAGE);
        }
        String path = request.path();
        String filePath = path.startsWith("/") ? path.substring(1) : path;

        try (
                InputStream inputStream = getClass().getClassLoader()
                        .getResourceAsStream("static/" + filePath + ".html")
        ) {
            if (inputStream == null) {
                return buildNotFoundResponse();
            } else {
                return buildStaticFileResponse(inputStream, filePath);
            }
        }
    }

    @Override
    protected HttpResponse doPost(HttpRequest request) throws Exception {
        Map<String, String> params = parseFormBody(request);
        String account = params.get("account");
        String password = params.get("password");
        String email = params.get("email");

        User user = new User(account, password, email);
        InMemoryUserRepository.save(user);

        String sessionId = createSession(user);
        return buildLoginSuccessResponse(sessionId);
    }

    private Map<String, String> parseFormBody(HttpRequest httpRequest) {
        String body = httpRequest.body();
        Map<String, String> formData = new HashMap<>();

        if (body == null || body.isEmpty()) {
            return formData;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                formData.put(keyValue[0], keyValue[1]);
            }
        }

        return formData;
    }

    private String createSession(User user) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId);
        session.setAttribute(USER_ATTRIBUTE, user);
        SessionManager.add(session);
        log.info("세션 생성: sessionId={}, user={}", sessionId, user.getAccount());
        return sessionId;
    }

    private HttpResponse buildLoginSuccessResponse(String sessionId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", INDEX_PAGE);
        headers.put("Set-Cookie", JSESSIONID + "=" + sessionId);
        return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
    }

    private HttpResponse buildRedirectResponse(String location) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", location);
        return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
    }

    private User getCurrentUser(HttpRequest request) {
        HttpCookie cookie = HttpCookie.from(request.headers().get("Cookie"));
        String sessionId = cookie.getJSessionId();
        if (sessionId != null) {
            Session session = SessionManager.getSession(sessionId);
            if (session != null) {
                return (User) session.getAttribute(USER_ATTRIBUTE);
            }
        }
        return null;
    }

    private HttpResponse buildNotFoundResponse() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/404.html")) {
            if (inputStream == null) {
                String responseBody = "<html><body><h1>404 Not Found</h1></body></html>";
                return new HttpResponse("HTTP/1.1", 404, "Not Found", headers, responseBody);
            } else {
                try (
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                ) {
                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    return new HttpResponse("HTTP/1.1", 404, "Not Found", headers, responseBody);
                }
            }
        }
    }

    private HttpResponse buildStaticFileResponse(InputStream inputStream, String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseBody = reader.lines().collect(Collectors.joining("\n"));
            responseBody += "\n";
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", getContentType(filePath));

            return new HttpResponse("HTTP/1.1", 200, "OK", headers, responseBody);
        }
    }

    private String getContentType(String filePath) {
        if (filePath.endsWith(".css")) {
            return "text/css;charset=utf-8";
        }
        return "text/html;charset=utf-8";
    }
}
