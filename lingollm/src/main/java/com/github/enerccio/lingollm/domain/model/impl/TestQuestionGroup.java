package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

/**
 * A group of {@link TestQuestion}s of a {@link Test} that share something, like a text to read. What the group shows
 * is up to its factory, see TestQuestionGroupFactoryRegistry. The groups of a test follow their {@link #getOrdinal()
 * ordinal}, the questions of a group theirs.
 */
@Entity
@Table(name = "test_question_groups")
public class TestQuestionGroup extends LanguageBoundEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentTest_id")
    @CleanupReference(Policy.OWNED_BY)
    private Test parentTest;

    // position in the test
    private Integer ordinal;

    // class name of the TestQuestionGroupFactory that handles the group, see TestQuestionGroupFactoryRegistry
    @ExtendedAttribute
    @Transient
    private String factory;

    public Test getParentTest() {
        return parentTest;
    }

    public void setParentTest(Test parentTest) {
        this.parentTest = parentTest;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getFactory() {
        return factory;
    }

    public void setFactory(String factory) {
        this.factory = factory;
    }
}
