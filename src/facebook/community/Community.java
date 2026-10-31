package facebook.community;

/**
 * Pages are public; private groups require membership to read posts.
 */
public record Community(long id, String name, long ownerId, CommunityType type, boolean privateGroup) {
}
