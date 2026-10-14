package com.github.enerccio.lingollm.domain.components;

import com.github.enerccio.lingollm.domain.collections.LimitStrategy;

import java.util.Date;

/**
 * Where a user stands with the limit on an AI: the limit and what they used in the current period.
 *
 * @param limit             the limit, {@link AILimit#unlimited()} for a user without one
 * @param usedPrefill       tokens sent since the last reset
 * @param usedGenerated     tokens generated since the last reset
 * @param resetsAt          when the used tokens count from zero again, null when the limit has no period
 */
public record LimitStatus(AILimit limit, long usedPrefill, long usedGenerated, Date resetsAt) {

    /**
     * What stops the user.
     */
    public enum Block {
        NOT_AVAILABLE, PREFILL, GENERATED, TOTAL
    }

    public long usedTotal() {
        return usedPrefill + usedGenerated;
    }

    /**
     * @return what stops the user from using the AI, null when they can
     */
    public Block block() {
        return block(0, 0);
    }

    /**
     * What stops the user from sending a request. The tokens sent and so the total are known before the request, so
     * it is refused when it would go over them; the tokens generated are not, they only stop the user once reached
     * (the last response can go over).
     *
     * With a {@link AILimit#isHardGeneratedLimit() hard} limit of the generated tokens the response the request reserves
     * has to fit as well.
     *
     * @param promptTokens   tokens of the request about to be sent, 0 to only look at what was used
     * @param reservedTokens the largest response the request allows, only looked at for a hard limit
     * @return what stops the user from sending it, null when nothing does
     */
    public Block block(long promptTokens, long reservedTokens) {
        if (limit.getStrategy() == LimitStrategy.NOT_AVAILABLE) {
            return Block.NOT_AVAILABLE;
        }
        if (limit.getStrategy() != LimitStrategy.TOKEN_LIMIT) {
            return null;
        }
        if (limit.getPrefillTokens() != null
                && (usedPrefill >= limit.getPrefillTokens() || usedPrefill + promptTokens > limit.getPrefillTokens())) {
            return Block.PREFILL;
        }
        if (limit.getGeneratedTokens() != null && (usedGenerated >= limit.getGeneratedTokens()
                || limit.isHardGeneratedLimit() && usedGenerated + reservedTokens > limit.getGeneratedTokens())) {
            return Block.GENERATED;
        }
        if (limit.getTotalTokens() != null
                && (usedTotal() >= limit.getTotalTokens() || usedTotal() + promptTokens > limit.getTotalTokens())) {
            return Block.TOTAL;
        }
        return null;
    }

    public boolean isBlocked() {
        return block() != null;
    }
}
