package facebook.content;

import java.time.Instant;

public record Comment(long id, long postId, Long parentId, long authorId, String text, Instant createdAt) {
}
