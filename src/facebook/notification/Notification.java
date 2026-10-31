package facebook.notification;

import java.time.Instant;

/**
 * Descriptions deliberately contain no private post or message bodies.
 */
public record Notification(long id, long recipientId, String description, Instant createdAt) {
}
