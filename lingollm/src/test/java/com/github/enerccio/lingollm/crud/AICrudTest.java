package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.AIType;
import com.github.enerccio.lingollm.domain.collections.ReasoningEffort;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.test.InferenceCollector;
import com.github.enerccio.lingollm.test.llm.MockLLMScenario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AICrudTest extends ExtendableCrudContract<AI> {

    @Override
    protected OwnedService<AI, ?> service() {
        return aiService;
    }

    @Override
    protected AI newEntity() {
        OpenAICompatible ai = new OpenAICompatible();
        ai.setAiType(AIType.OPEN_AI_COMPATIBLE);
        ai.setName("Local model");
        ai.setUri(llm.baseUrl());
        ai.setApiKey("sk-test");
        ai.setModel("model-a");
        ai.setModelName("Model A");
        ai.setNeedsJailbreak(true);
        ai.setJailbreak("jailbreak text");
        ai.setEnabledReasoning(true);
        ai.setReasoningEffort(ReasoningEffort.HIGH);
        ai.setMaxContext(8192);
        ai.setMaxCompletionTokens(512);
        ai.getAdditionalParameters().addProperty("top_k", 40);
        return ai;
    }

    @Override
    protected void assertCreated(AI loaded) {
        assertThat(loaded).isInstanceOf(OpenAICompatible.class);
        OpenAICompatible ai = (OpenAICompatible) loaded;
        assertThat(ai.getAiType()).isEqualTo(AIType.OPEN_AI_COMPATIBLE);
        assertThat(ai.getName()).isEqualTo("Local model");
        assertThat(ai.getUri()).isEqualTo(llm.baseUrl());
        assertThat(ai.getApiKey()).isEqualTo("sk-test");
        assertThat(ai.getModel()).isEqualTo("model-a");
        assertThat(ai.getModelName()).isEqualTo("Model A");
        assertThat(ai.getNeedsJailbreak()).isTrue();
        assertThat(ai.getJailbreak()).isEqualTo("jailbreak text");
        assertThat(ai.getEnabledReasoning()).isTrue();
        assertThat(ai.getReasoningEffort()).isEqualTo(ReasoningEffort.HIGH);
        assertThat(ai.getMaxContext()).isEqualTo(8192);
        assertThat(ai.getMaxCompletionTokens()).isEqualTo(512);
        assertThat(ai.getAdditionalParameters().get("top_k").getAsInt()).isEqualTo(40);
    }

    @Override
    protected void modify(AI entity) {
        OpenAICompatible ai = (OpenAICompatible) entity;
        ai.setName("Renamed");
        ai.setModel("model-b");
        ai.setReasoningEffort(ReasoningEffort.LOW);
        ai.getAdditionalParameters().addProperty("min_p", 0.05);
        ai.getAdditionalParameters().remove("top_k");
    }

    @Override
    protected void assertModified(AI loaded) {
        OpenAICompatible ai = (OpenAICompatible) loaded;
        assertThat(ai.getName()).isEqualTo("Renamed");
        assertThat(ai.getModel()).isEqualTo("model-b");
        assertThat(ai.getReasoningEffort()).isEqualTo(ReasoningEffort.LOW);
        assertThat(ai.getAdditionalParameters().keySet()).containsExactly("min_p");
        assertThat(ai.getUri()).isEqualTo(llm.baseUrl());
    }

    @Test
    void streamsWithUnsetReasoningFlags() throws Exception {
        OpenAICompatible ai = (OpenAICompatible) newEntity();
        ai.setEnabledReasoning(null);
        ai.setNeedsJailbreak(null);
        ai.setModel(MockLLMScenario.DEFAULT_MODEL);
        ai = (OpenAICompatible) aiService.save(ai);
        llm.reply("ok");

        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(ai).stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
        assertThat(collector.getResponse()).isEqualTo("ok");
        assertThat(llm.lastCompletionRequest().getBody().has("reasoning_effort")).isFalse();
    }

    @Test
    void sendsReasoningEffortWhenEnabled() throws Exception {
        AI ai = aiService.save(newEntity());
        llm.reply("ok");

        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(ai).stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
        assertThat(llm.lastCompletionRequest().getBody().get("reasoning_effort").getAsString()).isEqualTo("high");
        assertThat(llm.lastCompletionRequest().getModel()).isEqualTo("model-a");
        assertThat(llm.lastCompletionRequest().getApiKey()).isEqualTo("sk-test");
    }

    /**
     * Unlike other entities an AI can't be saved by anybody but its owner (an AI shared by an administrator is
     * readable by everybody), so the owner stays by refusing the save.
     */
    @Override
    @Test
    void savingKeepsExistingOwner() throws Exception {
        AI saved = create();
        login();

        AI loaded = reload(saved);
        modify(loaded);

        assertThatThrownBy(() -> service().save(loaded)).isInstanceOf(SecurityException.class);
        assertThat(reload(saved).getOwner().getId()).isEqualTo(owner.getId());
        assertThat(reload(saved).getName()).isEqualTo(saved.getName());
    }
}
