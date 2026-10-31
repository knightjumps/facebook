package facebook.social;

import facebook.notification.NotificationSink;
import facebook.user.UserDirectory;

import java.util.*;

import static facebook.common.Checks.require;

/**
 * One undirected friendship is represented by two adjacency-set entries.
 */
public final class RelationshipService {
    private record Pair(long first, long second) {
        static Pair unordered(long a, long b) {
            return new Pair(Math.min(a, b), Math.max(a, b));
        }
    }

    private final UserDirectory users;
    private final NotificationSink notifications;
    private final Map<Long, Set<Long>> friends = new HashMap<>();
    private final Map<Long, Set<Long>> following = new HashMap<>();
    private final Set<Pair> blocks = new HashSet<>();
    private final Map<Long, FriendRequest> requests = new HashMap<>();
    private final Map<Pair, Long> pending = new HashMap<>();
    private long nextId = 1;

    public RelationshipService(UserDirectory users, NotificationSink notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    private void validatePair(long a, long b) {
        users.get(a);
        users.get(b);
        require(a != b, "Cannot connect to yourself");
    }

    public boolean blocked(long a, long b) {
        return blocks.contains(new Pair(a, b)) || blocks.contains(new Pair(b, a));
    }

    public boolean areFriends(long a, long b) {
        return friends.getOrDefault(a, Set.of()).contains(b);
    }

    public boolean follows(long a, long b) {
        return following.getOrDefault(a, Set.of()).contains(b);
    }

    public Set<Long> friendsOf(long user) {
        return Set.copyOf(friends.getOrDefault(user, Set.of()));
    }

    public Set<Long> followsOf(long user) {
        return Set.copyOf(following.getOrDefault(user, Set.of()));
    }

    public FriendRequest request(long actor, long recipient) {
        validatePair(actor, recipient);
        require(!blocked(actor, recipient), "Connection is blocked");
        require(!areFriends(actor, recipient), "Already friends");
        Pair pair = Pair.unordered(actor, recipient);
        Long existing = pending.get(pair);
        if (existing != null) {
            FriendRequest request = requests.get(existing);
            require(request.senderId() == actor, "An incoming request already exists; respond to it");
            return request; // Retried send does not create another invitation/notification.
        }
        FriendRequest request = new FriendRequest(nextId++, actor, recipient, RequestStatus.PENDING);
        requests.put(request.id(), request);
        pending.put(pair, request.id());
        notifications.send(recipient, "Friend request #" + request.id());
        return request;
    }

    public FriendRequest respond(long actor, long requestId, RequestStatus outcome) {
        FriendRequest request = requests.get(requestId);
        require(request != null, "Request not found");
        require(!blocked(request.senderId(), request.recipientId()), "Connection is blocked");
        FriendRequest resolved = request.respond(actor, outcome);
        requests.put(requestId, resolved);
        pending.remove(Pair.unordered(request.senderId(), request.recipientId()));
        if (outcome == RequestStatus.ACCEPTED) {
            friends.computeIfAbsent(request.senderId(), id -> new HashSet<>()).add(request.recipientId());
            friends.computeIfAbsent(request.recipientId(), id -> new HashSet<>()).add(request.senderId());
            notifications.send(request.senderId(), "Friend request accepted #" + requestId);
        }
        return resolved;
    }

    public void follow(long actor, long target) {
        validatePair(actor, target);
        require(!blocked(actor, target), "Connection is blocked");
        following.computeIfAbsent(actor, id -> new HashSet<>()).add(target);
    }

    public void unfollow(long actor, long target) {
        validatePair(actor, target);
        following.getOrDefault(actor, new HashSet<>()).remove(target);
    }

    public void unfriend(long actor, long target) {
        validatePair(actor, target);
        friends.getOrDefault(actor, new HashSet<>()).remove(target);
        friends.getOrDefault(target, new HashSet<>()).remove(actor);
    }

    public void block(long actor, long target) {
        validatePair(actor, target);
        blocks.add(new Pair(actor, target));
        unfriend(actor, target);
        unfollow(actor, target);
        unfollow(target, actor);
        Long requestId = pending.remove(Pair.unordered(actor, target));
        if (requestId != null) {
            FriendRequest request = requests.get(requestId);
            requests.put(requestId, request.respond(request.senderId(), RequestStatus.CANCELED));
        }
    }

    public void unblock(long actor, long target) {
        validatePair(actor, target);
        blocks.remove(new Pair(actor, target)); // Does not restore friendship/follows.
    }
}
