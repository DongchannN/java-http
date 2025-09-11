package org.apache.coyote.http11;

import org.apache.catalina.SessionManager;
import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
import org.apache.catalina.Session;
import org.apache.catalina.controller.ControllerContainer;
import org.apache.catalina.controller.Controller;
import com.techcourse.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.coyote.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);
    private static final String INDEX_PAGE = "/index.html";
    private static final String LOGIN_FAILED_PAGE = "/401.html";
    private static final String JSESSIONID = "JSESSIONID";
    private static final String USER_ATTRIBUTE = "user";

    private final Socket connection;
    private final ControllerContainer controllerContainer;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
        this.controllerContainer = new ControllerContainer();
    }

    @Override
    public void run() {
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (
                final var inputStream = connection.getInputStream();
                final var outputStream = connection.getOutputStream();
                final var bufferedReader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1))
        ) {

            HttpRequest request = RequestParser.parse(bufferedReader);
            HttpResponse response = processHttpRequest(request);
            String responseString = response.toResponseString();

            outputStream.write(responseString.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public HttpResponse processHttpRequest(HttpRequest request) throws Exception {
        String path = request.path();
        User currentUser = getCurrentUser(request);
        
        if (isLoginPath(path)) {
            if (request.method().equals("GET") && currentUser != null) {
                return buildRedirectResponse(INDEX_PAGE);
            }
            if (request.method().equals("GET")) {
                return handleStaticFileRequest(path + ".html");
            } else if (request.method().equals("POST")) {
                return handleLoginRequest(request);
            } else {
                return buildNotFoundResponse();
            }
        } else if (isSignupPath(path)) {
            if (request.method().equals("GET") && currentUser != null) {
                return buildRedirectResponse(INDEX_PAGE);
            }
            if (request.method().equals("GET")) {
                return handleStaticFileRequest(path + ".html");
            } else if (request.method().equals("POST")) {
                return handleSignupRequest(request);
            } else {
                return buildNotFoundResponse();
            }
        } else {
            Controller controller = controllerContainer.findController(path);
            HttpResponse response = controller.service(request);
            return ensureSessionCookie(request, response);
        }
    }


    private boolean isLoginPath(String requestTarget) {
        return requestTarget.startsWith("/login");
    }

    private boolean isSignupPath(final String requestTarget) {
        return requestTarget.startsWith("/register");
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


    private HttpResponse buildRedirectResponse(String location) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", location);
        return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
    }

    private HttpResponse ensureSessionCookie(HttpRequest request, HttpResponse response) {
        HttpCookie cookie = HttpCookie.from(request.headers().get("Cookie"));
        if (!cookie.hasJSessionId()) {
            String newSessionId = UUID.randomUUID().toString();
            Map<String, String> newHeaders = new HashMap<>(response.headers());
            newHeaders.put("Set-Cookie", JSESSIONID + "=" + newSessionId);
            return new HttpResponse(response.version(), response.statusCode(), 
                    response.reasonPhrase(), newHeaders, response.body());
        }
        return response;
    }

    private HttpResponse handleStaticFileRequest(String requestTarget) throws IOException {
        String filePath = requestTarget.startsWith("/") ? requestTarget.substring(1) : requestTarget;

        try (InputStream inputStream = HttpResponse.class.getClassLoader().getResourceAsStream("static/" + filePath)) {
            if (inputStream == null) {
                return buildNotFoundResponse();
            } else {
                return buildStaticFileResponse(inputStream, filePath);
            }
        }
    }

    private HttpResponse buildNotFoundResponse() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");

        try (InputStream inputStream = HttpResponse.class.getClassLoader().getResourceAsStream("static/404.html")) {
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

    private HttpResponse handleLoginRequest(HttpRequest request) {
        Map<String, String> formData = parseFormBody(request);
        String account = formData.get("account");
        String password = formData.get("password");
        
        HttpResponse loginResponse = tryAuthenticate(account, password);
        if (loginResponse != null) {
            return loginResponse;
        }

        return buildRedirectResponse(LOGIN_FAILED_PAGE);
    }

    private HttpResponse handleSignupRequest(final HttpRequest request) {
        Map<String, String> params = parseFormBody(request);
        String account = params.get("account");
        String password = params.get("password");
        String email = params.get("email");
        User user = new User(account, password, email);
        InMemoryUserRepository.save(user);
        
        String sessionId = createSession(user);
        return buildLoginSuccessResponse(sessionId);
    }

    private String getContentType(String filePath) {
        if (filePath.endsWith(".css")) {
            return "text/css;charset=utf-8";
        }
        return "text/html;charset=utf-8";
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



    private HttpResponse tryAuthenticate(String account, String password) {
        if (account != null && password != null) {
            User user = InMemoryUserRepository.findByAccount(account).orElseThrow();
            if (user.checkPassword(password)) {
                String sessionId = createSession(user);
                
                log.info("로그인 성공: {}", user);
                return buildLoginSuccessResponse(sessionId);
            }
        }
        return null;
    }

    private HttpResponse buildLoginSuccessResponse(String sessionId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", INDEX_PAGE);
        headers.put("Set-Cookie", JSESSIONID + "=" + sessionId);
        return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
    }

    private String createSession(User user) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId);
        session.setAttribute(USER_ATTRIBUTE, user);
        SessionManager.add(session);
        log.info("세션 생성: sessionId={}, user={}", sessionId, user.getAccount());
        return sessionId;
    }
}
