package io.github.hackermanme.flashapi.bulk;

public class BulkLimitExceededException extends RuntimeException {
    public BulkLimitExceededException(String message) {
        super(message);
    }
}
