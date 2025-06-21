package com.openapispecs.generator.plugin.parser;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OpenApiBuilder {
    @SuppressWarnings("rawtypes")
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();
    private final Map<Class<? extends Throwable>, Method> globalExceptionHandlers = new ConcurrentHashMap<>();

    public OpenAPI build(String title, String version, String description, Set<Class<?>> controllers,
            Set<Class<?>> controllerAdvices) {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info().title(title).version(version).description(description))
                .paths(new Paths())
                .components(new Components());

        processControllerAdvice(controllerAdvices);

        for (Class<?> controller : controllers) {
            processController(controller, openAPI);
        }

        openAPI.getComponents().setSchemas(this.schemas);
        return openAPI;
    }

    private void processControllerAdvice(Set<Class<?>> controllerAdvices) {
        for (Class<?> adviceClass : controllerAdvices) {
            for (Method method : adviceClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ExceptionHandler.class)) {
                    ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
                    for (Class<? extends Throwable> exceptionClass : handler.value()) {
                        // Global handlers can be overwritten by more specific ones, but for now, first
                        // one wins.
                        globalExceptionHandlers.putIfAbsent(exceptionClass, method);
                    }
                }
            }
        }
    }

    private void processController(Class<?> controller, OpenAPI openAPI) {
        String classLevelPath = getPathFromAnnotation(controller.getAnnotation(RequestMapping.class), "");

        for (Method method : controller.getDeclaredMethods()) {
            processMethod(method, classLevelPath, openAPI);
        }
    }

    private void processMethod(Method method, String classLevelPath, OpenAPI openAPI) {
        Optional<MappingInfo> mappingInfoOpt = findMappingAnnotation(method);

        mappingInfoOpt.ifPresent(mappingInfo -> {
            String methodLevelPath = getPathFromAnnotation(mappingInfo.annotation, "");
            String fullPath = (classLevelPath + methodLevelPath).replaceAll("//", "/");

            PathItem pathItem = openAPI.getPaths().computeIfAbsent(fullPath, k -> new PathItem());
            Operation operation = createOperation(method);

            pathItem.operation(mappingInfo.httpMethod, operation);
        });
    }

    private Operation createOperation(Method method) {
        Operation operation = new Operation()
                .operationId(method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .summary(StringUtils.capitalize(method.getName()));

        // Process parameters (@PathVariable, @RequestParam, @RequestHeader)
        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)) {
                operation.setRequestBody(createRequestBody(parameter));
            } else {
                createParameter(parameter).ifPresent(operation::addParametersItem);
            }
        }

        // Process responses
        operation.setResponses(createApiResponses(method));

        return operation;
    }

    private RequestBody createRequestBody(java.lang.reflect.Parameter parameter) {
        Schema<?> schema = createSchema(parameter.getParameterizedType(), parameter);
        MediaType mediaType = new MediaType().schema(schema);
        Content content = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                mediaType);

        org.springframework.web.bind.annotation.RequestBody requestBodyAnn = parameter
                .getAnnotation(org.springframework.web.bind.annotation.RequestBody.class);

        return new RequestBody()
                .content(content)
                .required(requestBodyAnn.required());
    }

    private Optional<Parameter> createParameter(java.lang.reflect.Parameter parameter) {
        Parameter p = null;
        if (parameter.isAnnotationPresent(PathVariable.class)) {
            PathVariable ann = parameter.getAnnotation(PathVariable.class);
            String paramName = StringUtils.hasText(ann.value()) ? ann.value() : parameter.getName();
            p = new Parameter().in("path").name(paramName).required(true); // Path variables are always required
        } else if (parameter.isAnnotationPresent(RequestParam.class)) {
            RequestParam ann = parameter.getAnnotation(RequestParam.class);
            // Use the annotation's value/name if present, otherwise fall back to the
            // parameter's actual name
            String paramName = StringUtils.hasText(ann.value()) ? ann.value() : parameter.getName();
            p = new Parameter().in("query").name(paramName).required(ann.required());
        } else if (parameter.isAnnotationPresent(RequestHeader.class)) {
            RequestHeader ann = parameter.getAnnotation(RequestHeader.class);
            String paramName = StringUtils.hasText(ann.value()) ? ann.value() : parameter.getName();
            p = new Parameter().in("header").name(paramName).required(ann.required());
        }

        // If the parameter is not explicitly annotated with PathVariable, RequestParam,
        // or RequestHeader,
        // it might be a model attribute or other Spring-managed parameter, which we
        // currently ignore.
        if (p != null) {
            p.setSchema(createSchema(parameter.getParameterizedType(), parameter));
            return Optional.of(p);
        }
        return Optional.empty();
    }

    private ApiResponses createApiResponses(Method method) {
        ApiResponses responses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();

        Type returnType = method.getGenericReturnType();
        if (returnType != void.class && returnType != Void.class) {
            Schema<?> schema = createSchema(method.getGenericReturnType(), method.getAnnotatedReturnType());
            apiResponse.setContent(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    new MediaType().schema(schema)));
            if (schema != null) {
                apiResponse.setContent(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE, new MediaType().schema(schema)));
            }
        }

        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
        String statusCode = "200"; // Default for successful GET, etc.
        if (responseStatus != null) {
            statusCode = String.valueOf(responseStatus.value().value());
            apiResponse.description(responseStatus.reason());
        } else {
            // Infer from HTTP method
            Optional<MappingInfo> mappingInfo = findMappingAnnotation(method);
            if (mappingInfo.isPresent() && mappingInfo.get().httpMethod == PathItem.HttpMethod.POST) {
                statusCode = "201";
            }
        }

        if (!StringUtils.hasText(apiResponse.getDescription())) {
            try {
                apiResponse.description(HttpStatus.valueOf(Integer.parseInt(statusCode)).getReasonPhrase());
            } catch (IllegalArgumentException e) {
                apiResponse.description("Successful operation");
            }
        }

        responses.addApiResponse(statusCode, apiResponse);
        addErrorResponses(responses, method);
        return responses;
    }

    private void addErrorResponses(ApiResponses responses, Method controllerMethod) {
        Map<Class<? extends Throwable>, Method> handlers = new HashMap<>(globalExceptionHandlers);

        // Local handlers in the same controller override global ones
        for (Method handlerMethod : controllerMethod.getDeclaringClass().getDeclaredMethods()) {
            if (handlerMethod.isAnnotationPresent(ExceptionHandler.class)) {
                ExceptionHandler handler = handlerMethod.getAnnotation(ExceptionHandler.class);
                for (Class<? extends Throwable> exceptionClass : handler.value()) {
                    handlers.put(exceptionClass, handlerMethod);
                }
            }
        }

        for (Map.Entry<Class<? extends Throwable>, Method> entry : handlers.entrySet()) {
            Method handlerMethod = entry.getValue();
            ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(handlerMethod,
                    ResponseStatus.class);
            if (responseStatus == null) {
                // Try finding it on the exception class itself
                responseStatus = AnnotatedElementUtils.findMergedAnnotation(entry.getKey(), ResponseStatus.class);
            }

            HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
            String statusCode = String.valueOf(status.value());

            if (responses.containsKey(statusCode))
                continue;

            ApiResponse errorResponse = new ApiResponse();
            String description = responseStatus != null && StringUtils.hasText(responseStatus.reason())
                    ? responseStatus.reason()
                    : status.getReasonPhrase();
            errorResponse.description(description);

            Type returnType = handlerMethod.getGenericReturnType();
            if (returnType != void.class && returnType != Void.class) {
                Schema<?> schema = createSchema(handlerMethod.getGenericReturnType(),
                        handlerMethod.getAnnotatedReturnType());
                errorResponse.setContent(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE, new MediaType().schema(schema)));

                if (schema != null) {
                    errorResponse.setContent(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE, new MediaType().schema(schema)));
                }
            }
            responses.addApiResponse(statusCode, errorResponse);
        }
    }

    private Schema<?> createSchema(Type type) {
        return createSchema(type, null);
    }

    @SuppressWarnings("deprecation")
    private Schema<?> createSchema(Type type, AnnotatedElement annotatedElement) {

        // Handle reactive types by unwrapping them.
        if (type instanceof ParameterizedType pType) {
            Class<?> rawType = (Class<?>) pType.getRawType();
            // Unwrap Mono<T> to T and recurse.
            if (Mono.class.isAssignableFrom(rawType)) {
                return createSchema(pType.getActualTypeArguments()[0], annotatedElement);
            }
            // Treat Flux<T> as an array of T.
            if (Flux.class.isAssignableFrom(rawType)) {
                Type itemType = pType.getActualTypeArguments()[0];
                // The inner type might be ResponseEntity, so unwrap that too.
                Schema<?> itemsSchema = createSchema(itemType);
                return new ArraySchema().items(itemsSchema);
            }
        }

        // Handle ResponseEntity<T> by extracting T
        if (type instanceof ParameterizedType pType && pType.getRawType().equals(ResponseEntity.class)) {
            return createSchema(pType.getActualTypeArguments()[0], annotatedElement);
        }

        // Void means no content, so no schema.
        if (type.equals(Void.class) || type.equals(void.class)) {
            return null;
        }

        // Handle collections
        if (isCollection(type)) {
            Type itemType = getCollectionItemType(type);
            ArraySchema arraySchema = new ArraySchema().items(createSchema(itemType));
            applyValidationAnnotations(arraySchema, annotatedElement);
            return arraySchema;
        }

        // Handle Map<K, V> - assuming K is String for JSON objects
        if (isMap(type)) {
            Type valueType = getMapValueType(type);
            Schema<?> mapSchema = new ObjectSchema().additionalProperties(createSchema(valueType));
            applyValidationAnnotations(mapSchema, annotatedElement);
            return mapSchema;
        }

        Class<?> clazz = getClassFromType(type);

        // Handle basic types
        if (String.class.isAssignableFrom(clazz)) {
            StringSchema schema = new StringSchema();
            applyValidationAnnotations(schema, annotatedElement);
            return schema;
        }
        if (clazz.equals(LocalDate.class)) {
            StringSchema schema = (StringSchema) new StringSchema().format("date");
            applyValidationAnnotations(schema, annotatedElement);
            return schema;
        }
        if (clazz.equals(Date.class) || clazz.equals(LocalDateTime.class)) {
            StringSchema schema = (StringSchema) new StringSchema().format("date-time");
            applyValidationAnnotations(schema, annotatedElement);
            return schema;
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            Schema<?> schema;
            if (clazz.equals(Integer.class) || clazz.equals(int.class))
                schema = new IntegerSchema().format("int32");
            else if (clazz.equals(Long.class) || clazz.equals(long.class))
                schema = new IntegerSchema().format("int64");
            else if (clazz.equals(Float.class) || clazz.equals(float.class))
                schema = new NumberSchema().format("float");
            else
                schema = new NumberSchema().format("double"); // Double, BigDecimal
            applyValidationAnnotations(schema, annotatedElement);
            return schema;
        }

        // Handle POJOs
        if (!clazz.isPrimitive() && !clazz.getName().startsWith("java.")) {
            String schemaName = clazz.getSimpleName();
            if (schemas.containsKey(schemaName)) {
                return new Schema<>().$ref("#/components/schemas/" + schemaName);
            }

            ObjectSchema pojoSchema = new ObjectSchema();
            // Add a placeholder to handle circular references
            schemas.put(schemaName, pojoSchema);

            // Reflect on all declared fields (including private)
            for (Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                pojoSchema.addProperties(field.getName(), createSchema(field.getGenericType(), field));
                if (field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class)
                        || field.isAnnotationPresent(NotEmpty.class)) {
                    pojoSchema.addRequiredItem(field.getName());
                }
            }
            return new Schema<>().$ref("#/components/schemas/" + schemaName);
        }

        return new ObjectSchema().description("Unsupported type: " + type.getTypeName());
    }

    private void applyValidationAnnotations(Schema<?> schema, AnnotatedElement element) {
        if (element == null) {
            return;
        }

        if (element.isAnnotationPresent(Size.class)) {
            Size size = element.getAnnotation(Size.class);
            if (schema instanceof StringSchema) {
                schema.minLength(size.min());
                schema.maxLength(size.max());
            } else if (schema instanceof ArraySchema) {
                schema.minItems(size.min());
                schema.maxItems(size.max());
            }
        }
        if (element.isAnnotationPresent(Min.class)) {
            Min min = element.getAnnotation(Min.class);
            schema.minimum(BigDecimal.valueOf(min.value()));
        }
        if (element.isAnnotationPresent(Max.class)) {
            Max max = element.getAnnotation(Max.class);
            schema.maximum(BigDecimal.valueOf(max.value()));
        }
        if (element.isAnnotationPresent(Pattern.class)) {
            schema.pattern(element.getAnnotation(Pattern.class).regexp());
        }
        if (element.isAnnotationPresent(NotEmpty.class)) {
            if (schema instanceof StringSchema)
                schema.minLength(1);
            else if (schema instanceof ArraySchema)
                schema.minItems(1);
        }
        if (element.isAnnotationPresent(NotBlank.class)) {
            if (schema instanceof StringSchema) {
                schema.minLength(1);
                schema.pattern(schema.getPattern() == null ? "\\S" : schema.getPattern()); // Don't overwrite existing
                                                                                           // pattern
            }
        }
    }

    // --- Helper Methods ---

    private record MappingInfo(Annotation annotation, PathItem.HttpMethod httpMethod) {
    }

    private Optional<MappingInfo> findMappingAnnotation(Method method) {
        if (method.isAnnotationPresent(GetMapping.class))
            return Optional.of(new MappingInfo(method.getAnnotation(GetMapping.class), PathItem.HttpMethod.GET));
        if (method.isAnnotationPresent(PostMapping.class))
            return Optional.of(new MappingInfo(method.getAnnotation(PostMapping.class), PathItem.HttpMethod.POST));
        if (method.isAnnotationPresent(PutMapping.class))
            return Optional.of(new MappingInfo(method.getAnnotation(PutMapping.class), PathItem.HttpMethod.PUT));
        if (method.isAnnotationPresent(DeleteMapping.class))
            return Optional.of(new MappingInfo(method.getAnnotation(DeleteMapping.class), PathItem.HttpMethod.DELETE));
        if (method.isAnnotationPresent(PatchMapping.class))
            return Optional.of(new MappingInfo(method.getAnnotation(PatchMapping.class), PathItem.HttpMethod.PATCH));
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping ann = method.getAnnotation(RequestMapping.class);
            if (ann.method().length > 0) {
                // Simplified: takes the first method. A real tool would create an entry for
                // each.
                return Optional.of(new MappingInfo(ann, PathItem.HttpMethod.valueOf(ann.method()[0].name())));
            }
        }
        return Optional.empty();
    }

    private String getPathFromAnnotation(Annotation annotation, String defaultValue) {
        if (annotation == null)
            return defaultValue;
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            String[] paths = (String[]) valueMethod.invoke(annotation);
            return (paths.length > 0) ? paths[0] : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean isCollection(Type type) {
        if (type instanceof Class<?> clazz) {
            return Collection.class.isAssignableFrom(clazz) || clazz.isArray();
        }
        if (type instanceof ParameterizedType pType) {
            return Collection.class.isAssignableFrom((Class<?>) pType.getRawType());
        }
        return false;
    }

    private Type getCollectionItemType(Type type) {
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return clazz.getComponentType();
        }
        if (type instanceof ParameterizedType pType) {
            return pType.getActualTypeArguments()[0];
        }
        return Object.class;
    }

    private boolean isMap(Type type) {
        if (type instanceof Class<?> clazz) {
            return Map.class.isAssignableFrom(clazz);
        }
        if (type instanceof ParameterizedType pType) {
            return Map.class.isAssignableFrom((Class<?>) pType.getRawType());
        }
        return false;
    }

    private Type getMapValueType(Type type) {
        if (type instanceof ParameterizedType pType) {
            // For Map<K, V>, we are interested in V, which is the second type argument.
            if (pType.getActualTypeArguments().length > 1) {
                return pType.getActualTypeArguments()[1];
            }
        }
        return Object.class;
    }

    private Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType pType) {
            return (Class<?>) pType.getRawType();
        }
        throw new IllegalArgumentException("Cannot determine class from type: " + type);
    }
}