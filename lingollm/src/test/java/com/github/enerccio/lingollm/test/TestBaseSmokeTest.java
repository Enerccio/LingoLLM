package com.github.enerccio.lingollm.test;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.security.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the test infrastructure itself: context, isolated database, session user and the mock LLM wiring.
 */
class TestBaseSmokeTest extends LingoLLMTestBase {

    @Autowired
    private Configuration configuration;

    @Test
    void usesIsolatedApplicationFolder() {
        File home = new File(System.getProperty("user.home"), ".lingollm");
        assertThat(configuration.getFolder().getAbsoluteFile()).isNotEqualTo(home.getAbsoluteFile());
        assertThat(configuration.getDatabaseFile()).exists();
        assertThat(configuration.getDatabaseFile().toPath()).startsWith(configuration.getFolder().toPath());
    }

    @Test
    void createsAndLogsInUser() throws Exception {
        User user = login();

        assertThat(currentUser.getId()).isEqualTo(user.getId());
        assertThat(userService.authenticate(user.getLogin(), DEFAULT_PASSWORD)).isTrue();
        assertThat(userService.authenticate(user.getLogin(), "wrong")).isFalse();
    }

    @Test
    void createdAIIsOwnedByCurrentUser() throws Exception {
        User user = login();
        OpenAICompatible ai = createAI();

        assertThat(ai.getOwner().getId()).isEqualTo(user.getId());
        assertThat(aiService.findAll()).extracting(AI::getId).contains(ai.getId());
    }

    @Test
    void listsModelsOfMockLLM() throws Exception {
        login();
        llm.models("alpha", "beta");

        assertThat(inferenceServices.forAI(createAI()).getModels()).containsExactly("alpha", "beta");
    }
}
