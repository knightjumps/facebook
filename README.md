# Facebook LLD — Java interview reference

Original implementation; no Educative code or third-party libraries. Java 17+.

## Run

```bash
bash run.sh
```

The script compiles with Java 17 compatibility and all compiler warnings enabled,
then runs the driver and boundary checks. In IntelliJ, run `src/Main.java`.
Storage is in memory and resets on each run. There is no Spring Boot setup to
explain before reaching the business logic.

## Scope and explicit product rules

Implemented:

- Register users, edit profiles, and restrict profile details to public/friends/self.
- Search public user identity; private profile details require a separate read.
- Send/accept/reject/cancel friend requests; mutual friendship; independent follows.
- Unfriend, unfollow, block and unblock. Either user's block denies new connections,
  new messages, profile details and the other author's content. Blocking removes
  friendship, both follows and a pending invitation. Unblocking restores none.
- Publish personal posts with PUBLIC, FRIENDS or ONLY_ME audiences; change audience
  and delete posts. Comment/reply, like posts/comments, unlike posts, and share posts.
- Read a chronological feed from self, friends, followed users and subscribed communities.
- Create groups and pages; request membership/follow; approve/reject; leave/unfollow;
  owner moderation (ban/unban), privacy change and community deletion.
- Private-group content is available to active members only and cannot be shared out.
- Page followers read; only the owner publishes. The owner is the sole administrator
  in this implementation. A membership is a follow when the community is a page.
- Direct messages and participant-only conversation lookup.
- Stored notifications for friend requests/acceptance, membership requests/decisions,
  messages, comments and shares. Notification bodies contain no private content.
- Case-insensitive substring search for users, communities and accessible posts.

Deliberate scope limits:

- No authentication implementation: `actor` is a trusted identity supplied by an
  authenticated controller in a real application. Never take it directly from a
  client's request body. These are application/domain classes, not HTTP endpoints.
- No attachment uploads, custom audience lists, account suspension, recommendations,
  group messaging, read receipts, post editing or comment editing/deletion.
- No follow notifications or fan-out notifications to every follower when publishing.
  A followed source's new posts are retrieved through the feed. Avoid an O(followers)
  loop in `publish()` merely to demonstrate Observer.
- Private groups remain discoverable by name, but their posts remain private.
- Group joining is controlled by the group's privacy setting; a separate approval
  policy would be an extension. Changing private to public makes existing posts public
  to nonbanned users. Existing pending requests still need an explicit decision.
- Group/page admins are resource owners; multiple admins and ownership transfer are
  extensions. The owner cannot leave or ban themselves.
- Existing direct-message history remains readable after blocking. New sends fail.
- Authors cannot read private-group posts after leaving, although they can still delete
  their own post. Deleting a community makes its posts unavailable; tombstones and
  related records are retained in memory, not physically purged.
- Public community activity by a friend/followed author can appear in your feed even
  if you do not subscribe to that community. Access is checked in every case.

## Packages and responsibilities

```text
src/
  Main.java                   IntelliJ entry point
  facebook/
    app/                      Dependency wiring and executable walkthrough
    user/                     Immutable identity/profile data and profile authorization
    social/                   Requests, friendship adjacency, following, blocking
    content/                  Posts, comments, reactions, feed and visibility policy
    community/                Groups/pages and resource-scoped membership
    messaging/                Direct conversation storage and sending
    notification/             Delivery port and in-memory notification inbox
    discovery/                Search across existing features
    common/                   Tiny shared validation helper
```

`Facebook` is a composition root: it constructs and connects services. It does not
implement any feature. `Driver` demonstrates features. Neither is a God object.
Services own mutable maps; records are immutable values returned to callers.
Collection fields and query results are copied or returned as unmodifiable snapshots.
`UserDirectory.get/all` are internal storage operations. Viewer-facing profile access
must go through `ProfileService.read`, and search returns only `UserSummary`.

## Architecture and why these choices exist

```text
Driver / future authenticated controllers
    |
    +--> RelationshipService --> UserDirectory
    +--> CommunityService    --> UserDirectory
    +--> ProfileService      --> UserDirectory + RelationshipService
    +--> PostService         --> UserDirectory + CommunityService
    |        |                  + RelationshipService
    |        +--> PostAccessPolicy --> RelationshipService + CommunityService
    +--> MessagingService    --> UserDirectory + RelationshipService
    +--> SearchService       --> UserDirectory + communities + posts + relationships

Actions that generate alerts --> NotificationSink <-- NotificationService
```

### 1. Feature packages instead of a giant User class

A user is an identity, not the controller for the entire product. Relationship,
content and messaging rules evolve independently. Keeping them separate makes the
code explainable and keeps a change to one feature from growing `User` indefinitely.
These are modules in one application, not independently deployed microservices.

### 2. Immutable records and guarded transitions

`FriendRequest.respond` verifies the actor, pending state and requested transition,
then returns a new request value. There is no public status setter. Services replace
the stored value only after validation. We do not need State-pattern classes for four
simple states. Old returned records are snapshots; reread state after an update.

```text
           recipient accepts --> ACCEPTED
PENDING -- recipient rejects --> REJECTED
           sender cancels    --> CANCELED
```

A same-direction pending request retry returns the existing request. A crossing
request fails: the recipient should accept the incoming request. A resolved request
cannot transition again. Respond retries are deliberately errors; send retries and
likes are idempotent (repeating them does not duplicate their effect).

### 3. Friendship is not following

Friendships use two adjacency-set entries for efficient per-user lookup, while
follows are directional. A normalized user-pair key identifies one pending invitation
regardless of direction. Blocks retain direction, but access is denied if either user
has blocked the other. Hash sets prevent duplicate edges and reactions.

### 4. One privacy policy and current-state checks

`PostAccessPolicy` checks deletion, blocks, community access and audience. `PostService`
also checks the original of a share. Direct reads, comments, likes, feed and search
reuse those decisions. An enum labels an audience; policy code enforces it.

A share stores the original post ID rather than copying its body. Shares of shares
are flattened to that original ID. Tightening privacy or deleting the original
immediately invalidates unauthorized shares. Shares also retain their own audience.
Community posts use PUBLIC at the post level, with the community deciding access;
this avoids contradictory group-membership and friends-only rules in this exercise.

### 5. Groups and pages share a model, not every rule

`Community` plus `CommunityType` is intentional: both have an ID, owner, subscribers
and content. Two small rule branches distinguish publishing and joining. In a larger
product, introduce `Group` and `Page` behind an interface when their behavior diverges
substantially; empty subclasses today would add ceremony without useful behavior.
Membership is scoped to a community; there is no global `user.isAdmin` flag.

### 6. Narrow interfaces, not an interface for every class

`NotificationSink` is a real variation point: features can depend on an alert contract
without knowing the delivery implementation. The runnable sink stores alerts in memory.
We avoid speculative factories, Singleton, an event bus and repository interfaces
whose only implementation would forward to the same map. Add repository interfaces
when introducing a database or a concrete substitution need.

### 7. Indexed candidates and simple chronological ordering

Posts are indexed by author and community. Feed construction unions candidates,
deduplicates IDs, filters current access, then orders by time and ID descending.
The ID breaks timestamp ties deterministically. It returns at most 100 entries.
Search deliberately scans in-memory collections: an exact-key map would not implement
substring matching correctly. A production search index is a separate concern.

### 8. Direct conversations use a canonical pair

`(min(userA,userB), max(userA,userB))` identifies one conversation, so messages in
both directions share history. A caller queries only `(actor, other)`; there is no
operation exposing an arbitrary third party's conversation. A separate Conversation
entity becomes useful when adding group participants, titles or per-member receipts.

## Complexity and limitations

Let U = users, G = communities, P = posts, C = candidate feed posts,
K = comments on a post and M = messages in one direct conversation.
Hash operations below are expected/amortized O(1), not worst-case guarantees.

| Operation | Cost | Reason |
|---|---|---|
| Send/respond to request | O(1) | Pair/request maps and adjacency sets |
| Follow/unfollow/block | O(1) | Constant number of hash updates |
| Publish | O(1) | Store and append to author/community indexes |
| Read or check a share | O(1) | At most one original lookup |
| Like/unlike | O(1) | Set of user IDs per target |
| Group join/approve | O(1) | Community membership map |
| Build feed | O(G + A + C log C) | Scan G for subscriptions, A source authors, sort C candidates |
| Search users / communities / posts | O(U) / O(G) / O(P) | Scans, assuming bounded text length |
| Read comments / direct history | O(K) / O(M) | Filter/copy result |

Memory is O(users + relationship edges + invitations + communities + memberships +
posts + comments + individual likes + messages + notifications). Deleted posts and
resolved requests are retained. Lists are unbounded except the feed output limit.
Feed limits do not bound candidate loading or sorting; production needs cursor-based
pagination and a bounded merge of indexed recent posts. Community subscriptions can
also get a reverse user-to-community index when G becomes large.

**Concurrency:** this is explicitly a single-threaded, in-memory interview program.
Maps and multi-object updates are not thread-safe, durable or transactional. Do not
present the checks as proof of production concurrency safety. In-memory notification
delivery is synchronous and assumed to succeed; arbitrary throwing sink implementations
would require a defined failure policy.

For production, explain these extensions rather than attempting them during LLD:

- Authenticated controllers supply actor identity; never expose internal map lookups.
- Store friendship once using normalized endpoints plus a unique constraint.
- Transactionally resolve an invitation, create friendship and record an outbox event.
- Use version checks/locking and constraints for concurrent transitions and duplicate likes.
- Deliver outbox events asynchronously; deduplicate notification handling by event ID.
- Protect media separately; never rely on hiding a UI element or search result.
- Add cursor pagination, full-text indexes, retention and explicit repository boundaries.

## How to use this in a 60–80 minute interview

This repository is an executable reference, not a claim that every line should be
typed live. Agree on priorities first. The core path is friendships + posting/privacy;
groups, messages, search and notifications demonstrate coverage and follow-ups.

| Time | What to do |
|---|---|
| 0–8 min | Clarify mutual friendship, directional following, visibility and blocking |
| 8–18 min | Draw User/Profile, FriendRequest, Post/Comment and service boundaries |
| 18–35 min | Implement request/send/respond, guarded transitions and friendship sets |
| 35–53 min | Implement Post, access policy, publish/read, comments and duplicate-safe likes |
| 53–63 min | Demonstrate driver scenarios: acceptance, denied private read, duplicate like, block |
| 63–73 min | Add one requested extension: feed/sharing, groups/pages or messaging |
| 73–80 min | Discuss complexity, concurrent transitions, transactions and notifications |

For a 60-minute interview, reduce the extension section and preserve the final
correctness discussion. Do not start by creating every file in this repository.
Use plain fields/getters in place of records if the interviewer expects older Java.

Suggested explanation: “I keep coordination in feature services and the request's
state transition in the domain value. Relationships use indexed sets; access checks
are shared across discovery and interaction paths. The program is single-threaded;
a database implementation needs atomic transitions and uniqueness constraints.”

## Verification

`Driver` covers normal and denied flows with always-on checks. `RulesTest` additionally
checks crossing requests, immutable snapshots, notification deduplication, deterministic
feed ordering, shares of shares, invalid comment parents, independent blocks, pending
membership permissions, and ownership. Tests require no frameworks or network.

## Key Takeaways

- Model business distinctions before choosing patterns: request vs friendship,
  friendship vs follow, profile identity vs private details, membership vs ownership.
- Protect permissions at the operation and read boundaries; recheck current privacy.
- Use immutable values, focused services and direct hash indexes for a small clear design.
- Demonstrate invalid transitions and access denials, not just happy-path output.
- State concurrency and persistence limits honestly; explain the production extension.
