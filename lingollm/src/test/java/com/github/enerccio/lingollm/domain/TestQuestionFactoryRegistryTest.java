package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionFactoryRegistry;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.SingleChoiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuestionFactoryRegistryTest extends LingoLLMTestBase {

    @Autowired
    private TestQuestionFactoryRegistry registry;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private LanguageService languageService;

    @BeforeEach
    void loginUser() throws Exception {
        login();
    }

    private static LanguageString text(String raw) {
        return LanguageString.of(raw);
    }

    /** Not known to the application until it is registered. */
    public static class ExtensionFactory implements TestQuestionFactory {
        @Override
        public TestQuestionHandler<?> createHandler(Test test, TestQuestion testQuestion) {
            return null;
        }
    }

    @org.junit.jupiter.api.Test
    void aBuiltInFactoryIsLoadedByItsClassNameAndCached() {
        TestQuestionFactory factory = registry.getFactory(SingleChoiceFactory.class.getName());

        assertThat(factory).isInstanceOf(SingleChoiceFactory.class);
        assertThat(registry.getFactory(SingleChoiceFactory.class.getName())).isSameAs(factory);
    }

    @org.junit.jupiter.api.Test
    void unknownBlankAndForeignClassesGiveNoFactory() {
        assertThat(registry.getFactory("no.such.Factory")).isNull();
        assertThat(registry.getFactory((String) null)).isNull();
        assertThat(registry.getFactory(" ")).isNull();
        // exists, but is not a factory: never instantiated
        assertThat(registry.getFactory(java.util.ArrayList.class.getName())).isNull();
        assertThat(registry.getFactory(new TestQuestion())).isNull();
    }

    @org.junit.jupiter.api.Test
    void registeredFactoriesCanBeReplacedAndRemoved() {
        ExtensionFactory factory = new ExtensionFactory();

        registry.register(factory);

        assertThat(registry.getFactory(ExtensionFactory.class.getName())).isSameAs(factory);
        assertThat(registry.getFactories()).contains(factory);

        registry.unregister(ExtensionFactory.class);

        assertThat(registry.getFactories()).doesNotContain(factory);
    }

    @org.junit.jupiter.api.Test
    void singleChoiceHandlerKeepsItsDataInTheQuestion() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        Test test = new Test();
        test.setLanguage(languageService.save(language));
        test = testService.save(test);
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setTestQuestionFactory(SingleChoiceFactory.class.getName());
        question = questionService.save(question);

        TestQuestionHandler<?> handler = registry.getFactory(question).createHandler(test, question);
        assertThat(handler).isInstanceOf(SingleChoiceHandler.class);
        handler.setInstructions(text("Pick the verb"));
        ((SingleChoiceHandler<?>) handler).setChoices(List.of(text("hon"), text("taberu")), 1);
        handler.save();

        TestQuestion loaded = questionService.find(question.getId());
        assertThat(loaded.getTestQuestionFactory()).isEqualTo(SingleChoiceFactory.class.getName());
        String data = loaded.getAttributes().get("$testData").toString();
        assertThat(data).contains("Pick the verb").contains("taberu").contains("\"correctAnswer\":1");
    }
}
