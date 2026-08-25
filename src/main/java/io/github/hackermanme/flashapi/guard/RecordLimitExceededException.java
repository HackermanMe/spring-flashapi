package io.github.hackermanme.flashapi.guard;

/**
 * Thrown when a CREATE operation is rejected because the record count limit has been reached.
 */
public class RecordLimitExceededException extends RuntimeException {

    private final String entityName;
    private final long limit;

    public RecordLimitExceededException(String entityName, long limit) {
        super("Record limit exceeded for " + entityName + ": max " + limit + " allowed");
        this.entityName = entityName;
        this.limit = limit;
    }

    public String getEntityName() {
        return entityName;
    }

    public long getLimit() {
        return limit;
    }
}
