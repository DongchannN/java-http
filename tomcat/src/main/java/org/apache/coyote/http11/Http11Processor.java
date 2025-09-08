package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
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

    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
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

            HttpRequest request = HttpRequest.from(bufferedReader);
            HttpResponse response = processHttpRequest(request);
            String responseString = response.toResponseString();

            outputStream.write(responseString.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        }
    }

    public HttpResponse processHttpRequest(HttpRequest request) throws IOException {
        String requestTarget = request.requestTarget();

        if (isRootPath(requestTarget)) {
            return buildRootResponse();
        } else if (isLoginPath(requestTarget)) {
            if (request.method().equals("GET") && request.parseQueryString().isEmpty()) {
                return handleStaticFileRequest(requestTarget + ".html");
            } else if (request.method().equals("POST") || !request.parseQueryString().isEmpty()) {
                return handleLoginRequest(request);
            } else {
                return buildNotFoundResponse();
            }
        } else if (isSignupPath(requestTarget)) {
            if (request.method().equals("GET") && request.parseQueryString().isEmpty()) {
                return handleStaticFileRequest(requestTarget + ".html");
            } else if (request.method().equals("POST") || !request.parseQueryString().isEmpty()) {
                return handleSignupRequest(request);
            } else {
                return buildNotFoundResponse();
            }
        } else {
            return handleStaticFileRequest(requestTarget);
        }
    }

    private boolean isSignupPath(final String requestTarget) {
        return requestTarget.startsWith("/register");
    }

    private boolean isRootPath(String requestTarget) {
        return requestTarget.isEmpty() || requestTarget.equals("/");
    }

    private boolean isLoginPath(String requestTarget) {
        return requestTarget.startsWith("/login");
    }

    private HttpResponse buildRootResponse() {
        String responseBody = "Hello world!";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");
        return new HttpResponse("HTTP/1.1", 200, "OK", headers, responseBody);
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

    private HttpResponse handleLoginRequest(HttpRequest request) throws IOException {
        String requestTarget = request.requestTarget();

        if (requestTarget.contains("?")) {
            Map<String, String> queryParams = request.parseQueryString();
            String account = queryParams.get("account");
            String password = queryParams.get("password");

            if (account != null && password != null) {
                User user = InMemoryUserRepository.findByAccount(account).orElseThrow();
                if (user.checkPassword(password)) {
                    log.info("로그인 성공: {}", user);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Location", "/index.html");
                    headers.put("Set-Cookie", makeVeryDeliciousCookie());
                    return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
                }
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");

        try (InputStream inputStream = HttpResponse.class.getClassLoader().getResourceAsStream("static/401.html")) {
            if (inputStream == null) {
                String responseBody = "<html><body><h1>Login Page Not Found</h1></body></html>";
                return new HttpResponse("HTTP/1.1", 404, "Not Found", headers, responseBody);
            } else {
                try (
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                ) {
                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    return new HttpResponse("HTTP/1.1", 401, "Unauthorized", headers, responseBody);
                }
            }
        }
    }

    private HttpResponse handleSignupRequest(final HttpRequest request) {
        Map<String, String> params = parseFormBody(request);
        String account = params.get("account");
        String password = params.get("password");
        String email = params.get("email");
        User user = new User(account, password, email);
        InMemoryUserRepository.save(user);
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "/index.html");
        headers.put("Set-Cookie", makeVeryDeliciousCookie());
        return new HttpResponse("HTTP/1.1", 302, "Found", headers, "");
    }

    private String getContentType(String filePath) {
        if (filePath.endsWith(".css")) {
            return "text/css;charset=utf-8";
        }
        return "text/html;charset=utf-8";
    }

    public Map<String, String> parseFormBody(HttpRequest httpRequest) {
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

    private String makeVeryDeliciousCookie() {
        final UUID deliciousCookie = UUID.randomUUID();
        log.info("deliciousCookie = {}", deliciousCookie.toString());
        return "JSESSIONID=" + deliciousCookie;
    }
}
