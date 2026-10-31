package facebook.notification;

import java.time.*;
import java.util.*;

public final class NotificationService implements NotificationSink {
    private final Map<Long, List<Notification>> inboxes = new HashMap<>();
    private final Clock clock;
    private long nextId = 1;

    public NotificationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void send(long recipientId, String description) {
        inboxes.computeIfAbsent(recipientId, id -> new ArrayList<>())
                .add(new Notification(nextId++, recipientId, description, clock.instant()));
    }

    public List<Notification> inbox(long actor) {
        return List.copyOf(inboxes.getOrDefault(actor, List.of()));
    }
}
