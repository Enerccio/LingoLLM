package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.collections.ResetStrategy;
import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.components.LimitStatus;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.AILimitService;
import com.github.enerccio.lingollm.domain.service.InferenceException;
import com.github.enerccio.lingollm.domain.service.InferenceException.Type;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.test.InferenceCollector;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limits an administrator sets for the users of a shared AI: stored in the AI, counted in the usages, lazily reset.
 */
class AILimitServiceTest extends LingoLLMTestBase {

    @Autowired
    private AILimitService limitService;

    @Autowired
    private UsageService usageService;

    private User admin;
    private OpenAICompatible shared;

    private void sharedAi() throws Exception {
        admin = loginAdmin();
        shared = createAI();
        shared.setShared(true);
        shared = (OpenAICompatible) aiService.save(shared);
    }

    private static AILimit tokens(Long prefill, Long generated, Long total, ResetStrategy reset) {
        AILimit limit = new AILimit();
        limit.setStrategy(LimitStrategy.TOKEN_LIMIT);
        limit.setResetStrategy(reset);
        limit.setPrefillTokens(prefill);
        limit.setGeneratedTokens(generated);
        limit.setTotalTokens(total);
        return limit;
    }

    /**
     * The administrator sets the limit, then the user is logged in again.
     */
    private void limit(User user, AILimit limit) throws Exception {
        loginAs(admin);
        limitService.setLimit(shared, user, limit);
        loginAs(user);
    }

    private void usedLongAgo(AI ai, int days) throws Exception {
        Usage usage = usageService.findForUser(ai);
        usage.setLastUsed(Date.from(LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant()));
        usageService.save(usage);
    }

    @Test
    void userWithoutALimitIsNotLimited() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());

        LimitStatus status = limitService.getStatus(shared);
        assertThat(status.limit().getStrategy()).isEqualTo(LimitStrategy.UNLIMITED);
        assertThat(status.isBlocked()).isFalse();
        usageService.record(shared, 1_000_000, 1_000_000);
        limitService.check(shared);
        assertThat(usageService.findForUser(shared).getPeriodPrefillTokens()).as("not counted without a token limit").isZero();
        assertThat(reader).isNotNull();
    }

    @Test
    void limitsAreStoredInTheAiUnderTheUserUuid() throws Exception {
        sharedAi();
        User reader = createUser();
        limitService.setLimit(shared, reader, tokens(100L, null, 500L, ResetStrategy.WEEKLY));

        AI stored = aiService.find(shared.getId());
        assertThat(stored.getLimits()).contains(reader.getUuid());

        List<AILimitService.Entry> entries = limitService.findLimits(shared);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().user().getId()).isEqualTo(reader.getId());
        AILimit limit = entries.getFirst().limit();
        assertThat(limit.getStrategy()).isEqualTo(LimitStrategy.TOKEN_LIMIT);
        assertThat(limit.getResetStrategy()).isEqualTo(ResetStrategy.WEEKLY);
        assertThat(limit.getPrefillTokens()).isEqualTo(100);
        assertThat(limit.getGeneratedTokens()).isNull();
        assertThat(limit.getTotalTokens()).isEqualTo(500);

        limitService.removeLimit(shared, reader);
        assertThat(limitService.findLimits(shared)).isEmpty();
        assertThat(aiService.find(shared.getId()).getLimits()).isNull();
    }

    @Test
    void settingALimitAgainReplacesIt() throws Exception {
        sharedAi();
        User reader = createUser();
        limitService.setLimit(shared, reader, tokens(1L, null, null, ResetStrategy.DAILY));
        limitService.setLimit(shared, reader, tokens(2L, null, null, ResetStrategy.DAILY));

        assertThat(limitService.findLimits(shared)).hasSize(1);
        assertThat(limitService.findLimits(shared).getFirst().limit().getPrefillTokens()).isEqualTo(2);
    }

    @Test
    void onlyTheAdministratorWhoOwnsTheAiCanSetLimits() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());

        assertThatThrownBy(() -> limitService.setLimit(shared, reader, tokens(1L, null, null, ResetStrategy.DAILY)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> limitService.findLimits(shared)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> limitService.removeLimit(shared, reader)).isInstanceOf(SecurityException.class);

        loginAdmin();
        assertThatThrownBy(() -> limitService.setLimit(shared, reader, tokens(1L, null, null, ResetStrategy.DAILY)))
                .as("another administrator").isInstanceOf(SecurityException.class);
    }

    @Test
    void limitsAreForSharedAisOnly() throws Exception {
        admin = loginAdmin();
        OpenAICompatible own = createAI();
        User reader = createUser();

        assertThatThrownBy(() -> limitService.setLimit(own, reader, tokens(1L, null, null, ResetStrategy.DAILY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTotalThatCanNeverBeReachedIsRejected() throws Exception {
        sharedAi();
        User reader = createUser();

        assertThat(tokens(20_000L, 10_000L, 100_000L, ResetStrategy.DAILY).validate()).isEqualTo(AILimit.Problem.TOTAL_UNREACHABLE);
        assertThat(tokens(100_000L, 20_000L, 100_000L, ResetStrategy.DAILY).validate()).isNull();
        assertThat(tokens(60L, 40L, 100L, ResetStrategy.DAILY).validate()).as("exactly the total").isNull();
        assertThat(tokens(20_000L, null, 100_000L, ResetStrategy.DAILY).validate()).as("not all three").isNull();
        assertThat(tokens(60L, 50L, null, ResetStrategy.DAILY).validate()).as("no total").isNull();
        assertThat(tokens(-1L, null, null, ResetStrategy.DAILY).validate()).isEqualTo(AILimit.Problem.NEGATIVE);
        assertThatThrownBy(() -> limitService.setLimit(shared, reader, tokens(20L, 10L, 100L, ResetStrategy.DAILY)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(limitService.findLimits(shared)).isEmpty();
    }

    @Test
    void theLimitReachedFirstStopsTheUser() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(100L, null, 120L, ResetStrategy.DAILY));

        usageService.record(shared, 90, 20);
        LimitStatus status = limitService.getStatus(shared);
        assertThat(status.usedPrefill()).isEqualTo(90);
        assertThat(status.usedGenerated()).isEqualTo(20);
        assertThat(status.resetsAt()).isAfter(new Date());
        assertThat(status.block()).isNull();
        limitService.check(shared);

        usageService.record(shared, 5, 20);
        assertThat(limitService.getStatus(shared).block()).as("sent 95 of 100, total 135 of 120")
                .isEqualTo(LimitStatus.Block.TOTAL);
        assertThatThrownBy(() -> limitService.check(shared)).isInstanceOf(InferenceException.class)
                .extracting("type").isEqualTo(Type.LIMIT_REACHED);
    }

    @Test
    void aRequestThatWouldGoOverTheSentOrTotalLimitIsRefused() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(100L, null, 150L, ResetStrategy.DAILY));
        usageService.record(shared, 60, 30);

        limitService.check(shared, 40);
        assertThatThrownBy(() -> limitService.check(shared, 41)).as("sent 60 + 41 is over 100")
                .isInstanceOf(InferenceException.class).extracting("type").isEqualTo(Type.LIMIT_REACHED);

        usageService.record(shared, 0, 30);
        // total 120 of 150: sent 60 + 31 = 91 of 100 would be fine, the total is not
        limitService.check(shared, 30);
        assertThatThrownBy(() -> limitService.check(shared, 31)).as("total 120 + 31 is over 150")
                .isInstanceOf(InferenceException.class);
    }

    @Test
    void theGeneratedLimitIsReachedNotLookedAheadAndCanBeGoneOver() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(null, 10L, null, ResetStrategy.DAILY));
        usageService.record(shared, 0, 9);

        limitService.check(shared, 1_000_000);

        usageService.record(shared, 0, 20);
        assertThat(limitService.getStatus(shared).usedGenerated()).as("the last response went over").isEqualTo(29);
        assertThatThrownBy(() -> limitService.check(shared, 0)).isInstanceOf(InferenceException.class);
    }

    @Test
    void aHardGeneratedLimitRefusesARequestWhoseReservedResponseDoesNotFit() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        AILimit hard = tokens(null, 100L, null, ResetStrategy.DAILY);
        hard.setHardGeneratedLimit(true);
        limit(reader, hard);
        usageService.record(shared, 10, 30);

        limitService.check(shared, 0, 70);
        assertThatThrownBy(() -> limitService.check(shared, 0, 71)).as("30 used + 71 reserved is over 100")
                .isInstanceOf(InferenceException.class).extracting("type").isEqualTo(Type.LIMIT_REACHED);
        assertThat(limitService.getLimit(shared).isHardGeneratedLimit()).isTrue();

        limit(reader, tokens(null, 100L, null, ResetStrategy.DAILY));
        limitService.check(shared, 0, 10_000);
    }

    @Test
    void aHardGeneratedLimitUsesTheResponseSizeOfTheRequest() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        // createAI() allows responses of 1000 tokens
        AILimit hard = tokens(null, 500L, null, ResetStrategy.DAILY);
        hard.setHardGeneratedLimit(true);
        limit(reader, hard);
        llm.enqueue(MockLLMResponse.text("never sent"));

        assertThatThrownBy(() -> inferenceServices.forAI(shared)
                .stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), new InferenceCollector()))
                .isInstanceOf(InferenceException.class).extracting("type").isEqualTo(Type.LIMIT_REACHED);
        assertThat(llm.getCompletionRequests()).isEmpty();

        hard.setGeneratedTokens(1000L);
        limit(reader, hard);
        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(shared).stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), collector);
        assertThat(collector.await(Duration.ofSeconds(30))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
    }

    @Test
    void aPromptThatDoesNotFitIsNotSentToTheProvider() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(5L, null, null, ResetStrategy.DAILY));
        llm.enqueue(MockLLMResponse.text("never sent"));

        assertThatThrownBy(() -> inferenceServices.forAI(shared).stream(
                List.of(LLMChatMessage.of(LLMRole.USER, "a prompt that is certainly longer than five tokens, ".repeat(10))),
                new InferenceCollector()))
                .isInstanceOf(InferenceException.class).extracting("type").isEqualTo(Type.LIMIT_REACHED);
        assertThat(llm.getCompletionRequests()).isEmpty();
        assertThat(usageService.findForUser(shared)).isNull();
    }

    @Test
    void everyTokenLimitCanBeLeftEmpty() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(null, 10L, null, ResetStrategy.DAILY));

        usageService.record(shared, 1_000_000, 9);
        limitService.check(shared);

        usageService.record(shared, 0, 1);
        assertThat(limitService.getStatus(shared).block()).isEqualTo(LimitStatus.Block.GENERATED);
    }

    @Test
    void limitsAreOfTheUserNotOfTheAi() throws Exception {
        sharedAi();
        User limited = loginAs(createUser());
        limit(limited, tokens(10L, null, null, ResetStrategy.DAILY));
        usageService.record(shared, 10, 0);
        assertThat(limitService.getStatus(shared).isBlocked()).isTrue();

        loginAs(createUser());
        usageService.record(shared, 1000, 0);
        assertThat(limitService.getStatus(shared).isBlocked()).isFalse();
        loginAs(admin);
        assertThat(limitService.getStatus(shared).isBlocked()).isFalse();
    }

    @Test
    void aUserWhoIsNotAvailableCanNotUseTheAi() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        AILimit limit = new AILimit();
        limit.setStrategy(LimitStrategy.NOT_AVAILABLE);
        limit(reader, limit);
        llm.enqueue(MockLLMResponse.text("never sent"));

        assertThatThrownBy(() -> limitService.check(shared)).isInstanceOf(InferenceException.class)
                .extracting("type").isEqualTo(Type.NOT_AVAILABLE);
        assertThatThrownBy(() -> inferenceServices.forAI(shared)
                .stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), new InferenceCollector()))
                .isInstanceOf(InferenceException.class);
        assertThat(llm.getCompletionRequests()).isEmpty();
    }

    @Test
    void requestsAreRefusedUntilTheLimitIsRaised() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(null, null, 15L, ResetStrategy.DAILY));
        llm.enqueue(MockLLMResponse.text("one").usage(10, 5));
        InferenceCollector first = new InferenceCollector();
        inferenceServices.forAI(shared).stream(List.of(LLMChatMessage.of(LLMRole.USER, "hi")), first);
        assertThat(first.await(Duration.ofSeconds(30))).isEqualTo(InferenceCollector.Outcome.COMPLETED);

        assertThatThrownBy(() -> inferenceServices.forAI(shared)
                .stream(List.of(LLMChatMessage.of(LLMRole.USER, "again")), new InferenceCollector()))
                .isInstanceOf(InferenceException.class).extracting("type").isEqualTo(Type.LIMIT_REACHED);
        assertThat(llm.getCompletionRequests()).hasSize(1);

        limit(reader, tokens(null, null, 100L, ResetStrategy.DAILY));
        llm.enqueue(MockLLMResponse.text("two").usage(10, 5));
        InferenceCollector second = new InferenceCollector();
        inferenceServices.forAI(shared).stream(List.of(LLMChatMessage.of(LLMRole.USER, "again")), second);
        assertThat(second.await(Duration.ofSeconds(30))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
    }

    @Test
    void usedTokensAreTakenAsZeroInANewPeriod() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(null, null, 100L, ResetStrategy.DAILY));
        usageService.record(shared, 100, 0);
        assertThat(limitService.getStatus(shared).isBlocked()).isTrue();

        usedLongAgo(shared, 2);

        LimitStatus status = limitService.getStatus(shared);
        assertThat(status.usedPrefill()).isZero();
        assertThat(status.isBlocked()).isFalse();
        limitService.check(shared);
        assertThat(usageService.findForUser(shared).getPeriodPrefillTokens()).as("reset when the next request is counted").isEqualTo(100);

        usageService.record(shared, 30, 4);

        Usage usage = usageService.findForUser(shared);
        assertThat(usage.getPeriodPrefillTokens()).isEqualTo(30);
        assertThat(usage.getPeriodGeneratedTokens()).isEqualTo(4);
        assertThat(usage.getPrefillTokens()).as("all time counters are not reset").isEqualTo(130);
        assertThat(limitService.getStatus(shared).usedTotal()).isEqualTo(34);
    }

    @Test
    void usedTokensOfTheSamePeriodAreKept() throws Exception {
        sharedAi();
        User reader = loginAs(createUser());
        limit(reader, tokens(null, null, 100L, ResetStrategy.MONTHLY));
        usageService.record(shared, 10, 1);
        usedLongAgo(shared, 0);
        usageService.record(shared, 20, 2);

        assertThat(limitService.getStatus(shared).usedTotal()).isEqualTo(33);
    }

    @Test
    void periodsAreCalendarDaysWeeksAndMonths() {
        ZoneId zone = ZoneId.systemDefault();
        // Wednesday
        Date now = Date.from(LocalDateTime.of(2026, 10, 14, 15, 30).atZone(zone).toInstant());

        assertThat(ResetStrategy.DAILY.periodStart(now)).isEqualTo(at(LocalDate.of(2026, 10, 14)));
        assertThat(ResetStrategy.DAILY.nextReset(now)).isEqualTo(at(LocalDate.of(2026, 10, 15)));
        assertThat(ResetStrategy.WEEKLY.periodStart(now)).isEqualTo(at(LocalDate.of(2026, 10, 12)));
        assertThat(ResetStrategy.WEEKLY.nextReset(now)).isEqualTo(at(LocalDate.of(2026, 10, 19)));
        assertThat(ResetStrategy.MONTHLY.periodStart(now)).isEqualTo(at(LocalDate.of(2026, 10, 1)));
        assertThat(ResetStrategy.MONTHLY.nextReset(now)).isEqualTo(at(LocalDate.of(2026, 11, 1)));
    }

    private static Date at(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
