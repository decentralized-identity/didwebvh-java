package io.github.decentralizedidentity.didwebvh.wizard;

/** Thrown when the wizard cannot continue (invalid input, missing files, aborted flow). */
public class WizardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WizardException(String message) {
        super(message);
    }

    public WizardException(String message, Throwable cause) {
        super(message, cause);
    }
}
