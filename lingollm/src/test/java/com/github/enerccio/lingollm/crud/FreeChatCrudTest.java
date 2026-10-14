package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FreeChatCrudTest extends ExtendableCrudContract<FreeChat> {

    @Autowired
    private FreeChatService freeChatService;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private Language currentLanguage;

    private Language profile() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        language.setNameOverride(uniqueName("german"));
        return languageService.save(language);
    }

    @Override
    protected OwnedService<FreeChat, ?> service() {
        return freeChatService;
    }

    @Override
    protected FreeChat newEntity() throws Exception {
        FreeChat chat = new FreeChat();
        chat.setLanguage(profile());
        chat.setRootPrompt("root");
        chat.setPrompt("prompt");
        return chat;
    }

    @Override
    protected void assertCreated(FreeChat loaded) {
        assertThat(loaded.getRootPrompt()).isEqualTo("root");
        assertThat(loaded.getPrompt()).isEqualTo("prompt");
        assertThat(loaded.getLanguage()).isNotNull();
    }

    @Override
    protected void modify(FreeChat entity) {
        entity.setPrompt("changed");
    }

    @Override
    protected void assertModified(FreeChat loaded) {
        assertThat(loaded.getPrompt()).isEqualTo("changed");
        assertThat(loaded.getRootPrompt()).isEqualTo("root");
    }

    @Test
    void newChatBelongsToTheLanguageOfTheSession() throws Exception {
        Language german = profile();
        currentLanguage.setId(german.getId());

        FreeChat chat = freeChatService.save(new FreeChat());

        assertThat(chat.getLanguage().getId()).isEqualTo(german.getId());
        assertThat(freeChatService.findAllForUser(german)).extracting(FreeChat::getId).containsExactly(chat.getId());
    }

    @Test
    void languageIsKeptWhenSet() throws Exception {
        Language german = profile();
        Language other = profile();
        currentLanguage.setId(other.getId());

        FreeChat chat = new FreeChat();
        chat.setLanguage(german);
        chat = freeChatService.save(chat);

        assertThat(chat.getLanguage().getId()).isEqualTo(german.getId());
    }

    @Test
    void chatsAreListedPerLanguage() throws Exception {
        Language german = profile();
        Language other = profile();
        FreeChat inGerman = new FreeChat();
        inGerman.setLanguage(german);
        inGerman = freeChatService.save(inGerman);
        FreeChat inOther = new FreeChat();
        inOther.setLanguage(other);
        inOther = freeChatService.save(inOther);

        assertThat(freeChatService.findAllIdsForUser(german)).containsExactly(inGerman.getId());
        assertThat(freeChatService.findAllIdsForUser(other)).containsExactly(inOther.getId());
    }
}
