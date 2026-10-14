package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import jakarta.persistence.*;

import java.util.Date;

/**
 * What a user has used of an AI. There is one per AI and user (the owner of this entity): an AI that is used by its
 * owner only has one, a shared AI has one for everybody who uses it. Counts what the user used, and since the last reset for their limit ({@code AILimit}).
 * <p>
 * Counters are real columns because they are added up in SQL ({@code UsageRepository#add}), not read and saved, so
 * parallel generations don't lose counts.
 */
@Entity
@Table(name = "usages")
public class Usage extends OwnedEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "ai_id", nullable = false)
    @CleanupReference(Policy.OWNED_BY)
    private AI ai;

    /**
     * Tokens sent to the model (the prompt).
     */
    @Column(nullable = false)
    private long prefillTokens;

    /**
     * Tokens the model generated (the response, reasoning included).
     */
    @Column(nullable = false)
    private long generatedTokens;

    /**
     * Number of requests the counters were added up from.
     */
    @Column(nullable = false)
    private long requests;

    /**
     * Tokens sent since the last reset of the user's token limit ({@code AILimit}); only counted while the user has
     * one, zero otherwise. Stale when {@link #lastUsed} is before the start of the current period, see
     * {@code AILimitService#getStatus}.
     */
    @Column(nullable = false)
    private long periodPrefillTokens;

    /**
     * Tokens generated since the last reset, see {@link #periodPrefillTokens}.
     */
    @Column(nullable = false)
    private long periodGeneratedTokens;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUsed;

    public AI getAi() {
        return ai;
    }

    public void setAi(AI ai) {
        this.ai = ai;
    }

    public long getPrefillTokens() {
        return prefillTokens;
    }

    public void setPrefillTokens(long prefillTokens) {
        this.prefillTokens = prefillTokens;
    }

    public long getGeneratedTokens() {
        return generatedTokens;
    }

    public void setGeneratedTokens(long generatedTokens) {
        this.generatedTokens = generatedTokens;
    }

    public long getRequests() {
        return requests;
    }

    public void setRequests(long requests) {
        this.requests = requests;
    }

    public long getPeriodPrefillTokens() {
        return periodPrefillTokens;
    }

    public void setPeriodPrefillTokens(long periodPrefillTokens) {
        this.periodPrefillTokens = periodPrefillTokens;
    }

    public long getPeriodGeneratedTokens() {
        return periodGeneratedTokens;
    }

    public void setPeriodGeneratedTokens(long periodGeneratedTokens) {
        this.periodGeneratedTokens = periodGeneratedTokens;
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }
}
