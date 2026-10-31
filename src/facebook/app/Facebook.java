package facebook.app;

import facebook.community.CommunityService;
import facebook.content.*;
import facebook.discovery.SearchService;
import facebook.messaging.MessagingService;
import facebook.notification.NotificationService;
import facebook.social.RelationshipService;
import facebook.user.*;

import java.time.Clock;

/**
 * Composition root: wires dependencies; deliberately contains no business logic.
 */
public final class Facebook {
    public final UserDirectory users;
    public final ProfileService profiles;
    public final NotificationService notifications;
    public final RelationshipService relationships;
    public final CommunityService communities;
    public final PostService posts;
    public final MessagingService messages;
    public final SearchService search;

    public Facebook(Clock clock) {
        users = new UserDirectory();
        notifications = new NotificationService(clock);
        relationships = new RelationshipService(users, notifications);
        profiles = new ProfileService(users, relationships);
        communities = new CommunityService(users, notifications);
        PostAccessPolicy access = new PostAccessPolicy(relationships, communities);
        posts = new PostService(users, communities, relationships, access, notifications, clock);
        messages = new MessagingService(users, relationships, notifications, clock);
        search = new SearchService(users, relationships, communities, posts);
    }
}
