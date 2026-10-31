package facebook.social;

import static facebook.common.Checks.require;

/**
 * Returning a new value prevents callers from mutating service-owned state.
 */
public record FriendRequest(long id, long senderId, long recipientId, RequestStatus status) {
    public FriendRequest respond(long actor, RequestStatus next) {
        require(status == RequestStatus.PENDING, "Request is already resolved");
        require(next != null && next != RequestStatus.PENDING, "Invalid transition");
        long expectedActor = next == RequestStatus.CANCELED ? senderId : recipientId;
        require(actor == expectedActor, "Only the intended user can respond");
        return new FriendRequest(id, senderId, recipientId, next);
    }
}
