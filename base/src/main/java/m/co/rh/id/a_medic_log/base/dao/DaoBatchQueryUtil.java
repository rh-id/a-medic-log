package m.co.rh.id.a_medic_log.base.dao;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class for batching queries to avoid SQLite parameter binding limits
 */
final class DaoBatchQueryUtil {

    private DaoBatchQueryUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Executes an IN-clause query in chunks below SQLite's 999 bind-variable limit (older Android versions).
     * Returns all rows matching the given items; safe for null, empty, or duplicate inputs (items are deduplicated).
     *
     * @param items  List of items (e.g. ids) to query
     * @param query  Function that executes the query for a batch of items
     * @param <I>    Input item type of the query
     * @param <T>    Return type of the query
     * @return       Combined results from all batches, or empty list for null/empty input
     */
    static <I, T> List<T> queryInBatches(List<I> items, Function<List<I>, List<T>> query) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        // Deduplicate while preserving first-occurrence order
        List<I> deduplicatedIds = new ArrayList<>(new LinkedHashSet<>(items));
        int size = deduplicatedIds.size();
        int maxQuerySize = 500;
        if (size <= maxQuerySize) {
            return query.apply(deduplicatedIds);
        }
        List<T> result = new ArrayList<>();
        for (int i = 0, i2 = maxQuerySize;
             size > 0;
             size -= maxQuerySize,
                     i = i2,
                     i2 += Math.min(size, maxQuerySize)) {
            result.addAll(query.apply(deduplicatedIds.subList(i, i2)));
        }
        return result;
    }
}
