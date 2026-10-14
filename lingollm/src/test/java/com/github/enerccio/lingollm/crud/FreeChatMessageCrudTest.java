package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.ImageAttachment;
import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreeChatMessageCrudTest extends ExtendableCrudContract<FreeChatMessage> {

    @Autowired
    private FreeChatMessageService messageService;

    @Autowired
    private FreeChatService chatService;

    @Autowired
    private LanguageService languageService;

    private FreeChat chat() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        language = languageService.save(language);
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

    @Override
    protected OwnedService<FreeChatMessage, ?> service() {
        return messageService;
    }

    @Override
    protected FreeChatMessage newEntity() throws Exception {
        FreeChatMessage message = new FreeChatMessage();
        FreeChat chat = chat();
        message.setLanguage(chat.getLanguage());
        message.setParentChat(chat);
        message.setContents("hallo");
        message.setAttachments(List.of(new ImageAttachment("resource-uuid", "a cat")));
        return message;
    }

    @Override
    protected void assertCreated(FreeChatMessage loaded) {
        assertThat(loaded.getContents()).isEqualTo("hallo");
        assertThat(loaded.getParent()).isNull();
        assertThat(loaded.getAttachments()).containsExactly(new ImageAttachment("resource-uuid", "a cat"));
    }

    @Override
    protected void modify(FreeChatMessage entity) {
        entity.setContents("tschüss");
        entity.setAttachments(List.of());
    }

    @Override
    protected void assertModified(FreeChatMessage loaded) {
        assertThat(loaded.getContents()).isEqualTo("tschüss");
        assertThat(loaded.getAttachments()).isEmpty();
    }

    @Test
    void messagesFormAChainThroughTheirParents() throws Exception {
        FreeChat chat = chat();
        FreeChatMessage first = message(chat, null, "one");
        FreeChatMessage second = message(chat, first, "two");

        List<FreeChatMessage> messages = messageService.findByChat(chat);

        assertThat(messages).extracting(FreeChatMessage::getContents).containsExactly("one", "two");
        assertThat(messages.get(1).getParent().getId()).isEqualTo(first.getId());
        assertThat(second.getParentChat().getId()).isEqualTo(chat.getId());
    }

    @Test
    void messagesOfAChatOfSomebodyElseAreRefused() throws Exception {
        FreeChat chat = chat();
        login();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.findByChat(chat))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
