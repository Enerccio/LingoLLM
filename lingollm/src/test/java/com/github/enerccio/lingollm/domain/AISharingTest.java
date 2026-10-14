package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AIs shared by an administrator, and the application setting that stops users from creating their own.
 */
class AISharingTest extends LingoLLMTestBase {

    @AfterEach
    void allowUserAisAgain() throws Exception {
        // the application settings are shared by all tests
        loginAdmin();
        aiService.setUserCreationAllowed(true);
    }

    private OpenAICompatible shareNewAI() throws Exception {
        OpenAICompatible ai = createAI();
        ai.setShared(true);
        return (OpenAICompatible) aiService.save(ai);
    }

    @Test
    void administratorIsReadFromTheDatabaseNotFromTheSession() throws Exception {
        loginAdmin();
        // the UI's session user never has the flag filled in
        currentUser.setAdmin(false);
        assertThat(aiService.isAdministrator()).isTrue();

        loginAs(createUser());
        currentUser.setAdmin(true);
        assertThat(aiService.isAdministrator()).isFalse();
    }

    @Test
    void sharedAiIsVisibleToOtherUsersAndPrivateOneIsNot() throws Exception {
        User admin = loginAdmin();
        AI shared = shareNewAI();
        AI personal = createAI();

        loginAs(createUser());

        assertThat(aiService.findAllForUser()).extracting(AI::getId).contains(shared.getId()).doesNotContain(personal.getId());
        assertThat(aiService.findAllIdsForUser()).contains(shared.getId()).doesNotContain(personal.getId());
        assertThat(aiService.findForUser(shared.getUuid())).isNotNull();
        assertThat(aiService.findForUser(personal.getUuid())).isNull();
        assertThat(aiService.isOwnedByCurrentUser(aiService.findForUser(shared.getUuid()))).isFalse();

        loginAs(admin);
        assertThat(aiService.findAllForUser()).extracting(AI::getId).contains(shared.getId(), personal.getId());
        assertThat(aiService.isOwnedByCurrentUser(aiService.findForUser(shared.getUuid()))).isTrue();
    }

    @Test
    void deletedSharedAiIsNotListed() throws Exception {
        loginAdmin();
        AI shared = shareNewAI();
        aiService.delete(shared, false);

        loginAs(createUser());

        assertThat(aiService.findForUser(shared.getUuid())).isNull();
        assertThat(aiService.findAllForUser()).extracting(AI::getId).doesNotContain(shared.getId());
    }

    @Test
    void sharedAiCantBeChangedOrDeletedByOtherUsers() throws Exception {
        loginAdmin();
        AI shared = shareNewAI();

        loginAs(createUser());
        AI loaded = aiService.findForUser(shared.getUuid());
        loaded.setName("hijacked");

        assertThatThrownBy(() -> aiService.save(loaded)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> aiService.delete(loaded, false)).isInstanceOf(SecurityException.class);

        assertThat(aiService.find(shared.getId()).getName()).isEqualTo(shared.getName());
        assertThat(aiService.find(shared.getId()).isDeleted()).isFalse();
    }

    @Test
    void onlyAdministratorsCanShare() throws Exception {
        login();
        OpenAICompatible ai = createAI();
        ai.setShared(true);

        assertThatThrownBy(() -> aiService.save(ai)).isInstanceOf(SecurityException.class);

        OpenAICompatible fresh = new OpenAICompatible();
        fresh.setName(uniqueName("fresh"));
        fresh.setShared(true);
        assertThatThrownBy(() -> aiService.save(fresh)).isInstanceOf(SecurityException.class);
        assertThat(aiService.find(ai.getId()).isShared()).isFalse();
    }

    @Test
    void creatingAiCanBeDisabledForUsersButNotForAdministrators() throws Exception {
        User user = login();
        assertThat(aiService.isUserCreationAllowed()).isTrue();
        assertThat(aiService.canCreate()).isTrue();

        loginAdmin();
        aiService.setUserCreationAllowed(false);

        assertThat(aiService.isUserCreationAllowed()).isFalse();
        assertThat(aiService.canCreate()).as("administrators always can").isTrue();
        createAI();

        loginAs(user);
        assertThat(aiService.canCreate()).isFalse();
        assertThatThrownBy(this::createAI).isInstanceOf(SecurityException.class);

        loginAdmin();
        aiService.setUserCreationAllowed(true);
        loginAs(user);
        assertThat(aiService.canCreate()).isTrue();
        createAI();
    }

    @Test
    void disablingCreationKeepsTheExistingAisOfUsers() throws Exception {
        User user = login();
        OpenAICompatible own = createAI();

        loginAdmin();
        OpenAICompatible shared = shareNewAI();
        aiService.setUserCreationAllowed(false);

        loginAs(user);
        assertThat(aiService.findAllForUser()).extracting(AI::getId).contains(own.getId(), shared.getId());
        own.setName("renamed");
        assertThat(aiService.save(own).getName()).isEqualTo("renamed");
        aiService.delete(own, false);
    }

    @Test
    void onlyAdministratorsChangeTheSetting() throws Exception {
        login();

        assertThatThrownBy(() -> aiService.setUserCreationAllowed(false)).isInstanceOf(SecurityException.class);
        assertThat(aiService.isUserCreationAllowed()).isTrue();
    }
}
