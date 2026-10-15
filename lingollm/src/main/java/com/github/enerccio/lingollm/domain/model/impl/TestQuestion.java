package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

/**
 * A question of a {@link Test}.
 */
@Entity
@Table(name = "test_questions")
public class TestQuestion extends LanguageBoundEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentTest_id")
    @CleanupReference(Policy.OWNED_BY)
    private Test parentTest;

    @ExtendedAttribute
    @Transient
    private String testQuestionFactory;

    @Transient
    private TestQuestionFactory cachedFactory;

    // position in the test
    private Integer ordinal;

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

    public String getTestQuestionFactory() {
        return testQuestionFactory;
    }

    public void setTestQuestionFactory(String testQuestionFactory) {
        this.testQuestionFactory = testQuestionFactory;
    }

    public TestQuestionFactory getCachedFactory() {
        return cachedFactory;
    }

    public void setCachedFactory(TestQuestionFactory cachedFactory) {
        this.cachedFactory = cachedFactory;
    }
}
