package com.techcourse.db;

import com.techcourse.model.User;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository {
    private static final Map<String, User> database = new ConcurrentHashMap<>();

    public static void save(String sessionId, User user) {
        database.put(sessionId, user);
    }

    public static Optional<User> findBySessionId(String sessionId) {
        return Optional.ofNullable(database.get(sessionId));
    }

    public static void remove(String sessionId) {
        database.remove(sessionId);
    }

    private InMemorySessionRepository() {}
}
