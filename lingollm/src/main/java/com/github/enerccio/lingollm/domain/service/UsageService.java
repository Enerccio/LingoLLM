package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.repository.UsageRepository;

import java.util.List;

/**
 * Counts what the current user uses of the AIs, one {@link Usage} per AI and user. Only counting for now.
 */
public interface UsageService extends OwnedService<Usage, UsageRepository> {

    /**
     * Adds one request to the usage of the AI by the current user, creating the usage on first use. Never throws:
     * a failure to count is logged and must not break the generation.
     *
     * @param prefillTokens    tokens sent to the model
     * @param generatedTokens tokens the model generated
     */
    void record(AI ai, long prefillTokens, long generatedTokens);

    /**
     * The transactional part of {@link #record}, only for it (the lock around it must wait for the commit): use
     * {@link #record} instead.
     */
    void add(AI ai, long prefillTokens, long generatedTokens) throws Exception;

    /**
     * @return usage of the AI by the current user, null if they never used it
     */
    Usage findForUser(AI ai) throws Exception;

    /**
     * Usages of the AI by all users, for its owner or an administrator.
     *
     * @throws SecurityException for anybody else
     */
    List<Usage> findAllForAI(AI ai) throws Exception;

    /**
     * Usages of all AIs by all users, for the administrator's overview.
     *
     * @throws SecurityException for anybody but an administrator
     */
    List<Usage> findAllUsages() throws Exception;

}
