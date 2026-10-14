package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.Date;
import java.util.List;

public interface UsageRepository extends OwnedRepository<Usage> {

    /**
     * @return the usage of the AI by the user, null if the user never used it
     */
    Usage findByAI(AI ai, User user) throws Exception;

    /**
     * @return usages of the AI by all users
     */
    List<Usage> findAllByAI(AI ai) throws Exception;

    /**
     * @return usages of all AIs by all users
     */
    List<Usage> findAllUsages() throws Exception;

    /**
     * Adds to the counters of the usage of the AI by the user (one request), in SQL so parallel additions don't lose
     * counts.
     *
     * @param limited whether the user has a token limit, then the counters since the last reset are added to as well,
     *                otherwise they are set to zero
     * @return number of updated usages, 0 when there is none yet
     */
    int add(AI ai, User user, long prefillTokens, long generatedTokens, boolean limited) throws Exception;

    /**
     * Sets the counters since the last reset to zero when the usage was last used before {@code periodStart}, the
     * lazy reset of a token limit.
     *
     * @return number of reset usages
     */
    int resetPeriod(AI ai, User user, Date periodStart) throws Exception;

}
