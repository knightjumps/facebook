package facebook.app;

import facebook.community.*;
import facebook.content.*;
import facebook.social.*;
import facebook.user.*;

import java.time.*;
import java.util.List;

/**
 * Run with assertions enabled or disabled: checks are always executed.
 */
public final class Driver {
    private static int checks;

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
        System.out.println("  PASS: " + label);
    }

    private static void denied(Runnable operation, String label) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            check(true, label);
            return;
        }
        throw new AssertionError("Expected denial: " + label);
    }

    private static void section(String name) {
        System.out.println("\n" + name);
    }

    public static void main(String[] args) {
        Facebook fb = new Facebook(Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
        long alice = fb.users.register(new Profile("Alice", "Java backend engineer", List.of("CS"), List.of("Acme"))).id();
        long bob = fb.users.register(new Profile("Bob", "Enjoys distributed systems", List.of(), List.of())).id();
        long carol = fb.users.register(new Profile("Carol", "Photographer", List.of(), List.of())).id();
        long dave = fb.users.register(new Profile("Dave", "Reader", List.of(), List.of())).id();

        section("1. Profiles and discovery");
        fb.users.updateProfile(alice, new Profile("Alice", "SDE-3 candidate", List.of("CS"), List.of("Acme"), ProfileVisibility.FRIENDS));
        check(fb.search.users(bob, "ali").get(0).name().equals("Alice"), "Search returns public identity only");
        denied(() -> fb.profiles.read(bob, alice), "Private profile details denied to nonfriend");

        section("2. Friend-request lifecycle and independent following");
        FriendRequest request = fb.relationships.request(alice, bob);
        check(fb.relationships.request(alice, bob).id() == request.id(), "Repeated send returns pending invitation");
        denied(() -> fb.relationships.respond(carol, request.id(), RequestStatus.ACCEPTED), "Unrelated user cannot accept");
        fb.relationships.respond(bob, request.id(), RequestStatus.ACCEPTED);
        check(fb.relationships.areFriends(alice, bob) && fb.relationships.areFriends(bob, alice), "Friendship is symmetric");
        check(fb.profiles.read(bob, alice).bio().equals("SDE-3 candidate"), "Friend can read profile details");
        fb.relationships.follow(carol, alice);
        check(fb.relationships.follows(carol, alice) && !fb.relationships.areFriends(carol, alice), "Following does not create friendship");
        FriendRequest rejected = fb.relationships.request(dave, bob);
        fb.relationships.respond(bob, rejected.id(), RequestStatus.REJECTED);
        FriendRequest canceled = fb.relationships.request(dave, carol);
        fb.relationships.respond(dave, canceled.id(), RequestStatus.CANCELED);
        denied(() -> fb.relationships.respond(carol, canceled.id(), RequestStatus.ACCEPTED), "Canceled request cannot be accepted");

        section("3. Posts, privacy, comments, reactions and sharing");
        Post publicPost = fb.posts.publish(alice, "Java design patterns", Audience.PUBLIC, null);
        Post privatePost = fb.posts.publish(alice, "Friends-only interview notes", Audience.FRIENDS, null);
        Post secret = fb.posts.publish(alice, "Private diary", Audience.ONLY_ME, null);
        check(fb.posts.feed(carol, 20).size() == 1, "Follower feed includes only permitted posts");
        check(fb.posts.feed(bob, 20).size() == 2, "Friend feed includes friends-only content");
        denied(() -> fb.posts.read(bob, secret.id()), "Only-me post is private");
        denied(() -> fb.posts.comment(carol, privatePost.id(), null, "Leak?"), "Cannot comment on invisible post");
        Comment comment = fb.posts.comment(bob, publicPost.id(), null, "Useful!");
        fb.posts.comment(alice, publicPost.id(), comment.id(), "Thanks");
        check(fb.posts.comments(carol, publicPost.id()).size() == 2, "Comments and replies are linked to post");
        check(fb.posts.likePost(bob, publicPost.id()) == 1 && fb.posts.likePost(bob, publicPost.id()) == 1, "Repeated likes do not inflate count");
        check(fb.posts.likeComment(alice, comment.id()) == 1, "Comments can be liked");
        fb.posts.unlikePost(bob, publicPost.id());
        check(fb.posts.likePost(carol, publicPost.id()) == 1, "Unlike removes the individual reaction");
        Post shared = fb.posts.share(bob, privatePost.id(), "Read this", Audience.PUBLIC);
        denied(() -> fb.posts.read(carol, shared.id()), "Public share cannot expose private original");
        fb.posts.setAudience(alice, privatePost.id(), Audience.ONLY_ME);
        denied(() -> fb.posts.read(bob, shared.id()), "Existing shares obey changed original privacy");
        check(fb.search.posts(carol, "diary").isEmpty(), "Search filters private content");

        section("4. Private groups and public pages");
        Community group = fb.communities.create(alice, "Java Club", CommunityType.GROUP, true);
        check(fb.communities.join(bob, group.id()) == MembershipStatus.PENDING, "Private group requires approval");
        denied(() -> fb.communities.approve(carol, group.id(), bob, true), "Nonowner cannot approve membership");
        fb.communities.approve(alice, group.id(), bob, true);
        Post groupPost = fb.posts.publish(bob, "Group study plan", Audience.PUBLIC, group.id());
        check(fb.posts.read(alice, groupPost.id()).id() == groupPost.id(), "Active member reads group content");
        denied(() -> fb.posts.read(carol, groupPost.id()), "Nonmember cannot read private group content");
        denied(() -> fb.posts.share(bob, groupPost.id(), "Outside", Audience.PUBLIC), "Private group content cannot be shared outside");
        check(fb.search.communities(carol, "Java").size() == 1, "Private group name remains discoverable");
        fb.communities.leave(bob, group.id());
        denied(() -> fb.posts.read(bob, groupPost.id()), "Former member loses private-group access");
        fb.communities.join(bob, group.id());
        fb.communities.approve(alice, group.id(), bob, true);
        fb.communities.ban(alice, group.id(), bob);
        denied(() -> fb.communities.join(bob, group.id()), "Banned member cannot rejoin");
        fb.communities.unban(alice, group.id(), bob);
        Community page = fb.communities.create(alice, "Backend Weekly", CommunityType.PAGE, false);
        fb.communities.join(carol, page.id()); // Join means follow for a page.
        Post pagePost = fb.posts.publish(alice, "Weekly Java article", Audience.PUBLIC, page.id());
        check(fb.posts.feed(carol, 20).contains(pagePost), "Followed page contributes to feed");
        denied(() -> fb.posts.publish(carol, "Spam", Audience.PUBLIC, page.id()), "Page followers cannot publish as owner");
        fb.communities.leave(carol, page.id());
        fb.communities.setPrivacy(alice, group.id(), false);
        check(fb.posts.read(carol, groupPost.id()).id() == groupPost.id(), "Group privacy change affects existing posts");
        fb.communities.delete(alice, group.id());
        denied(() -> fb.posts.read(carol, groupPost.id()), "Deleted community content becomes unavailable");

        section("5. Messaging, blocking and unfriending");
        fb.messages.send(alice, bob, "Ready for the interview?");
        fb.messages.send(bob, alice, "Yes!");
        check(fb.messages.conversation(bob, alice).size() == 2, "Both participants read the direct conversation");
        check(fb.messages.conversation(carol, alice).isEmpty(), "Another user's conversation is separate");
        fb.relationships.block(alice, bob);
        check(!fb.relationships.areFriends(alice, bob), "Blocking removes friendship");
        denied(() -> fb.messages.send(bob, alice, "Blocked"), "Blocked pair cannot send messages");
        denied(() -> fb.posts.read(bob, publicPost.id()), "Blocking overrides public visibility");
        check(fb.search.users(bob, "Alice").isEmpty(), "Blocked identity omitted from search");
        check(fb.messages.conversation(bob, alice).size() == 2, "Blocking preserves existing message history");
        fb.relationships.unblock(alice, bob);
        check(!fb.relationships.areFriends(alice, bob), "Unblocking does not restore friendship");
        FriendRequest again = fb.relationships.request(alice, bob);
        fb.relationships.respond(bob, again.id(), RequestStatus.ACCEPTED);
        fb.relationships.unfriend(bob, alice);
        fb.relationships.unfollow(carol, alice);
        check(!fb.relationships.areFriends(alice, bob) && !fb.relationships.follows(carol, alice), "Unfriend and unfollow are independent actions");

        section("6. Notifications and deletion");
        check(!fb.notifications.inbox(bob).isEmpty(), "Friend requests, comments and messages create notifications");
        Post disposable = fb.posts.publish(alice, "Temporary", Audience.PUBLIC, null);
        Post disposableShare = fb.posts.share(carol, disposable.id(), "Bookmark", Audience.PUBLIC);
        denied(() -> fb.posts.delete(carol, disposable.id()), "Only author may delete post");
        fb.posts.delete(alice, disposable.id());
        denied(() -> fb.posts.read(carol, disposableShare.id()), "Deleting original invalidates its shares");
        System.out.println("\nCompleted " + checks + " verified checks.");
    }
}
