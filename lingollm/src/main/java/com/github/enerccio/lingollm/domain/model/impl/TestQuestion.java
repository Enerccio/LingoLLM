package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

/**
 * A question of a {@link Test}, in a {@link TestQuestionGroup}. The questions of a group follow their {@link #getOrdinal()
 * ordinal}.
 */
@Entity
@Table(name = "test_questions")
public class TestQuestion extends LanguageBoundEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentTest_id")
    @CleanupReference(Policy.OWNED_BY)
    private Test parentTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionGroup_id")
    @CleanupReference(Policy.OWNED_BY)
    private TestQuestionGroup questionGroup;

    @ExtendedAttribute
    @Transient
    private String testQuestionFactory;

    @Transient
    private TestQuestionFactory cachedFactory;

    // position in the group
    private Integer ordinal;

    public Test getParentTest() {
        return parentTest;
    }

    public void setParentTest(Test parentTest) {
        this.parentTest = parentTest;
    }

    /**
     * @return the group the question belongs to, what the question is shown with
     */
    public TestQuestionGroup getQuestionGroup() {
        return questionGroup;
    }

    public void setQuestionGroup(TestQuestionGroup questionGroup) {
        this.questionGroup = questionGroup;
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
