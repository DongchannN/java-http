package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RequestParser {

    public static HttpRequest parse(BufferedReader bufferedReader) throws IOException {
        String startLine = readRequestLine(bufferedReader);
        String[] requestLine = parseRequestLine(startLine);
        String method = requestLine[0];
        String requestTarget = requestLine[1];
        String version = requestLine[2];

        Map<String, String> headers = parseHeaders(bufferedReader);

        String body = parseBody(bufferedReader, headers);

        String path = extractPath(requestTarget);
        Map<String, String> queryParams = parseQueryString(requestTarget);

        return new HttpRequest(
                method,
                path,
                version,
                headers,
                body,
                queryParams
        );
    }

    private static String readRequestLine(BufferedReader bufferedReader) throws IOException {
        String startLine = bufferedReader.readLine();
        if (startLine == null || startLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid request: empty start line");
        }
        return startLine;
    }

    private static String[] parseRequestLine(String startLine) {
        String[] parts = startLine.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid HTTP request format: " + startLine);
        }

        return parts;
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

    private static String parseBody(BufferedReader bufferedReader, Map<String, String> headers) throws IOException {
        String contentLengthHeader = headers.get("Content-Length");
        if (contentLengthHeader == null || contentLengthHeader.isEmpty()) {
            return "";
        }

        try {
            int contentLength = Integer.parseInt(contentLengthHeader);
            if (contentLength <= 0) {
                return "";
            }

            char[] buffer = new char[contentLength];
            int totalRead = 0;

            while (totalRead < contentLength) {
                int read = bufferedReader.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }

            return new String(buffer, 0, totalRead);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Content-Length header: " + contentLengthHeader);
        }
    }

    private static String extractPath(String requestTarget) {
        int queryStart = requestTarget.indexOf("?");
        if (queryStart == -1) {
            return requestTarget;
        }
        return requestTarget.substring(0, queryStart);
    }

    private static Map<String, String> parseQueryString(String requestTarget) {
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
