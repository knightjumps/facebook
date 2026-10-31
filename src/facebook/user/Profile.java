package facebook.user;

import java.util.*;

import static facebook.common.Checks.text;

/**
 * Name remains discoverable; visibility controls the rest of the profile.
 */
public record Profile(String name, String bio, List<String> education, List<String> work,
                      ProfileVisibility visibility) {
    public Profile(String name, String bio, List<String> education, List<String> work) {
        this(name, bio, education, work, ProfileVisibility.PUBLIC);
    }

    public Profile {
        name = text(name);
        bio = bio == null ? "" : bio;
        education = List.copyOf(education);
        work = List.copyOf(work);
        Objects.requireNonNull(visibility);
    }
}
