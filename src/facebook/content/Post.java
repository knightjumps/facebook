package facebook.content;

import java.time.Instant;

/**
 * Nullable communityId/originalPostId distinguish personal posts and shares.
 */
public record Post(long id, long authorId, String text, Audience audience,
                   Long communityId, Long originalPostId, Instant createdAt, boolean deleted) {
    public Post withAudience(Audience next) {
        return new Post(id, authorId, text, next, communityId, originalPostId, createdAt, deleted);
    }

    public Post tombstone() {
        return new Post(id, authorId, "", audience, communityId, originalPostId, createdAt, true);
    }
}
