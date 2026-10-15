package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.service.dto.TestQuestionRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the level test asks the model for, and the places where a language can add more.
 */
class GaugeTestRequestsTest {

    private static class Plain extends BaseLanguageDefinitionService {
        Plain() {
            super(Languages.GERMAN);
        }
    }

    private static class Extended extends BaseLanguageDefinitionService {
        final List<String> between = new ArrayList<>();

        Extended() {
            super(Languages.GERMAN);
        }

        @Override
        protected List<TestQuestionRequest> questionsBetweenSections(String previous, String next) {
            between.add(previous + ">" + next);
            return List.of(new TestQuestionRequest("listening", "SingleChoiceHandler", "about sounds"));
        }

        @Override
        protected List<TestQuestionRequest> questionsAtEnd() {
            return List.of(new TestQuestionRequest("final", "WritingQuestionHandler", null));
        }
    }

    @Test
    void twentyQuestionsFiveOfThemAboutATextInOneRun() {
        List<TestQuestionRequest> requests = new Plain().gaugeQuestionRequests();

        assertThat(requests).hasSize(20);
        assertThat(requests).extracting(TestQuestionRequest::getNumber).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(requests.subList(0, 5)).extracting(TestQuestionRequest::getSection).containsOnly("reading");
        assertThat(requests.subList(5, 20)).extracting(TestQuestionRequest::getSection).containsOnly("other");
        assertThat(requests.subList(0, 5)).extracting(TestQuestionRequest::getType)
                .allMatch(type -> type.startsWith("ReadingComprehension"));
        // the others are a mix of all the other kinds
        assertThat(requests.subList(5, 20)).extracting(TestQuestionRequest::getType).containsOnly(
                "SingleChoiceHandler", "MultipleChoiceHandler", "WritingQuestionHandler", "WordOrderQuestionHandler")
                .contains("WritingQuestionHandler", "WordOrderQuestionHandler", "MultipleChoiceHandler");
    }

    @Test
    void aLanguageCanAddQuestionsBetweenTheSectionsAndAtTheEnd() {
        Extended language = new Extended();
        List<TestQuestionRequest> requests = language.gaugeQuestionRequests();

        assertThat(language.between).containsExactly("reading>other");
        assertThat(requests).hasSize(22);
        assertThat(requests.get(5).getSection()).isEqualTo("listening");
        assertThat(requests.get(5).getNotes()).isEqualTo("about sounds");
        assertThat(requests.get(6).getSection()).isEqualTo("other");
        assertThat(requests.get(21).getSection()).isEqualTo("final");
        assertThat(requests).extracting(TestQuestionRequest::getNumber).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 22).boxed().toList());
    }
}
