package com.github.enerccio.lingollm.inference;

import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.service.dto.LLMAttachment;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.test.InferenceTestBase;
import com.github.enerccio.lingollm.test.InferenceCollector;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Media attached to a message are sent as content parts by the OpenAI compatible provider.
 */
class InferenceAttachmentsTest extends InferenceTestBase {

    @Autowired
    private InferenceServices inferenceServices;

    private JsonObject userMessage() {
        JsonArray messages = llm.lastCompletionRequest().getBody().getAsJsonArray("messages");
        return messages.get(messages.size() - 1).getAsJsonObject();
    }

    private void send(LLMChatMessage... messages) throws Exception {
        llm.enqueue(MockLLMResponse.text("seen"));
        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(ai).stream(List.of(messages), collector);
        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
    }

    @Test
    void messageWithoutAttachmentsKeepsPlainContent() throws Exception {
        send(LLMChatMessage.of(LLMRole.USER, "hello"));

        assertThat(userMessage().get("content").isJsonPrimitive()).isTrue();
        assertThat(userMessage().get("content").getAsString()).isEqualTo("hello");
    }

    @Test
    void imageIsSentAsDataUrlAfterText() throws Exception {
        send(LLMChatMessage.of(LLMRole.USER, "what is this")
                .addAttachment(LLMAttachment.of("image/png", new byte[]{1, 2, 3})));

        JsonArray parts = userMessage().getAsJsonArray("content");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getAsJsonObject().get("type").getAsString()).isEqualTo("text");
        assertThat(parts.get(0).getAsJsonObject().get("text").getAsString()).isEqualTo("what is this");
        JsonObject image = parts.get(1).getAsJsonObject();
        assertThat(image.get("type").getAsString()).isEqualTo("image_url");
        assertThat(image.getAsJsonObject("image_url").get("url").getAsString()).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    void imageUrlIsPassedAsIs() throws Exception {
        send(LLMChatMessage.of(LLMRole.USER, null)
                .addAttachment(LLMAttachment.ofUrl(LLMAttachment.Kind.IMAGE, "image/jpeg", "https://example.com/a.jpg")));

        JsonArray parts = userMessage().getAsJsonArray("content");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).getAsJsonObject().getAsJsonObject("image_url").get("url").getAsString())
                .isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void audioIsSentAsInputAudio() throws Exception {
        send(LLMChatMessage.of(LLMRole.USER, "transcribe")
                .addAttachment(LLMAttachment.of("audio/wav", new byte[]{1, 2, 3})));

        JsonObject audio = userMessage().getAsJsonArray("content").get(1).getAsJsonObject();
        assertThat(audio.get("type").getAsString()).isEqualTo("input_audio");
        assertThat(audio.getAsJsonObject("input_audio").get("data").getAsString()).isEqualTo("AQID");
        assertThat(audio.getAsJsonObject("input_audio").get("format").getAsString()).isEqualTo("wav");
    }

    @Test
    void fileIsSentAsFilePart() throws Exception {
        send(LLMChatMessage.of(LLMRole.USER, "summarize")
                .addAttachment(LLMAttachment.of(LLMAttachment.Kind.FILE, "application/pdf", "doc.pdf", new byte[]{1, 2, 3})));

        JsonObject file = userMessage().getAsJsonArray("content").get(1).getAsJsonObject();
        assertThat(file.get("type").getAsString()).isEqualTo("file");
        assertThat(file.getAsJsonObject("file").get("filename").getAsString()).isEqualTo("doc.pdf");
        assertThat(file.getAsJsonObject("file").get("file_data").getAsString())
                .isEqualTo("data:application/pdf;base64,AQID");
    }

    @Test
    void unsupportedAudioFormatIsRejected() {
        LLMChatMessage message = LLMChatMessage.of(LLMRole.USER, "x")
                .addAttachment(LLMAttachment.of("audio/ogg", new byte[]{1}));

        assertThatThrownBy(() -> inferenceServices.forAI(ai).stream(List.of(message), new InferenceCollector()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audio/ogg");
        assertThat(llm.getCompletionRequests()).isEmpty();
    }
}
