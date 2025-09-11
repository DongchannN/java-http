package org.apache.coyote.http11;

import com.techcourse.controller.LoginController;
import com.techcourse.controller.SignupController;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.catalina.controller.Controller;
import org.apache.catalina.controller.ControllerContainer;
import org.apache.coyote.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);
    private static final String JSESSIONID = "JSESSIONID";

    private final Socket connection;
    private final ControllerContainer controllerContainer;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
        this.controllerContainer = new ControllerContainer();
        controllerContainer.addControllerWithPath("/login", new LoginController());
        controllerContainer.addControllerWithPath("/register", new SignupController());
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

        Controller controller = controllerContainer.findController(path);
        HttpResponse response = controller.service(request);
        return ensureSessionCookie(request, response);
    }

    private HttpResponse ensureSessionCookie(HttpRequest request, HttpResponse response) {
        HttpCookie cookie = HttpCookie.from(request.headers().get("Cookie"));
        if (!cookie.hasJSessionId()) {
            String newSessionId = UUID.randomUUID().toString();
            Map<String, String> newHeaders = new HashMap<>(response.headers());
            newHeaders.put("Set-Cookie", JSESSIONID + "=" + newSessionId);
            return new HttpResponse(
                    response.version(), response.statusCode(),
                    response.reasonPhrase(), newHeaders, response.body()
            );
        }
        return response;
    }

}
