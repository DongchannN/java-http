package org.apache.coyote.http11;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public record HttpResponse(
        String version,
        int statusCode,
        String reasonPhrase,
        Map<String, String> headers,
        String body
) {

    public String toResponseString() {
        StringBuilder formatted = new StringBuilder();

        formatted.append(version).append(" ")
                .append(statusCode).append(" ")
                .append(reasonPhrase).append("\r\n");

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                formatted.append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\r\n");
            }
        }

        formatted.append("Content-Length: ")
                .append(body.getBytes(StandardCharsets.UTF_8).length)
                .append("\r\n");
        formatted.append("\r\n");
        formatted.append(body);

        return formatted.toString();
    }
}
