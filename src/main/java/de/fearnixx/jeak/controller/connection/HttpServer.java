package de.fearnixx.jeak.controller.connection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fearnixx.jeak.controller.controller.ControllerContainer;
import de.fearnixx.jeak.controller.controller.ControllerMethod;
import de.fearnixx.jeak.controller.controller.MethodParameter;
import de.fearnixx.jeak.controller.reflect.RequestBody;
import de.fearnixx.jeak.controller.reflect.RequestMapping;
import de.fearnixx.jeak.controller.reflect.RequestParam;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Route;
import spark.Spark;

import static spark.Spark.port;

/**
 * A wrapper for the http server.
 */
public class HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private static final String API_ENDPOINT = "/api";
    private int port = 8723;

    public HttpServer() {
        init();
    }

    public HttpServer(int port) {
        this.port = port;
        init();
    }

    public void init() {
        port(port);
    }

    /**
     * A wrapper for the {@code registerMethod()} method utilizing Sparks ability to build hierarchical endpoints.
     *
     * @param controllerContainer
     */
    public void registerController(ControllerContainer controllerContainer) {
        controllerContainer.getControllerMethodList().forEach(controllerMethod -> {
            registerMethod(controllerMethod.getRequestMethod(),
                    buildEndpoint(controllerContainer, controllerMethod),
                    generateRoute(controllerContainer, controllerMethod));
        });
    }

    private String buildEndpoint(ControllerContainer controllerContainer, ControllerMethod controllerMethod) {
        char DELIMITER = '/';
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(API_ENDPOINT);
        String controllerEndpoint = controllerContainer.getControllerEndpoint();
        if (controllerEndpoint.charAt(0) != DELIMITER) {
            stringBuilder.append(DELIMITER);
        }
        stringBuilder.append(controllerEndpoint);
        String methodEndpoint = controllerMethod.getPath();
        if (methodEndpoint.charAt(0) != DELIMITER) {
            stringBuilder.append(DELIMITER);
        }
        stringBuilder.append(methodEndpoint);
        return stringBuilder.toString().replaceAll("//", "/");
    }

    /**
     * Registers a method to Spark.
     *
     * @param httpMethod The {@link RequestMethod} to map the method to.
     * @param path       The api path as {@link String}for the given method.
     * @param route      The {@link Route} which is supposed to be invoked when a call to the specified
     *                   {@code path} and {@code httpMethod} is made.
     */
    public void registerMethod(RequestMethod httpMethod, String path, Route route) {
        switch (httpMethod) {
            case GET:
                Spark.get(path, route);
                break;
            case PUT:
                Spark.put(path, route);
                break;
            case POST:
                Spark.post(path, route);
                break;
            case PATCH:
                Spark.patch(path, route);
                break;
            case DELETE:
                Spark.delete(path, route);
                break;
            case HEAD:
                Spark.head(path, route);
                break;
        }
    }

    /**
     * Generate a Spark specific {@link Route} for the provided controller and method.
     *
     * @param controllerContainer The {@link ControllerContainer} of the controller.
     * @param controllerMethod    The {@link ControllerMethod} of the method.
     * @return A {@link Route} containing the actions of the {@link ControllerMethod}.
     */
    private Route generateRoute(ControllerContainer controllerContainer, ControllerMethod controllerMethod) {
        List<MethodParameter> methodParameterList = controllerMethod.getMethodParameters();
        return (request, response) -> {
            Object[] methodParameters = new Object[methodParameterList.size()];
            for (MethodParameter methodParameter : methodParameterList) {
                Object retrievedParameter = null;
                if (methodParameter.hasAnnotation(RequestParam.class)) {
                    retrievedParameter = transformRequestOption(request.params(getRequestParamName(methodParameter)), request, methodParameter);
                } else if (methodParameter.hasAnnotation(RequestBody.class)) {
                    retrievedParameter = transformRequestOption(request.body(), request, methodParameter);
                }
                methodParameters[methodParameter.getPosition()] = retrievedParameter;
            }
            Object returnValue = controllerContainer.invoke(controllerMethod, methodParameters);
            String contentType = controllerMethod.getAnnotation(RequestMapping.class).contentType();
            if (contentType.contains("json")) {
                response.type(contentType);
                returnValue = toJson(returnValue);
            }
            return returnValue;
        };
    }

    private Object transformRequestOption(String string, Request request, MethodParameter methodParameter) {
        Object retrievedParameter;
        if (request.contentType().equals("application/json")) {
            retrievedParameter = fromJson(string, methodParameter.getType());
        } else {
            retrievedParameter = string;
        }
        return retrievedParameter;
    }

    /**
     * Retrieve the name from a {@link RequestParam} annotated value.
     *
     * @param methodParameter
     * @return The name of the annotated variable.
     */
    private String getRequestParamName(MethodParameter methodParameter) {
        Function<Annotation, Object> function = annotation -> ((RequestParam) annotation).name();
        return (String) methodParameter.callAnnotationFunction(function, RequestParam.class).get();
    }

    /**
     * Generate a json representation of the provided object.
     *
     * @param o
     * @return A {@link String} with the object as json if o =! null,
     * an empty {@link String} otherwise.
     * @throws JsonProcessingException
     */
    private String toJson(Object o) throws JsonProcessingException {
        if (o == null) {
            return "";
        }
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(o);
        return json;
    }

    private Object fromJson(String json, Class<?> clazz) {
        ObjectMapper objectMapper = new ObjectMapper();
        Object deserializedObject = null;
        try {
            deserializedObject = objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            logger.error("There was an error while trying to deserialize json",e);
        }
        return deserializedObject;
    }

    interface requestTranslator {

    }

}