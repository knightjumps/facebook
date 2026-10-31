package facebook.community;

import facebook.notification.NotificationSink;
import facebook.user.UserDirectory;

import java.util.*;

import static facebook.common.Checks.*;

/**
 * Pages and groups share storage, not identical permission rules.
 */
public final class CommunityService {
    private final UserDirectory users;
    private final NotificationSink notifications;
    private final Map<Long, Community> communities = new LinkedHashMap<>();
    private final Map<Long, Map<Long, MembershipStatus>> members = new HashMap<>();
    private long nextId = 1;

    public CommunityService(UserDirectory users, NotificationSink notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    public Community create(long actor, String name, CommunityType type, boolean privateGroup) {
        users.get(actor);
        Objects.requireNonNull(type);
        require(type == CommunityType.GROUP || !privateGroup, "Pages must be public");
        Community community = new Community(nextId++, text(name), actor, type, privateGroup);
        communities.put(community.id(), community);
        members.put(community.id(), new HashMap<>(Map.of(actor, MembershipStatus.ACTIVE)));
        return community;
    }

    public Community get(long id) {
        Community community = communities.get(id);
        require(community != null, "Community not found");
        return community;
    }

    public MembershipStatus status(long communityId, long user) {
        get(communityId);
        return members.get(communityId).get(user);
    }

    public MembershipStatus join(long actor, long communityId) {
        users.get(actor);
        Community community = get(communityId);
        MembershipStatus old = status(communityId, actor);
        require(old != MembershipStatus.BANNED, "Banned from community");
        if (old == MembershipStatus.ACTIVE || old == MembershipStatus.PENDING) return old;
        MembershipStatus next = community.privateGroup() ? MembershipStatus.PENDING : MembershipStatus.ACTIVE;
        members.get(communityId).put(actor, next);
        if (next == MembershipStatus.PENDING)
            notifications.send(community.ownerId(), "Membership request for community #" + communityId);
        return next;
    }

    public void approve(long actor, long communityId, long applicant, boolean accept) {
        requireOwner(actor, communityId);
        require(status(communityId, applicant) == MembershipStatus.PENDING, "No pending membership");
        members.get(communityId).put(applicant, accept ? MembershipStatus.ACTIVE : MembershipStatus.REJECTED);
        notifications.send(applicant, "Membership decision for community #" + communityId);
    }

    public void leave(long actor, long communityId) {
        users.get(actor);
        require(get(communityId).ownerId() != actor, "Owner must retain membership");
        require(status(communityId, actor) != MembershipStatus.BANNED, "A ban cannot be removed by leaving");
        members.get(communityId).remove(actor);
    }

    public void ban(long actor, long communityId, long target) {
        requireOwner(actor, communityId);
        users.get(target);
        require(actor != target, "Cannot ban the owner");
        members.get(communityId).put(target, MembershipStatus.BANNED);
    }

    public void unban(long actor, long communityId, long target) {
        requireOwner(actor, communityId);
        require(status(communityId, target) == MembershipStatus.BANNED, "User is not banned");
        members.get(communityId).remove(target);
    }

    public void setPrivacy(long actor, long communityId, boolean privateGroup) {
        requireOwner(actor, communityId);
        Community old = get(communityId);
        require(old.type() == CommunityType.GROUP || !privateGroup, "Pages must be public");
        communities.put(communityId, new Community(old.id(), old.name(), old.ownerId(), old.type(), privateGroup));
    }

    public void delete(long actor, long communityId) {
        requireOwner(actor, communityId);
        communities.remove(communityId);
        members.remove(communityId);
    }

    private void requireOwner(long actor, long id) {
        require(get(id).ownerId() == actor, "Only the community owner may manage it");
    }

    public boolean canDiscover(long viewer, long id) {
        Community c = communities.get(id);
        return c != null && status(id, viewer) != MembershipStatus.BANNED;
    }

    public boolean canRead(long viewer, long id) {
        if (!canDiscover(viewer, id)) return false;
        return !get(id).privateGroup() || status(id, viewer) == MembershipStatus.ACTIVE;
    }

    public boolean canPublish(long actor, long id) {
        Community c = get(id);
        return c.type() == CommunityType.PAGE ? c.ownerId() == actor
                : status(id, actor) == MembershipStatus.ACTIVE;
    }

    public Set<Long> subscriptions(long actor) {
        Set<Long> result = new HashSet<>();
        members.forEach((id, memberships) -> {
            if (memberships.get(actor) == MembershipStatus.ACTIVE) result.add(id);
        });
        return Set.copyOf(result);
    }

    public List<Community> all() {
        return List.copyOf(communities.values());
    }
}
