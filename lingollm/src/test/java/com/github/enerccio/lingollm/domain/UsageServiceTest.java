package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One usage per AI and user: a personal AI has one, a shared AI has one for everybody who uses it.
 */
class UsageServiceTest extends LingoLLMTestBase {

    @Autowired
    private UsageService usageService;

    @Test
    void unusedAiHasNoUsage() throws Exception {
        login();

        assertThat(usageService.findForUser(createAI())).isNull();
    }

    @Test
    void firstUseCreatesTheUsageAndLaterUsesAddToIt() throws Exception {
        User user = login();
        AI ai = createAI();

        usageService.record(ai, 100, 20);
        usageService.record(ai, 50, 5);

        Usage usage = usageService.findForUser(ai);
        assertThat(usage.getPrefillTokens()).isEqualTo(150);
        assertThat(usage.getGeneratedTokens()).isEqualTo(25);
        assertThat(usage.getRequests()).isEqualTo(2);
        assertThat(usage.getLastUsed()).isNotNull();
        assertThat(usage.getOwner().getId()).isEqualTo(user.getId());
        assertThat(usage.getAi().getId()).isEqualTo(ai.getId());
        assertThat(usageService.findAllForUser()).filteredOn(u -> u.getAi().getId().equals(ai.getId())).hasSize(1);
    }

    @Test
    void negativeCountsAreIgnored() throws Exception {
        login();
        AI ai = createAI();

        usageService.record(ai, -5, -7);

        assertThat(usageService.findForUser(ai).getPrefillTokens()).isZero();
        assertThat(usageService.findForUser(ai).getGeneratedTokens()).isZero();
    }

    @Test
    void sharedAiHasAUsagePerUser() throws Exception {
        User admin = loginAdmin();
        OpenAICompatible shared = createAI();
        shared.setShared(true);
        shared = (OpenAICompatible) aiService.save(shared);
        usageService.record(shared, 10, 1);

        User first = loginAs(createUser());
        usageService.record(shared, 200, 20);
        usageService.record(shared, 300, 30);

        User second = loginAs(createUser());
        usageService.record(shared, 4000, 400);

        assertThat(usageService.findForUser(shared).getPrefillTokens()).isEqualTo(4000);
        loginAs(first);
        assertThat(usageService.findForUser(shared).getPrefillTokens()).isEqualTo(500);
        assertThat(usageService.findForUser(shared).getGeneratedTokens()).isEqualTo(50);

        loginAs(admin);
        assertThat(usageService.findForUser(shared).getPrefillTokens()).isEqualTo(10);
        assertThat(usageService.findAllForAI(shared)).extracting(u -> u.getOwner().getId())
                .containsExactlyInAnyOrder(admin.getId(), first.getId(), second.getId());
    }

    @Test
    void whoUsesAnAiIsVisibleToItsOwnerAndAdministratorsOnly() throws Exception {
        User owner = login();
        AI ai = createAI();
        usageService.record(ai, 1, 1);

        assertThat(usageService.findAllForAI(ai)).hasSize(1);

        loginAs(createUser());
        assertThatThrownBy(() -> usageService.findAllForAI(ai)).isInstanceOf(SecurityException.class);

        loginAdmin();
        assertThat(usageService.findAllForAI(ai)).hasSize(1);
        assertThat(usageService.findAllForAI(ai).getFirst().getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void usageOfAllUsersIsForAdministratorsOnly() throws Exception {
        User first = login();
        usageService.record(createAI(), 1, 1);
        User second = loginAs(createUser());
        usageService.record(createAI(), 2, 2);

        assertThatThrownBy(() -> usageService.findAllUsages()).isInstanceOf(SecurityException.class);

        loginAdmin();
        assertThat(usageService.findAllUsages()).extracting(u -> u.getOwner().getId())
                .contains(first.getId(), second.getId());
    }

    @Test
    void usageWithoutAiIsIgnored() throws Exception {
        login();

        usageService.record(null, 1, 1);
        usageService.record(new OpenAICompatible(), 1, 1);
    }
}
