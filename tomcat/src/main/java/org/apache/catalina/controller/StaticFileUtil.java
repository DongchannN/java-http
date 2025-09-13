package org.apache.catalina.controller;

import org.apache.catalina.ResponseUtil;
import org.apache.coyote.http11.HttpResponse;
import org.apache.coyote.http11.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StaticFileUtil {

    public static HttpResponse buildStaticFileResponse(String filePath) throws IOException {
        try (InputStream inputStream = StaticFileUtil.class.getClassLoader()
                .getResourceAsStream("static/" + filePath)) {
            if (inputStream == null) {
                return ResponseUtil.buildNotFoundResponse();
            }
            return buildStaticFileResponse(inputStream, filePath);
        }
    }

    public static HttpResponse buildStaticFileResponse(InputStream inputStream, String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseBody = reader.lines().collect(Collectors.joining("\n"));
            responseBody += "\n";
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", getContentType(filePath));

            return new HttpResponse(
                    "HTTP/1.1",
                    HttpStatus.OK.getCode(),
                    HttpStatus.OK.getReasonPhrase(),
                    headers,
                    responseBody
            );
        }
    }

    private static String getContentType(String filePath) {
        if (filePath.endsWith(".css")) {
            return "text/css;charset=utf-8";
        }
        return "text/html;charset=utf-8";
    }
}