package dev.modularforge.shared.persistence.mongo;

import org.bson.Document;
import java.util.List;
import java.util.regex.Pattern;

/** Only repository-owned field names enter this builder; values stay BSON parameters. */
public final class MongoFilters {
    private MongoFilters() { }

    public static Document filter(Object... pairs) {
        Document result = new Document();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    public static Document users(String search, Boolean active, Boolean verified, Object type) {
        Document result = filter("isActive", active, "emailVerified", verified, "userType", type);
        if (search != null && !search.isBlank()) {
            Pattern text = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            result.put("$or", List.of(new Document("username", text), new Document("email", text)));
        }
        return result;
    }

    public static Document activity(Long userId, String role, String action, String resourceType, Boolean success,
                                    Object start, Object end, String ip) {
        Document result = filter("userId", userId, "role", role, "action", action,
                "resourceType", resourceType, "success", success, "ipAddress", ip);
        Document dates = filter("$gte", start, "$lte", end);
        if (!dates.isEmpty()) result.put("createdAt", dates);
        return result;
    }
}
