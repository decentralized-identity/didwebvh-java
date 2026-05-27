package io.github.decentralizedidentity.didwebvh.core;

/** Thrown when signing or proof generation fails. */
public class SigningException extends DidWebVhException {

    private static final long serialVersionUID = 1L;

    public SigningException(String message) {
        super(message);
    }

    public SigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
