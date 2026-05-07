package io.github.decentralizedidentity.didwebvh.core;

import com.google.gson.JsonObject;

/** Thrown when DID resolution fails. */
public class ResolutionException extends DidWebVhException {

    private static final long serialVersionUID = 1L;
    private static final String PROBLEM_TYPE_PREFIX = "urn:didwebvh:error:";

    private final String error;
    private final JsonObject problemDetails;

    public ResolutionException(String message) {
        super(message);
        this.error = null;
        this.problemDetails = null;
    }

    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
        this.problemDetails = null;
    }

    public ResolutionException(String message, String error) {
        super(message);
        this.error = error;
        this.problemDetails = problemDetails(error, message);
    }

    public ResolutionException(String message, String error, Throwable cause) {
        super(message, cause);
        this.error = error;
        this.problemDetails = problemDetails(error, message);
    }

    public String getError() {
        return error;
    }

    public JsonObject getProblemDetails() {
        return problemDetails == null ? null : problemDetails.deepCopy();
    }

    private static JsonObject problemDetails(String error, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", PROBLEM_TYPE_PREFIX + safeProblemType(error));
        json.addProperty("title", titleFor(error));
        json.addProperty("detail", message);
        return json;
    }

    private static String safeProblemType(String error) {
        if (error == null || error.isEmpty()) {
            return "unknown";
        }
        return error.replaceAll("[^A-Za-z0-9._~-]", "-");
    }

    private static String titleFor(String error) {
        if ("invalidDid".equals(error)) {
            return "Invalid DID";
        }
        if ("notFound".equals(error)) {
            return "Not Found";
        }
        if ("httpError".equals(error)) {
            return "HTTP Error";
        }
        return error == null || error.isEmpty() ? "Resolution Error" : error;
    }
}
