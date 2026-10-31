package facebook.messaging;

import facebook.notification.NotificationSink;
import facebook.social.RelationshipService;
import facebook.user.UserDirectory;

import java.time.*;
import java.util.*;

import static facebook.common.Checks.*;

/**
 * Direct messaging only: group conversations and read receipts are extensions.
 */
public final class MessagingService {
    private record ConversationKey(long lower, long higher) {
        static ConversationKey of(long a, long b) {
            return new ConversationKey(Math.min(a, b), Math.max(a, b));
        }
    }

    private final UserDirectory users;
    private final RelationshipService relationships;
    private final NotificationSink notifications;
    private final Clock clock;
    private final Map<ConversationKey, List<Message>> conversations = new HashMap<>();
    private long nextId = 1;

    public MessagingService(UserDirectory users, RelationshipService relationships,
                            NotificationSink notifications, Clock clock) {
        this.users = users;
        this.relationships = relationships;
        this.notifications = notifications;
        this.clock = clock;
    }

    public Message send(long actor, long recipient, String body) {
        users.get(actor);
        users.get(recipient);
        require(actor != recipient && !relationships.blocked(actor, recipient), "Cannot send message");
        Message message = new Message(nextId++, actor, recipient, text(body), clock.instant());
        conversations.computeIfAbsent(ConversationKey.of(actor, recipient), key -> new ArrayList<>()).add(message);
        notifications.send(recipient, "New message #" + message.id());
        return message;
    }

    public List<Message> conversation(long actor, long other) {
        users.get(actor);
        users.get(other);
        // Existing conversation history remains readable after blocking; new sends are denied.
        return List.copyOf(conversations.getOrDefault(ConversationKey.of(actor, other), List.of()));
    }
}
