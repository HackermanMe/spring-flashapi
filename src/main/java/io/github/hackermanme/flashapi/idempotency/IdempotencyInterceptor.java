package io.github.hackermanme.flashapi.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAY_HEADER = "Idempotency-Replay";
    private static final String ATTR_KEY = "idempotency.key";
    private static final String ATTR_BODY = "idempotency.body";
    private static final String ATTR_PATH = "idempotency.path";
    private static final String ATTR_METHOD = "idempotency.method";
    private static final Set<String> MUTABLE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;

    public IdempotencyInterceptor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (key == null || key.isEmpty()) {
            return true;
        }

        String method = request.getMethod();
        if (!MUTABLE_METHODS.contains(method)) {
            return true;
        }

        String path = request.getRequestURI();
        String body = readBody(request);

        Optional<IdempotencyRecord> existing = idempotencyService.checkKey(key, path, method, body);

        if (existing.isPresent()) {
            replayResponse(response, existing.get());
            return false;
        }

        request.setAttribute(ATTR_KEY, key);
        request.setAttribute(ATTR_BODY, body);
        request.setAttribute(ATTR_PATH, path);
        request.setAttribute(ATTR_METHOD, method);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String key = (String) request.getAttribute(ATTR_KEY);
        if (key == null || ex != null) {
            return;
        }

        String path = (String) request.getAttribute(ATTR_PATH);
        String method = (String) request.getAttribute(ATTR_METHOD);
        String requestBody = (String) request.getAttribute(ATTR_BODY);

        int status = response.getStatus();

        if (status < 200 || status >= 300) {
            return;
        }

        String responseBody = extractResponseBody(response);
        Map<String, String> responseHeaders = extractResponseHeaders(response);

        idempotencyService.storeResponse(key, path, method, requestBody, status, responseBody, responseHeaders);
    }

    private void replayResponse(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.getResponseStatus());
        response.setContentType("application/json");
        response.setHeader(IDEMPOTENCY_REPLAY_HEADER, "true");

        Map<String, String> headers = idempotencyService.parseResponseHeaders(record.getResponseHeaders());
        headers.forEach(response::setHeader);

        if (record.getResponseBody() != null) {
            response.getWriter().write(record.getResponseBody());
        }

        log.debug("Replayed response for idempotency key '{}'", record.getIdempotencyKey());
    }

    private String readBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return "";
        }

        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
        byte[] buf = wrapper.getContentAsByteArray();
        if (buf.length == 0) {
            return "";
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private String extractResponseBody(HttpServletResponse response) {
        if (!(response instanceof ContentCachingResponseWrapper)) {
            return "";
        }

        ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
        byte[] buf = wrapper.getContentAsByteArray();
        if (buf.length == 0) {
            return "";
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        for (String headerName : response.getHeaderNames()) {
            headers.put(headerName, response.getHeader(headerName));
        }
        return headers;
    }
}
