package io.github.hackermanme.flashapi.openapi;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public final class ControllerScanner {

    private final RequestMappingHandlerMapping handlerMapping;
    private final Set<String> excludedPrefixes;

    public ControllerScanner(RequestMappingHandlerMapping handlerMapping, Set<String> excludedPrefixes) {
        this.handlerMapping = handlerMapping;
        this.excludedPrefixes = excludedPrefixes;
    }

    public List<ControllerEndpoint> scan() {
        List<ControllerEndpoint> endpoints = new ArrayList<>();
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : methods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            Class<?> controllerClass = handlerMethod.getBeanType();
            if (!controllerClass.isAnnotationPresent(RestController.class)) continue;
            if (isFlashApiInternal(controllerClass)) continue;

            Set<String> patterns = extractPatterns(mappingInfo);
            Set<String> httpMethods = extractMethods(mappingInfo);

            for (String pattern : patterns) {
                if (isExcluded(pattern)) continue;
                for (String httpMethod : httpMethods) {
                    endpoints.add(buildEndpoint(pattern, httpMethod, controllerClass, handlerMethod));
                }
            }
        }

        endpoints.sort(Comparator.comparing(ControllerEndpoint::path)
                .thenComparing(ControllerEndpoint::httpMethod));
        return endpoints;
    }

    private boolean isFlashApiInternal(Class<?> cls) {
        return cls.getPackageName().startsWith("io.github.hackermanme.flashapi");
    }

    private boolean isExcluded(String pattern) {
        for (String prefix : excludedPrefixes) {
            if (pattern.startsWith(prefix)) return true;
        }
        return false;
    }

    private Set<String> extractPatterns(RequestMappingInfo info) {
        Set<String> patterns = new LinkedHashSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns()
                    .forEach(p -> patterns.add(p.getPatternString()));
        } else if (info.getPatternsCondition() != null) {
            patterns.addAll(info.getPatternsCondition().getPatterns());
        }
        return patterns;
    }

    private Set<String> extractMethods(RequestMappingInfo info) {
        Set<String> methods = new LinkedHashSet<>();
        if (info.getMethodsCondition() != null && !info.getMethodsCondition().getMethods().isEmpty()) {
            info.getMethodsCondition().getMethods()
                    .forEach(m -> methods.add(m.name().toLowerCase()));
        } else {
            methods.add("get");
        }
        return methods;
    }

    private ControllerEndpoint buildEndpoint(String path, String httpMethod,
                                             Class<?> controllerClass, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        String tag = resolveTag(controllerClass);
        String operationId = method.getName();
        String summary = resolveSummary(method, httpMethod, path);
        List<ControllerEndpoint.EndpointParameter> params = resolveParameters(method);
        Class<?> requestBodyType = resolveRequestBody(method);
        Class<?> returnType = resolveReturnType(method);

        return new ControllerEndpoint(path, httpMethod, tag, operationId, summary,
                params, requestBodyType, returnType);
    }

    private String resolveTag(Class<?> controllerClass) {
        String name = controllerClass.getSimpleName();
        if (name.endsWith("Controller")) {
            name = name.substring(0, name.length() - "Controller".length());
        }
        return name;
    }

    private String resolveSummary(Method method, String httpMethod, String path) {
        String name = method.getName();
        String readable = name.replaceAll("([A-Z])", " $1").trim();
        return readable.substring(0, 1).toUpperCase() + readable.substring(1);
    }

    private List<ControllerEndpoint.EndpointParameter> resolveParameters(Method method) {
        List<ControllerEndpoint.EndpointParameter> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            PathVariable pathVar = param.getAnnotation(PathVariable.class);
            if (pathVar != null) {
                String name = pathVar.value().isEmpty() ? param.getName() : pathVar.value();
                params.add(new ControllerEndpoint.EndpointParameter(name, "path", true, param.getType(), null));
                continue;
            }
            RequestParam reqParam = param.getAnnotation(RequestParam.class);
            if (reqParam != null) {
                String name = reqParam.value().isEmpty() ? param.getName() : reqParam.value();
                boolean required = reqParam.required();
                String defaultVal = ValueConstants.DEFAULT_NONE.equals(reqParam.defaultValue()) ? null : reqParam.defaultValue();
                params.add(new ControllerEndpoint.EndpointParameter(name, "query", required, param.getType(), defaultVal));
            }
            RequestHeader reqHeader = param.getAnnotation(RequestHeader.class);
            if (reqHeader != null) {
                String name = reqHeader.value().isEmpty() ? param.getName() : reqHeader.value();
                params.add(new ControllerEndpoint.EndpointParameter(name, "header", reqHeader.required(), param.getType(), null));
            }
        }
        return params;
    }

    private Class<?> resolveRequestBody(Method method) {
        for (Parameter param : method.getParameters()) {
            if (param.isAnnotationPresent(RequestBody.class)) {
                return param.getType();
            }
        }
        return null;
    }

    private Class<?> resolveReturnType(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) return null;
        if (returnType.getName().startsWith("org.springframework.http.ResponseEntity")) {
            java.lang.reflect.Type generic = method.getGenericReturnType();
            if (generic instanceof java.lang.reflect.ParameterizedType pt) {
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> cls) {
                    return cls;
                }
            }
            return null;
        }
        return returnType;
    }
}
