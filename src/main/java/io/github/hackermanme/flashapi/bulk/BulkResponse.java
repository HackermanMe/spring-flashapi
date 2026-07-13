package io.github.hackermanme.flashapi.bulk;

import java.util.List;

public record BulkResponse(
        int success,
        int failed,
        List<BulkResult> results
) {
    public static BulkResponse from(List<BulkResult> results) {
        int success = (int) results.stream().filter(r -> r.error() == null).count();
        return new BulkResponse(success, results.size() - success, results);
    }
}
