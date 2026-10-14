package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.repository.AIRepository;

/**
 * The AIs of the current user are their own and the ones shared by an administrator ({@link AI#isShared()}), see
 * {@link #findAllForUser()}. Only the owner can change or delete an AI, only an administrator can share one.
 */
public interface AIService extends ExtendableService<AI, AIRepository> {

    /**
     * Whether users who are not administrators can create their own AIs (application setting, on by default).
     */
    boolean isUserCreationAllowed() throws Exception;

    /**
     * Changes {@link #isUserCreationAllowed()}, administrators only.
     */
    void setUserCreationAllowed(boolean allowed) throws Exception;

    /**
     * Whether the current user can create a new AI: administrators always, others when {@link #isUserCreationAllowed()}.
     */
    boolean canCreate() throws Exception;

    /**
     * Whether the current user is an administrator, for the UI to show what only administrators can do (sharing an
     * AI, limits). Read from the database like {@code AdminGuard}: the session's copy of the user does not have the
     * flag filled in, so {@code currentUser.isAdmin()} is always false in the UI.
     */
    boolean isAdministrator();

    /**
     * Whether the AI is the current user's own, as opposed to one shared by somebody else. Only own AIs can be changed.
     */
    boolean isOwnedByCurrentUser(AI ai);

}
