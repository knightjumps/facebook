package facebook.user;

import facebook.social.RelationshipService;

import static facebook.common.Checks.require;

public final class ProfileService {
    private final UserDirectory users;
    private final RelationshipService relationships;

    public ProfileService(UserDirectory users, RelationshipService relationships) {
        this.users = users;
        this.relationships = relationships;
    }

    public Profile read(long actor, long target) {
        users.get(actor);
        Profile profile = users.get(target).profile();
        boolean visible = actor == target || (!relationships.blocked(actor, target) &&
                (profile.visibility() == ProfileVisibility.PUBLIC ||
                        profile.visibility() == ProfileVisibility.FRIENDS && relationships.areFriends(actor, target)));
        require(visible, "Profile unavailable");
        return profile;
    }
}
