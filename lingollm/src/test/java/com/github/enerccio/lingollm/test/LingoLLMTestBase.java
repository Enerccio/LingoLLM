package com.github.enerccio.lingollm.test;

import com.github.enerccio.lingollm.domain.collections.AIType;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.test.llm.MockLLMScenario;
import com.github.enerccio.lingollm.test.llm.MockLLMServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import java.util.UUID;

/**
 * Base for tests running against the real Spring wiring (services, JPA, Flyway on SQLite), without OSGi and
 * {@code @Extendable} instrumentation, see {@link TestContextPostProcessor}.
 * <p>
 * The context is cached and shared by all test classes, so the database is shared too: create data with unique
 * names ({@link #uniqueName}) or annotate the class with {@code @DirtiesContext} to get a fresh database.
 * <p>
 * Each test method runs in its own mock HTTP request and session (session scoped beans such as the current
 * {@link User} start empty), and gets its own mock LLM scenario in {@link #llm}.
 */
@SpringJUnitWebConfig(locations = "classpath:META-INF/spring/test-application-config.xml")
public abstract class LingoLLMTestBase {

    public static final String DEFAULT_PASSWORD = "password";

    @Autowired
    protected UserService userService;

    @Autowired
    protected AIService aiService;

    @Autowired
    protected InferenceServices inferenceServices;

    /**
     * Session scoped current user, see {@link #loginAs}.
     */
    @Autowired
    protected User currentUser;

    /**
     * Mock LLM programmed by the test, use {@link #createAI()} for an AI pointing to it.
     */
    protected MockLLMScenario llm;

    @BeforeEach
    void setUpMockLLM() {
        llm = MockLLMServer.shared().scenario();
    }

    @AfterEach
    void tearDownMockLLM() {
        llm.close();
    }

    protected static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected User createUser(String login, String password, boolean admin) throws Exception {
        User user = new User();
        user.setLogin(login);
        user.setFullName(login);
        user.setAdmin(admin);
        user = userService.save(user);
        return userService.changePassword(user, password);
    }

    protected User createUser() throws Exception {
        return createUser(uniqueName("user"), DEFAULT_PASSWORD, false);
    }

    /**
     * Makes the user the current user of the test session, like a successful login in the UI.
     */
    protected User loginAs(User user) {
        currentUser.setId(user.getId());
        currentUser.setLogin(user.getLogin());
        currentUser.setFullName(user.getFullName());
        currentUser.setAdmin(user.isAdmin());
        return user;
    }

    /**
     * Creates a user and logs them in.
     */
    protected User login() throws Exception {
        return loginAs(createUser());
    }

    /**
     * Creates an administrator and logs them in, for the services that only administrators can use.
     */
    protected User loginAdmin() throws Exception {
        return loginAs(createUser(uniqueName("admin"), DEFAULT_PASSWORD, true));
    }

    /**
     * OpenAI compatible AI owned by the current user, pointing to the test's mock LLM scenario.
     */
    protected OpenAICompatible createAI() throws Exception {
        return createAI(llm);
    }

    protected OpenAICompatible createAI(MockLLMScenario scenario) throws Exception {
        OpenAICompatible ai = new OpenAICompatible();
        ai.setAiType(AIType.OPEN_AI_COMPATIBLE);
        ai.setName(uniqueName("mock-ai"));
        ai.setUri(scenario.baseUrl());
        ai.setApiKey("test-key");
        ai.setModel(MockLLMScenario.DEFAULT_MODEL);
        ai.setModelName(MockLLMScenario.DEFAULT_MODEL);
        ai.setNeedsJailbreak(false);
        ai.setEnabledReasoning(false);
        ai.setMaxContext(32000);
        ai.setMaxCompletionTokens(1000);
        return (OpenAICompatible) aiService.save(ai);
    }
}
