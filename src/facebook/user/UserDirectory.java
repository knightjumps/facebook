package facebook.user;

import java.util.*;

import static facebook.common.Checks.require;

/**
 * Registration/profile storage. Account authentication is outside this exercise.
 */
public final class UserDirectory {
    private final Map<Long, User> users = new LinkedHashMap<>();
    private long nextId = 1;

    public User register(Profile profile) {
        User user = new User(nextId++, Objects.requireNonNull(profile));
        users.put(user.id(), user);
        return user;
    }

    public User get(long id) {
        User user = users.get(id);
        require(user != null, "User not found: " + id);
        return user;
    }

    public void updateProfile(long actor, Profile profile) {
        get(actor);
        users.put(actor, new User(actor, Objects.requireNonNull(profile)));
    }

    public List<User> all() {
        return List.copyOf(users.values());
    }
}
