package facebook;

import facebook.app.Facebook;
import facebook.community.*;
import facebook.content.*;
import facebook.social.*;
import facebook.user.*;
import java.time.*;
import java.util.*;

/** Boundary and invariant checks beyond the walkthrough; no test dependencies needed. */
public final class RulesTest {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Check " + (checks + 1));
        checks++;
    }
    private static void denied(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid action");
    }
    public static void main(String[] args) {
        Facebook fb = new Facebook(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        List<String> education = new ArrayList<>(List.of("CS"));
        long a = fb.users.register(new Profile("A", "", education, List.of())).id();
        long b = fb.users.register(new Profile("B", "", List.of(), List.of())).id();
        long c = fb.users.register(new Profile("C", "", List.of(), List.of())).id();
        education.add("Should not leak");
        check(fb.profiles.read(a, a).education().size() == 1);
        denied(() -> fb.relationships.request(a, a));
        denied(() -> fb.relationships.request(a, 999));
        FriendRequest request = fb.relationships.request(a, b);
        int notifications = fb.notifications.inbox(b).size();
        fb.relationships.request(a, b);
        check(fb.notifications.inbox(b).size() == notifications);
        denied(() -> fb.relationships.request(b, a));
        denied(() -> fb.relationships.respond(a, request.id(), RequestStatus.ACCEPTED));
        fb.relationships.respond(b, request.id(), RequestStatus.ACCEPTED);
        denied(() -> fb.relationships.respond(b, request.id(), RequestStatus.REJECTED));
        fb.relationships.follow(b, a);
        Post first = fb.posts.publish(a, "first", Audience.PUBLIC, null);
        Post second = fb.posts.publish(a, "second", Audience.PUBLIC, null);
        check(fb.posts.feed(b, 10).equals(List.of(second, first))); // Dedup + stable timestamp tie-break.
        denied(() -> fb.posts.feed(b, 0));
        denied(() -> fb.posts.publish(a, "  ", Audience.PUBLIC, null));
        Comment comment = fb.posts.comment(b, first.id(), null, "hello");
        denied(() -> fb.posts.comment(c, second.id(), comment.id(), "wrong post"));
        denied(() -> fb.posts.likeComment(c, 999));
        denied(() -> fb.posts.setAudience(b, first.id(), Audience.ONLY_ME));
        Post share = fb.posts.share(b, first.id(), "", Audience.PUBLIC);
        Post shareAgain = fb.posts.share(c, share.id(), "", Audience.PUBLIC);
        check(shareAgain.originalPostId().equals(first.id())); // Shares are flattened.
        fb.posts.setAudience(a, first.id(), Audience.ONLY_ME);
        denied(() -> fb.posts.read(c, shareAgain.id()));
        fb.relationships.block(c, a);
        fb.relationships.block(a, c);
        fb.relationships.unblock(c, a);
        check(fb.relationships.blocked(a, c)); // Only the other user's block remains.
        denied(() -> fb.relationships.follow(c, a));
        fb.relationships.unblock(a, c);
        FriendRequest pending = fb.relationships.request(a, c);
        fb.relationships.block(c, a);
        fb.relationships.unblock(c, a);
        denied(() -> fb.relationships.respond(c, pending.id(), RequestStatus.ACCEPTED));
        Community group = fb.communities.create(a, "G", CommunityType.GROUP, true);
        fb.communities.join(b, group.id());
        denied(() -> fb.posts.publish(b, "Pending member", Audience.PUBLIC, group.id()));
        fb.communities.approve(a, group.id(), b, true);
        fb.communities.ban(a, group.id(), b);
        denied(() -> fb.communities.leave(b, group.id()));
        denied(() -> fb.communities.leave(a, group.id()));
        denied(() -> fb.communities.delete(c, group.id()));
        denied(() -> fb.communities.create(a, "Page", CommunityType.PAGE, true));
        Community page = fb.communities.create(a, "P", CommunityType.PAGE, false);
        denied(() -> fb.communities.setPrivacy(a, page.id(), true));
        fb.messages.send(a, b, "one");
        check(fb.messages.conversation(c, b).isEmpty());
        denied(() -> fb.messages.send(a, a, "self"));
        try { fb.notifications.inbox(b).clear(); throw new AssertionError("Mutable inbox"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { fb.relationships.friendsOf(a).clear(); throw new AssertionError("Mutable friends"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("RulesTest: " + checks + " checks passed.");
    }
}
