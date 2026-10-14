package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * A free conversation in the learned language, with no lesson behind it.
 */
@Entity
@Table(name = "free_chats")
public class FreeChat extends LanguageBoundEntity {

    @ExtendedAttribute
    @Transient
    private String rootPrompt;

    @ExtendedAttribute
    @Transient
    private String prompt;

    public String getRootPrompt() {
        return rootPrompt;
    }

    public void setRootPrompt(String rootPrompt) {
        this.rootPrompt = rootPrompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
