package com.thesis.elearning.modules.question.domain;

/**
 * The authenticated principal, reduced to what the authoring rules need.
 *
 * <p>Passed in rather than read from {@code SecurityContextHolder} inside the
 * service so ownership checks stay testable: a unit test constructs
 * {@code new ActorContext(7L, false)} directly instead of standing up a filter
 * chain. The controller is the single place that converts an {@code Authentication}
 * into one of these.
 *
 * @param userId the {@code users.user_id} carried in the JWT subject.
 * @param admin  whether the principal holds ROLE_ADMIN, which bypasses the
 *               author-owns-the-question rule but not the role gate itself.
 */
public record ActorContext(Long userId, boolean admin) {}
