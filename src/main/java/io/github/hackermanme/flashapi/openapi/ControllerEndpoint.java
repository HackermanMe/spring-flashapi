package io.github.hackermanme.flashapi.openapi;

import java.util.List;

public record ControllerEndpoint(
        String path,
        String httpMethod,
        String tag,
        String operationId,
        String summary,
        List<EndpointParameter> parameters,
        Class<?> requestBodyType,
        Class<?> returnType
) {

    public record EndpointParameter(
            String name,
            String in,
            boolean required,
            Class<?> type,
            String defaultValue
    ) {}
}
