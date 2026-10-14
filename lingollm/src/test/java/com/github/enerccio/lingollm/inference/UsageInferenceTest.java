package com.github.enerccio.lingollm.inference;

import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.test.InferenceCollector;
import com.github.enerccio.lingollm.test.InferenceTestBase;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tokens of a request are counted for the user who started it, on whichever thread it ends.
 */
class UsageInferenceTest extends InferenceTestBase {

    private static final List<LLMChatMessage> PAYLOAD = List.of(LLMChatMessage.of(LLMRole.USER, "hello there"));

    @Autowired
    private UsageService usageService;

    @Test
    void streamCountsWhatTheProviderReported() throws Exception {
        llm.enqueue(MockLLMResponse.text("answer").usage(120, 8));
        InferenceCollector collector = new InferenceCollector();

        inferenceServices.forAI(ai).stream(PAYLOAD, collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
        Usage usage = usageService.findForUser(ai);
        assertThat(usage.getPrefillTokens()).isEqualTo(120);
        assertThat(usage.getGeneratedTokens()).isEqualTo(8);
        assertThat(usage.getRequests()).isEqualTo(1);
    }

    @Test
    void streamEstimatesWhenTheProviderReportsNothing() throws Exception {
        llm.enqueue(MockLLMResponse.text("answer"));
        InferenceCollector collector = new InferenceCollector();

        inferenceServices.forAI(ai).stream(PAYLOAD, collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
        Usage usage = usageService.findForUser(ai);
        assertThat(usage.getPrefillTokens()).isPositive();
        assertThat(usage.getGeneratedTokens()).isPositive();
    }

    @Test
    void inferCountsToo() throws Exception {
        // the mock reports usage only to a streaming request that asks for it, so this one is estimated
        llm.enqueue(MockLLMResponse.text("answer"));
        CompletableFuture<String> done = new CompletableFuture<>();

        inferenceServices.forAI(ai).infer(PAYLOAD, new com.github.enerccio.lingollm.domain.service.InferenceService.InferenceResultCallback() {
            public void onResult(String text, com.github.enerccio.lingollm.domain.service.structured.SchemaCheck check) {
                done.complete(text);
            }

            public void onCancel() {
                done.cancel(false);
            }

            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        assertThat(done.get(10, TimeUnit.SECONDS)).isEqualTo("answer");
        Usage usage = usageService.findForUser(ai);
        assertThat(usage.getRequests()).isEqualTo(1);
        assertThat(usage.getPrefillTokens()).isPositive();
        assertThat(usage.getGeneratedTokens()).isPositive();
    }

    @Test
    void failedRequestIsNotCounted() throws Exception {
        llm.enqueue(MockLLMResponse.error(401, "bad key"));
        InferenceCollector collector = new InferenceCollector();

        inferenceServices.forAI(ai).stream(PAYLOAD, collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.ERROR);
        Usage usage = usageService.findForUser(ai);
        assertThat(usage == null || usage.getRequests() == 0).isTrue();
    }
}
