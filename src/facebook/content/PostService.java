package facebook.content;

import facebook.community.CommunityService;
import facebook.notification.NotificationSink;
import facebook.social.RelationshipService;
import facebook.user.UserDirectory;

import java.time.*;
import java.util.*;

import static facebook.common.Checks.*;

public final class PostService {
    private record ReactionTarget(boolean comment, long id) {
    }

    private final UserDirectory users;
    private final CommunityService communities;
    private final RelationshipService relationships;
    private final PostAccessPolicy access;
    private final NotificationSink notifications;
    private final Clock clock;
    private final Map<Long, Post> posts = new LinkedHashMap<>();
    private final Map<Long, List<Long>> byAuthor = new HashMap<>(), byCommunity = new HashMap<>();
    private final Map<Long, Comment> comments = new HashMap<>();
    private final Map<Long, List<Long>> commentsByPost = new HashMap<>();
    private final Map<ReactionTarget, Set<Long>> likes = new HashMap<>();
    private long nextPostId = 1, nextCommentId = 1;

    public PostService(UserDirectory users, CommunityService communities, RelationshipService relationships,
                       PostAccessPolicy access, NotificationSink notifications, Clock clock) {
        this.users = users;
        this.communities = communities;
        this.relationships = relationships;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
    }

    public Post publish(long actor, String body, Audience audience, Long communityId) {
        users.get(actor);
        Objects.requireNonNull(audience);
        String validated = text(body);
        if (communityId != null) {
            require(communities.canPublish(actor, communityId), "Cannot publish in this community");
            require(audience == Audience.PUBLIC, "Community membership controls community-post visibility");
        }
        return store(actor, validated, audience, communityId, null);
    }

    private Post store(long actor, String body, Audience audience, Long communityId, Long originalId) {
        Post post = new Post(nextPostId++, actor, body, audience, communityId, originalId, clock.instant(), false);
        posts.put(post.id(), post);
        byAuthor.computeIfAbsent(actor, id -> new ArrayList<>()).add(post.id());
        if (communityId != null) byCommunity.computeIfAbsent(communityId, id -> new ArrayList<>()).add(post.id());
        return post;
    }

    public boolean canView(long viewer, Post post) {
        if (!access.canView(viewer, post)) return false;
        if (post.originalPostId() == null) return true;
        Post original = posts.get(post.originalPostId());
        return original != null && access.canView(viewer, original);
    }

    public Post read(long actor, long postId) {
        users.get(actor);
        Post post = posts.get(postId);
        require(post != null && canView(actor, post), "Post unavailable");
        return post;
    }

    public Post share(long actor, long postId, String caption, Audience audience) {
        Post source = read(actor, postId);
        Objects.requireNonNull(audience);
        long originalId = source.originalPostId() == null ? source.id() : source.originalPostId();
        Post original = read(actor, originalId);
        require(original.communityId() == null || !communities.get(original.communityId()).privateGroup(),
                "Cannot share private-group posts outside the group");
        Post shared = store(actor, caption == null ? "" : caption.trim(), audience, null, originalId);
        if (actor != original.authorId()) notifications.send(original.authorId(), "Post shared #" + originalId);
        return shared;
    }

    public void setAudience(long actor, long postId, Audience audience) {
        Post post = owned(actor, postId);
        Objects.requireNonNull(audience);
        require(post.communityId() == null, "Use community privacy for community posts");
        posts.put(postId, post.withAudience(audience));
    }

    public void delete(long actor, long postId) {
        posts.put(postId, owned(actor, postId).tombstone());
    }

    private Post owned(long actor, long id) {
        users.get(actor);
        Post post = posts.get(id);
        require(post != null && !post.deleted() && post.authorId() == actor, "Only author may modify post");
        return post;
    }

    public Comment comment(long actor, long postId, Long parentId, String body) {
        Post post = read(actor, postId);
        if (parentId != null) {
            Comment parent = comments.get(parentId);
            require(parent != null && parent.postId() == postId, "Parent must belong to the same post");
            require(!relationships.blocked(actor, parent.authorId()), "Cannot reply to a blocked user");
        }
        Comment comment = new Comment(nextCommentId++, postId, parentId, actor, text(body), clock.instant());
        comments.put(comment.id(), comment);
        commentsByPost.computeIfAbsent(postId, id -> new ArrayList<>()).add(comment.id());
        if (actor != post.authorId()) notifications.send(post.authorId(), "Comment on post #" + postId);
        return comment;
    }

    public List<Comment> comments(long actor, long postId) {
        read(actor, postId);
        return commentsByPost.getOrDefault(postId, List.of()).stream().map(comments::get)
                .filter(c -> !relationships.blocked(actor, c.authorId())).toList();
    }

    public int likePost(long actor, long id) {
        read(actor, id);
        return like(actor, new ReactionTarget(false, id));
    }

    public int likeComment(long actor, long id) {
        Comment comment = comments.get(id);
        require(comment != null, "Comment not found");
        read(actor, comment.postId());
        require(!relationships.blocked(actor, comment.authorId()), "Comment unavailable");
        return like(actor, new ReactionTarget(true, id));
    }

    private int like(long actor, ReactionTarget target) {
        Set<Long> reactors = likes.computeIfAbsent(target, id -> new HashSet<>());
        reactors.add(actor);
        return reactors.size(); // A repeated like is idempotent.
    }

    public void unlikePost(long actor, long id) {
        read(actor, id);
        likes.getOrDefault(new ReactionTarget(false, id), new HashSet<>()).remove(actor);
    }

    public List<Post> visiblePosts(long actor) {
        users.get(actor);
        return posts.values().stream().filter(p -> canView(actor, p)).toList();
    }

    /**
     * Candidate IDs are deduplicated because a post can enter through multiple subscriptions.
     */
    public List<Post> feed(long actor, int limit) {
        users.get(actor);
        require(limit > 0 && limit <= 100, "Limit must be 1..100");
        Set<Long> authors = new HashSet<>(relationships.friendsOf(actor));
        authors.addAll(relationships.followsOf(actor));
        authors.add(actor);
        Set<Long> candidates = new HashSet<>();
        for (long author : authors) candidates.addAll(byAuthor.getOrDefault(author, List.of()));
        for (long community : communities.subscriptions(actor))
            candidates.addAll(byCommunity.getOrDefault(community, List.of()));
        return candidates.stream().map(posts::get).filter(p -> canView(actor, p))
                .sorted(Comparator.comparing(Post::createdAt).thenComparingLong(Post::id).reversed())
                .limit(limit).toList();
    }
}
