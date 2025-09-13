package org.apache.catalina.controller;

import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.apache.coyote.http11.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class DefaultController implements Controller {

    @Override
    public HttpResponse service(final HttpRequest request) throws Exception {
        String responseBody = "Hello world!";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");
        
        return new HttpResponse(
                request.version(),
                HttpStatus.OK.getCode(),
                HttpStatus.OK.getReasonPhrase(),
                headers,
                responseBody
        );
    }
}
