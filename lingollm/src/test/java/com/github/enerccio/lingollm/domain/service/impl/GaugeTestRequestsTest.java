package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.collections.CEFR;
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
    void twentyOneQuestionsSixOfThemAboutATextInOneRun() {
        List<TestQuestionRequest> requests = new Plain().gaugeQuestionRequests();

        assertThat(requests).hasSize(21);
        assertThat(requests).extracting(TestQuestionRequest::getNumber).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 21).boxed().toList());
        assertThat(requests.subList(0, 6)).extracting(TestQuestionRequest::getSection).containsOnly("reading");
        assertThat(requests.subList(6, 21)).extracting(TestQuestionRequest::getSection).containsOnly("other");
        assertThat(requests.subList(0, 6)).extracting(TestQuestionRequest::getType)
                .allMatch(type -> type.startsWith("ReadingComprehension"))
                .containsExactly("ReadingComprehensionWithSingleChoiceHandler", "ReadingComprehensionWithMultipleChoicesHandler",
                        "ReadingComprehensionWithSingleChoiceHandler", "ReadingComprehensionWithMultipleChoicesHandler",
                        "ReadingComprehensionWithSingleChoiceHandler", "ReadingComprehensionWithMultipleChoicesHandler");
        // the others are a mix of all the other kinds
        assertThat(requests.subList(6, 21)).extracting(TestQuestionRequest::getType).containsOnly(
                "SingleChoiceHandler", "MultipleChoiceHandler", "WritingQuestionHandler", "WordOrderQuestionHandler")
                .contains("WritingQuestionHandler", "WordOrderQuestionHandler", "MultipleChoiceHandler");
    }

    @Test
    void everySectionCoversAllTheLevelsEasiestFirst() {
        List<TestQuestionRequest> requests = new Plain().gaugeQuestionRequests();

        // one of each level for the text questions
        assertThat(requests.subList(0, 6)).extracting(TestQuestionRequest::getLevel).containsExactly(CEFR.values());
        // 15 questions over 6 levels: two or three of each
        List<CEFR> other = requests.subList(6, 21).stream().map(TestQuestionRequest::getLevel).toList();
        assertThat(other).isSorted();
        for (CEFR level : CEFR.values()) {
            assertThat(other.stream().filter(level::equals).count()).isBetween(2L, 3L);
        }
    }

    @Test
    void levelsAreSpreadEvenlyEasiestFirst() {
        assertThat(BaseLanguageDefinitionService.spreadLevels(1)).containsExactly(CEFR.B1);
        assertThat(BaseLanguageDefinitionService.spreadLevels(2)).containsExactly(CEFR.A1, CEFR.C2);
        assertThat(BaseLanguageDefinitionService.spreadLevels(12)).containsExactly(
                CEFR.A1, CEFR.A1, CEFR.A2, CEFR.A2, CEFR.B1, CEFR.B1, CEFR.B2, CEFR.B2, CEFR.C1, CEFR.C1, CEFR.C2, CEFR.C2);
        assertThat(BaseLanguageDefinitionService.spreadLevels(0)).isEmpty();
    }

    @Test
    void aLanguageCanAddQuestionsBetweenTheSectionsAndAtTheEnd() {
        Extended language = new Extended();
        List<TestQuestionRequest> requests = language.gaugeQuestionRequests();

        assertThat(language.between).containsExactly("reading>other");
        assertThat(requests).hasSize(23);
        assertThat(requests.get(6).getSection()).isEqualTo("listening");
        assertThat(requests.get(6).getNotes()).isEqualTo("about sounds");
        assertThat(requests.get(7).getSection()).isEqualTo("other");
        assertThat(requests.get(22).getSection()).isEqualTo("final");
        assertThat(requests).extracting(TestQuestionRequest::getNumber).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 23).boxed().toList());
        // what a language adds without a level gets one
        assertThat(requests).extracting(TestQuestionRequest::getLevel).doesNotContainNull();
    }
}
