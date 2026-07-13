package io.github.hackermanme.flashapi.bulk;

import java.util.Map;

public record BulkResult(
        int index,
        String status,
        Map<String, Object> data,
        String error
) {
    public static BulkResult success(int index, String status, Map<String, Object> data) {
        return new BulkResult(index, status, data, null);
    }

    public static BulkResult failure(int index, String error) {
        return new BulkResult(index, "error", null, error);
    }
}
