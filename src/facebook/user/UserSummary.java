package facebook.user;

/**
 * Search exposes identity, not a potentially private biography.
 */
public record UserSummary(long id, String name) {
}
