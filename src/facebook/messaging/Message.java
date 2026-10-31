package facebook.messaging;

import java.time.Instant;

public record Message(long id, long senderId, long recipientId, String text, Instant createdAt) {
}
