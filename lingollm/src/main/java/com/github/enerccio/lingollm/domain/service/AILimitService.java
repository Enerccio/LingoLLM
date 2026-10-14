package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.components.LimitStatus;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

/**
 * Limits of the users of a shared AI. An administrator sets them per user (they are stored in the AI, see
 * {@link AI#getLimits()}); the tokens a user used since the last reset are in their usage ({@link UsageService}).
 * A user without a limit is not limited.
 * <p>
 * Nothing resets by a timer: when the period of the limit ({@code ResetStrategy}) changed since the user last used the AI,
 * the used tokens are taken as zero when read and set to zero when the next request is counted.
 */
public interface AILimitService {

    /**
     * A limit of a user.
     */
    record Entry(User user, AILimit limit) {}

    /**
     * Limits of all users, for the owner of the AI. Users that were deleted are left out.
     *
     * @throws SecurityException for anybody but an administrator who owns the AI
     */
    List<Entry> findLimits(AI ai) throws Exception;

    /**
     * Sets or replaces the limit of the user.
     *
     * @throws IllegalArgumentException when the AI is not shared or the limit is not valid ({@link AILimit#validate()})
     * @throws SecurityException        for anybody but an administrator who owns the AI
     */
    void setLimit(AI ai, User user, AILimit limit) throws Exception;

    /**
     * Removes the limit of the user, who then is not limited.
     *
     * @throws SecurityException for anybody but an administrator who owns the AI
     */
    void removeLimit(AI ai, User user) throws Exception;

    /**
     * The limit of the current user on the AI, {@link AILimit#unlimited()} when they have none.
     */
    AILimit getLimit(AI ai) throws Exception;

    /**
     * The state of the current user's limit on the AI, for them to see.
     */
    LimitStatus getStatus(AI ai) throws Exception;

    /**
     * Whether the current user can send a request to the AI now, without looking at the size of the request:
     * {@link #check(AI, long, long)} with 0.
     */
    default void check(AI ai) throws Exception {
        check(ai, 0, 0);
    }

    /**
     * {@link #check(AI, long, long)} without a reserved response, for a limit that is not hard.
     */
    default void check(AI ai, long promptTokens) throws Exception {
        check(ai, promptTokens, 0);
    }

    /**
     * Whether the current user can send a request of the given size to the AI now. The tokens sent (and the total)
     * are known in advance, so a request that would go over them is refused; the tokens generated are not, they stop
     * the user only once the limit is reached, unless the administrator made it a
     * {@link AILimit#isHardGeneratedLimit() hard} limit: then the response the request reserves has to fit too.
     *
     * @param promptTokens   estimated tokens of the request
     * @param reservedTokens the largest response the request allows (0 when it has no limit)
     * @throws InferenceException of type {@link InferenceException.Type#NOT_AVAILABLE} or
     *                            {@link InferenceException.Type#LIMIT_REACHED} when they can not
     */
    void check(AI ai, long promptTokens, long reservedTokens) throws Exception;

}
