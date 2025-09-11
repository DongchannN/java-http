package org.apache.catalina.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractController implements Controller {

    @Override
    public final HttpResponse service(HttpRequest request) throws Exception {
        switch (request.method()) {
            case "GET" -> {
                return doGet(request);
            }
            case "POST" -> {
                return doPost(request);
            }
            default -> {
                return buildMethodNotAllowedResponse();
            }
        }
    }

    protected abstract HttpResponse doGet(HttpRequest request) throws Exception;
    
    protected abstract HttpResponse doPost(HttpRequest request) throws Exception;

    private HttpResponse buildMethodNotAllowedResponse() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/405.html")) {
            if (inputStream == null) {
                String responseBody = "<html><body><h1>405 Method Not Allowed</h1></body></html>";
                return new HttpResponse("HTTP/1.1", 405, "Method Not Allowed", headers, responseBody);
            } else {
                try (
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                ) {
                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    return new HttpResponse("HTTP/1.1", 404, "Method Not Allowed", headers, responseBody);
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
