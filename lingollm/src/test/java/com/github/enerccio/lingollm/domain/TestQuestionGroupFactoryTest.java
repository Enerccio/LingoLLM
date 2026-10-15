package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory;
import com.github.enerccio.lingollm.domain.factories.impl.PlainGroupFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingTextGroupFactory;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupFactoryRegistry;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.PlainGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.ReadingTextGroupHandler;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuestionGroupFactoryTest extends LingoLLMTestBase {

    @Autowired
    private TestQuestionGroupFactoryRegistry registry;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private LanguageService languageService;

    private Test test;

    @BeforeEach
    void testWithProfile() throws Exception {
        login();
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        Test t = new Test();
        t.setLanguage(languageService.save(language));
        test = testService.save(t);
    }

    private static LanguageString text(String raw) {
        LanguageString string = new LanguageString();
        string.setRawString(raw);
        return string;
    }

    private TestQuestionGroup group(Class<? extends TestQuestionGroupFactory> factory) throws Exception {
        TestQuestionGroup group = new TestQuestionGroup();
        group.setLanguage(test.getLanguage());
        group.setParentTest(test);
        group.setFactory(factory.getName());
        return groupService.save(group);
    }

    private JsonObject savedData(TestQuestionGroup group) throws Exception {
        return groupService.find(group.getId()).getAttributes().getAsJsonObject("$groupData");
    }

    /** Not known to the application until it is registered. */
    public static class ExtensionFactory implements TestQuestionGroupFactory {
        @Override
        public TestQuestionGroupHandler<?> createHandler(Test test, TestQuestionGroup group) {
            return null;
        }
    }

    @org.junit.jupiter.api.Test
    void aBuiltInFactoryIsLoadedByItsClassNameAndCached() {
        TestQuestionGroupFactory factory = registry.getFactory(PlainGroupFactory.class.getName());

        assertThat(factory).isInstanceOf(PlainGroupFactory.class);
        assertThat(registry.getFactory(PlainGroupFactory.class.getName())).isSameAs(factory);
    }

    @org.junit.jupiter.api.Test
    void unknownBlankAndForeignClassesGiveNoFactory() {
        assertThat(registry.getFactory("no.such.Factory")).isNull();
        assertThat(registry.getFactory((String) null)).isNull();
        assertThat(registry.getFactory(java.util.ArrayList.class.getName())).isNull();
        // a question factory is not a group factory
        assertThat(registry.getFactory(com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory.class.getName())).isNull();
        assertThat(registry.getFactory(new TestQuestionGroup())).isNull();
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
    void plainGroupKeepsItsInstructions() throws Exception {
        TestQuestionGroup group = group(PlainGroupFactory.class);

        TestQuestionGroupHandler<?> handler = registry.getFactory(group).createHandler(test, group);
        assertThat(handler).isInstanceOf(PlainGroupHandler.class);
        handler.setInstructions(text("Answer all"));
        handler.save();

        assertThat(savedData(group).getAsJsonObject("instructions").get("rawString").getAsString()).isEqualTo("Answer all");
    }

    @org.junit.jupiter.api.Test
    void readingTextGroupKeepsTheTextOnceForTheGroup() throws Exception {
        TestQuestionGroup group = group(ReadingTextGroupFactory.class);

        TestQuestionGroupHandler<?> created = registry.getFactory(group).createHandler(test, group);
        assertThat(created).isInstanceOf(ReadingTextGroupHandler.class);
        ReadingTextGroupHandler handler = (ReadingTextGroupHandler) created;
        handler.setInstructions(text("Read and answer"));
        handler.setText(text("Taro ate an apple."));
        handler.save();
        handler.setText(text("Taro ate a pear."));
        handler.save();

        JsonObject data = savedData(group);
        assertThat(data.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("Taro ate a pear.");
        assertThat(data.getAsJsonObject("instructions").get("rawString").getAsString()).isEqualTo("Read and answer");
    }
}
