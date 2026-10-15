package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.collections.GradedBy;
import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.collections.TestVerdict;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

/**
 * The answer to one {@link TestQuestion} within a {@link TestResult} and how it was judged.
 */
@Entity
@Table(name = "test_question_results")
public class TestQuestionResult extends LanguageBoundEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "testResult_id")
    @CleanupReference(Policy.OWNED_BY)
    private TestResult testResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    @CleanupReference(Policy.OWNED_BY)
    private TestQuestion question;

    @ExtendedAttribute
    @Transient
    private String answer;

    @ExtendedAttribute
    @Transient
    private TestVerdict verdict;

    @ExtendedAttribute
    @Transient
    private GradedBy gradedBy;

    /**
     * The learner said they do not know the answer, so there is none ({@link #getAnswer()} is null). Judged as a
     * wrong one, but it tells a guess from knowing nothing.
     */
    @ExtendedAttribute
    @Transient
    private boolean dontKnow = false;

    // explanation or correction of the judge
    @ExtendedAttribute
    @Transient
    private String feedback;

    public TestResult getTestResult() {
        return testResult;
    }

    public void setTestResult(TestResult testResult) {
        this.testResult = testResult;
    }

    public TestQuestion getQuestion() {
        return question;
    }

    public void setQuestion(TestQuestion question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isDontKnow() {
        return dontKnow;
    }

    public void setDontKnow(boolean dontKnow) {
        this.dontKnow = dontKnow;
    }

    public TestVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(TestVerdict verdict) {
        this.verdict = verdict;
    }

    public GradedBy getGradedBy() {
        return gradedBy;
    }

    public void setGradedBy(GradedBy gradedBy) {
        this.gradedBy = gradedBy;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
