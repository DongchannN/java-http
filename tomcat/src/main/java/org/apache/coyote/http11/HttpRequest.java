package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public record HttpRequest(
        String method,
        String requestTarget,
        String version,
        Map<String, String> headers,
        String body
) {
    
    public static HttpRequest from(BufferedReader bufferedReader) throws IOException {
        String startLine = readRequestLine(bufferedReader);
        
        String[] parts = startLine.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid HTTP request format: " + startLine);
        }
        
        String method = parts[0];
        String requestTarget = parts[1];
        String version = parts[2];
        
        Map<String, String> headers = parseHeaders(bufferedReader);
        String body = ""; // TODO: body 파싱 로직 구현
        
        return new HttpRequest(method, requestTarget, version, headers, body);
    }

    private static String readRequestLine(BufferedReader bufferedReader) throws IOException {
        String startLine = bufferedReader.readLine();
        if (startLine == null || startLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid request: empty start line");
        }
        return startLine;
    }

    private static Map<String, String> parseHeaders(BufferedReader bufferedReader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    public Map<String, String> parseQueryString() {
        Map<String, String> params = new HashMap<>();

        int queryStart = requestTarget.indexOf("?");
        if (queryStart == -1) {
            return params;
        }

        String queryString = requestTarget.substring(queryStart + 1);
        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }

        return params;
    }
}
