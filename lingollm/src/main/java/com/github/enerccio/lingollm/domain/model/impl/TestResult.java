package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

/**
 * One attempt at a {@link Test}, its answers are {@link TestQuestionResult}s.
 */
@Entity
@Table(name = "test_results")
public class TestResult extends LanguageBoundEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    @CleanupReference(Policy.OWNED_BY)
    private Test test;

    /**
     * The learner has finished the attempt. The result is created when the test is started and its answers are recorded
     * as they are given, so an attempt that is not finished can be continued (a column: the application looks for it).
     */
    @Column(name = "finished", nullable = false)
    private boolean finished = false;

    @ExtendedAttribute
    @Transient
    private Integer score;

    @ExtendedAttribute
    @Transient
    private Integer maxScore;

    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }
}
