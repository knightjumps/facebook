package facebook.content;

import facebook.community.CommunityService;
import facebook.social.RelationshipService;

/**
 * Shared visibility rule; PostService also checks a share's original post.
 */
public final class PostAccessPolicy {
    private final RelationshipService relationships;
    private final CommunityService communities;

    public PostAccessPolicy(RelationshipService relationships, CommunityService communities) {
        this.relationships = relationships;
        this.communities = communities;
    }

    public boolean canView(long viewer, Post post) {
        if (post.deleted() || relationships.blocked(viewer, post.authorId())) return false;
        if (post.communityId() != null && !communities.canRead(viewer, post.communityId())) return false;
        if (viewer == post.authorId()) return true;
        return switch (post.audience()) {
            case PUBLIC -> true;
            case FRIENDS -> relationships.areFriends(viewer, post.authorId());
            case ONLY_ME -> false;
        };
    }
}
