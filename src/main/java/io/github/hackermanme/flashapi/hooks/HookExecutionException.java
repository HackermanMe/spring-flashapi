package io.github.hackermanme.flashapi.hooks;

/**
 * Thrown when a lifecycle hook fails to execute.
 */
public class HookExecutionException extends RuntimeException {
    public HookExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
