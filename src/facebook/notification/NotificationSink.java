package facebook.notification;

/**
 * A narrow dependency: features do not depend on a delivery channel.
 */
@FunctionalInterface
public interface NotificationSink {
    void send(long recipientId, String description);
}
