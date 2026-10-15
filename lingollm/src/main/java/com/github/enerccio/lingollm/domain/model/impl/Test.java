package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * A set of {@link TestQuestion}s the learner can take. Every attempt is a {@link TestResult}.
 */
@Entity
@Table(name = "tests")
public class Test extends LanguageBoundEntity {

    @ExtendedAttribute
    @Transient
    private String name;

    @ExtendedAttribute
    @Transient
    private String description;

    /**
     * What the model reasoned while it wrote the test, kept for debugging or when the user is curious. Null when the
     * test was not written by a model or the provider does not send reasoning.
     */
    @ExtendedAttribute
    @Transient
    private String reasoning;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
