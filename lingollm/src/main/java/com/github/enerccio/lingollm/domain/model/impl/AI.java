package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.collections.AIType;
import com.github.enerccio.lingollm.domain.collections.ReasoningEffort;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.service.InferenceService;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

@Entity
@Table(name = "ais")
@Inheritance(strategy = InheritanceType.JOINED)
public class AI extends ExtendableEntity {

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "ai_type")
    private AIType aiType;

    @Transient
    private InferenceService inferenceService;

    @Lob
    private String name;

    @Lob
    private String jailbreak;

    @Column
    private Boolean needsJailbreak;

    // reasoning

    @Column
    private Boolean enabledReasoning;

    @Column
    @Enumerated(EnumType.STRING)
    private ReasoningEffort reasoningEffort;

    @Column
    private Integer maxCompletionTokens;

    @Column
    private Integer maxContext;

    /**
     * Shared with all users: they can pick it for their chats, only the owner (an administrator) can see and change
     * it. Real column because the lookups of the user's AIs filter on it.
     */
    @Column(nullable = false)
    private boolean shared = false;

    /**
     * Limits of the users of a shared AI: a JSON object with the settings of each user under their uuid, see
     * {@code AILimits}. Not a column, nothing searches by it; what a user used is in their {@link Usage} (counters
     * that change with every request must not be saved with the AI).
     */
    @Transient
    @ExtendedAttribute
    private String limits;

    public AIType getAiType() {
        return aiType;
    }

    public void setAiType(AIType aiType) {
        this.aiType = aiType;
        this.inferenceService = null;
    }

    public InferenceService getInferenceService() {
        return inferenceService;
    }

    public void setInferenceService(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJailbreak() {
        return jailbreak;
    }

    public void setJailbreak(String jailbreak) {
        this.jailbreak = jailbreak;
    }

    public Boolean getNeedsJailbreak() {
        return needsJailbreak;
    }

    public void setNeedsJailbreak(Boolean needsJailbreak) {
        this.needsJailbreak = needsJailbreak;
    }

    public Boolean getEnabledReasoning() {
        return enabledReasoning;
    }

    public void setEnabledReasoning(Boolean enabledReasoning) {
        this.enabledReasoning = enabledReasoning;
    }

    public ReasoningEffort getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(ReasoningEffort reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Integer getMaxContext() {
        return maxContext;
    }

    public void setMaxContext(Integer maxContext) {
        this.maxContext = maxContext;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getLimits() {
        return limits;
    }

    public void setLimits(String limits) {
        this.limits = limits;
    }
}
