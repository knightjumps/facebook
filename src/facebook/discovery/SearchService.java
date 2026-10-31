package facebook.discovery;

import facebook.community.*;
import facebook.content.*;
import facebook.social.RelationshipService;
import facebook.user.*;

import java.util.*;

import static facebook.common.Checks.text;

/**
 * Linear substring search is intentional for interview-sized in-memory data.
 */
public final class SearchService {
    private final UserDirectory users;
    private final RelationshipService relationships;
    private final CommunityService communities;
    private final PostService posts;

    public SearchService(UserDirectory users, RelationshipService relationships,
                         CommunityService communities, PostService posts) {
        this.users = users;
        this.relationships = relationships;
        this.communities = communities;
        this.posts = posts;
    }

    private String query(String query) {
        return text(query).toLowerCase(Locale.ROOT);
    }

    public List<UserSummary> users(long actor, String query) {
        users.get(actor);
        String q = query(query);
        return users.all().stream().filter(u -> !relationships.blocked(actor, u.id()))
                .filter(u -> u.profile().name().toLowerCase(Locale.ROOT).contains(q))
                .map(u -> new UserSummary(u.id(), u.profile().name())).toList();
    }

    public List<Community> communities(long actor, String query) {
        users.get(actor);
        String q = query(query);
        return communities.all().stream().filter(c -> communities.canDiscover(actor, c.id()))
                .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    public List<Post> posts(long actor, String query) {
        String q = query(query);
        return posts.visiblePosts(actor).stream().filter(p -> p.text().toLowerCase(Locale.ROOT).contains(q)).toList();
    }
}
