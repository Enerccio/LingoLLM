package com.github.enerccio.lingollm.domain.components;

import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.collections.ResetStrategy;

/**
 * What one user may use of a shared AI. An administrator sets it per user, a user without one is not limited. It
 * is stored in the AI ({@link AILimits}); what the user used since the last reset is in their usage.
 * <p>
 * The token limits are only read for {@link LimitStrategy#TOKEN_LIMIT}; each one can be null, which is unlimited.
 * Whichever is reached first stops the user: tokens sent, tokens generated, or both added up.
 */
public class AILimit {

    /**
     * What is wrong with a limit that can not be saved.
     */
    public enum Problem {
        /** A negative number of tokens. */
        NEGATIVE,
        /** All three limits are set and sent plus generated is less than the total, which then can never be reached. */
        TOTAL_UNREACHABLE
    }

    private LimitStrategy strategy = LimitStrategy.UNLIMITED;
    private ResetStrategy resetStrategy = ResetStrategy.DAILY;
    private Long prefillTokens;
    private Long generatedTokens;
    private Long totalTokens;
    private boolean hardGeneratedLimit;

    public static AILimit unlimited() {
        return new AILimit();
    }

    public LimitStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(LimitStrategy strategy) {
        this.strategy = strategy == null ? LimitStrategy.UNLIMITED : strategy;
    }

    public ResetStrategy getResetStrategy() {
        return resetStrategy;
    }

    public void setResetStrategy(ResetStrategy resetStrategy) {
        this.resetStrategy = resetStrategy == null ? ResetStrategy.DAILY : resetStrategy;
    }

    /**
     * @return limit of tokens sent to the model (the prompts), null when not limited
     */
    public Long getPrefillTokens() {
        return prefillTokens;
    }

    public void setPrefillTokens(Long prefillTokens) {
        this.prefillTokens = prefillTokens;
    }

    /**
     * @return limit of tokens the model generated (the responses), null when not limited
     */
    public Long getGeneratedTokens() {
        return generatedTokens;
    }

    public void setGeneratedTokens(Long generatedTokens) {
        this.generatedTokens = generatedTokens;
    }

    /**
     * @return limit of sent and generated tokens together, null when not limited
     */
    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * @return whether the response a request reserves (its maximum response size) must fit into the limit of the
     * generated tokens, otherwise the request is refused; without it the limit only stops the user once it is reached
     * and the last response can go over it
     */
    public boolean isHardGeneratedLimit() {
        return hardGeneratedLimit;
    }

    public void setHardGeneratedLimit(boolean hardGeneratedLimit) {
        this.hardGeneratedLimit = hardGeneratedLimit;
    }

    /**
     * @return whether it limits the user at all
     */
    public boolean isRestrictive() {
        return strategy == LimitStrategy.NOT_AVAILABLE
                || strategy == LimitStrategy.TOKEN_LIMIT && (prefillTokens != null || generatedTokens != null || totalTokens != null);
    }

    /**
     * @return what is wrong with the limit, null when it is fine
     */
    public Problem validate() {
        if (strategy != LimitStrategy.TOKEN_LIMIT) {
            return null;
        }
        if (isNegative(prefillTokens) || isNegative(generatedTokens) || isNegative(totalTokens)) {
            return Problem.NEGATIVE;
        }
        if (prefillTokens != null && generatedTokens != null && totalTokens != null
                && prefillTokens + generatedTokens < totalTokens) {
            return Problem.TOTAL_UNREACHABLE;
        }
        return null;
    }

    private static boolean isNegative(Long value) {
        return value != null && value < 0;
    }
}
