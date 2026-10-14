package com.github.enerccio.lingollm.cleanup;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.*;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.service.*;
import com.github.enerccio.lingollm.domain.service.CleanupService.EntityKey;
import com.github.enerccio.lingollm.domain.service.TrashService.DeleteResult;
import com.github.enerccio.lingollm.domain.service.TrashService.RestoreResult;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Soft delete cascade, trash and cleanup of the language profiles, chats and providers. The test database is shared
 * and purge works on all of it, so the tests only assert on what they created.
 */
class CleanupTrashTest extends LingoLLMTestBase {

    @Autowired
    private CleanupService cleanupService;

    @Autowired
    private TrashService trashService;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private FreeChatService chatService;

    @Autowired
    private FreeChatMessageService messageService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private UsageService usageService;

    @BeforeEach
    void setUp() throws Exception {
        loginAdmin();
    }

    private Language profile() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        language.setNameOverride(uniqueName("german"));
        return languageService.save(language);
    }

    private FreeChat chat(Language language) throws Exception {
        FreeChat chat = new FreeChat();
        chat.setLanguage(language);
        return chatService.save(chat);
    }

    private FreeChatMessage message(FreeChat chat, FreeChatMessage parent, String contents) throws Exception {
        FreeChatMessage message = new FreeChatMessage();
        message.setLanguage(chat.getLanguage());
        message.setParentChat(chat);
        message.setParent(parent);
        message.setContents(contents);
        return messageService.save(message);
    }

    private static EntityKey key(Class<?> type, com.github.enerccio.lingollm.domain.model.BaseEntity entity) {
        return new EntityKey(type, entity.getId());
    }

    @Test
    void onlyAdministratorsUseCleanup() throws Exception {
        login();

        assertThatThrownBy(() -> cleanupService.getReferenceModel()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> cleanupService.analyze()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> cleanupService.purge()).isInstanceOf(SecurityException.class);
    }

    @Test
    void deletingAProfileDeletesItsChatsAndMessages() throws Exception {
        Language language = profile();
        FreeChat chat = chat(language);
        FreeChatMessage first = message(chat, null, "one");
        FreeChatMessage second = message(chat, first, "two");

        languageService.delete(languageService.find(language.getId()), false);

        assertThat(chatService.findAllIdsForUser()).doesNotContain(chat.getId());
        assertThat(messageService.findAllIdsForUser()).doesNotContain(first.getId(), second.getId());
        assertThat(languageService.findAllIdsForUser()).doesNotContain(language.getId());
    }

    @Test
    void purgeRemovesTheProfileWithItsChatsAndMessages() throws Exception {
        Language language = profile();
        FreeChat chat = chat(language);
        FreeChatMessage first = message(chat, null, "one");
        FreeChatMessage second = message(chat, first, "two");
        languageService.delete(languageService.find(language.getId()), false);

        cleanupService.purge();

        assertThat(languageService.find(language.getId())).isNull();
        assertThat(chatService.find(chat.getId())).isNull();
        assertThat(messageService.find(first.getId())).isNull();
        assertThat(messageService.find(second.getId())).isNull();
    }

    @Test
    void purgeRemovesAProviderWithItsUsageAndClearsTheDefaultModel() throws Exception {
        OpenAICompatible ai = createAI();
        UserSetting setting = settingService.getOrCreate(UserSetting.class);
        setting.setDefaultModel(ai.getId());
        settingService.save(setting);
        usageService.record(ai, 10, 5);
        aiService.delete(aiService.find(ai.getId()), false);

        cleanupService.purge();

        assertThat(aiService.find(ai.getId())).isNull();
        assertThat(settingService.getOrCreate(UserSetting.class).getDefaultModel()).isNull();
    }

    @Test
    void aProviderAndTheLastLanguageOfASettingDoNotBlockThePurge() throws Exception {
        Language language = profile();
        UserSetting setting = settingService.getOrCreate(UserSetting.class);
        setting.setLastLanguage(language.getId());
        settingService.save(setting);
        languageService.delete(languageService.find(language.getId()), false);

        cleanupService.purge();

        assertThat(languageService.find(language.getId())).isNull();
        assertThat(settingService.getOrCreate(UserSetting.class).getLastLanguage()).isNull();
    }

    @Test
    void restoringAProfileBringsItsChatsBack() throws Exception {
        Language language = profile();
        FreeChat chat = chat(language);
        FreeChatMessage message = message(chat, null, "one");
        languageService.delete(languageService.find(language.getId()), false);

        RestoreResult result = trashService.restore(List.of(key(Language.class, language)));

        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getRestored()).contains(key(FreeChat.class, chat), key(FreeChatMessage.class, message));
        assertThat(languageService.findAllIdsForUser()).contains(language.getId());
        assertThat(messageService.findAllIdsForUser()).contains(message.getId());
    }

    @Test
    void aMessageCannotBeRestoredWithoutItsDeletedProfile() throws Exception {
        Language language = profile();
        FreeChat chat = chat(language);
        FreeChatMessage message = message(chat, null, "one");
        languageService.delete(languageService.find(language.getId()), false);

        RestoreResult result = trashService.restore(List.of(key(FreeChatMessage.class, message)));

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getRestored()).isEmpty();
        assertThat(messageService.findAllIdsForUser()).doesNotContain(message.getId());
    }

    @Test
    void permanentDeleteMovesTheChildrenToTheParent() throws Exception {
        Language language = profile();
        FreeChat chat = chat(language);
        FreeChatMessage first = message(chat, null, "one");
        FreeChatMessage second = message(chat, first, "two");
        FreeChatMessage third = message(chat, second, "three");
        messageService.delete(messageService.find(second.getId()), false);

        assertThat(trashService.getPermanentlyDeletableTypes()).containsExactly(FreeChatMessage.class);
        DeleteResult result = trashService.deletePermanently(List.of(key(FreeChatMessage.class, second)));

        assertThat(result.isBlocked()).isFalse();
        assertThat(messageService.find(second.getId())).isNull();
        assertThat(messageService.find(third.getId()).getParent().getId()).isEqualTo(first.getId());
    }

    @Test
    void onlyMessagesCanBeDeletedPermanently() throws Exception {
        Language language = profile();
        languageService.delete(languageService.find(language.getId()), false);

        assertThatThrownBy(() -> trashService.deletePermanently(List.of(key(Language.class, language))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usersSeeOnlyTheirOwnDeletedObjectsInTheTrash() throws Exception {
        Language language = profile();
        languageService.delete(languageService.find(language.getId()), false);
        login();

        assertThat(trashService.find(TrashService.TrashFilter.all(), 0, 1000))
                .extracting(item -> item.key().id()).doesNotContain(language.getId());
        assertThatThrownBy(() -> trashService.restore(List.of(key(Language.class, language))))
                .isInstanceOf(SecurityException.class);
    }
}
