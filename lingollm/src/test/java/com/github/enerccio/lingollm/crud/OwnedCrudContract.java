package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.model.BaseEntity;
import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD behaviour shared by every owned entity: identity on create, update, soft and hard delete, and owner isolation.
 * Subclasses create a valid entity, change it, and check the change survived a reload.
 */
public abstract class OwnedCrudContract<T extends OwnedEntity> extends LingoLLMTestBase {

    protected User owner;

    @BeforeEach
    void loginOwner() throws Exception {
        owner = login();
    }

    protected abstract OwnedService<T, ?> service();

    /**
     * New, unsaved, valid entity (referenced entities may be saved).
     */
    protected abstract T newEntity() throws Exception;

    /**
     * Checks fields set by {@link #newEntity()} after a reload.
     */
    protected abstract void assertCreated(T loaded) throws Exception;

    protected abstract void modify(T entity) throws Exception;

    protected abstract void assertModified(T loaded) throws Exception;

    protected T create() throws Exception {
        return service().save(newEntity());
    }

    protected T reload(T entity) throws Exception {
        return service().find(entity.getId());
    }

    protected static Long idOf(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    @Test
    void createAssignsIdentityAndOwner() throws Exception {
        Date before = new Date();
        T saved = create();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUuid()).hasSize(36);
        assertThat(saved.getCreation()).isAfterOrEqualTo(new Date(before.getTime() - 1));
        assertThat(saved.getModification()).isNotNull();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void createdEntityCanBeFound() throws Exception {
        T saved = create();

        T loaded = reload(saved);
        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getUuid()).isEqualTo(saved.getUuid());
        assertThat(loaded.getOwner().getId()).isEqualTo(owner.getId());
        assertCreated(loaded);

        assertThat(service().find(saved.getUuid())).isEqualTo(saved.getId());
        assertThat(idOf(service().findForUser(saved.getUuid()))).isEqualTo(saved.getId());
        assertThat(service().findAllForUser()).extracting(BaseEntity::getId).contains(saved.getId());
        assertThat(service().findAllIdsForUser()).contains(saved.getId());
        assertThat(service().findAllIds()).contains(saved.getId());
    }

    @Test
    void updateIsPersisted() throws Exception {
        T saved = create();
        Date created = saved.getCreation();
        Date modified = saved.getModification();
        Thread.sleep(5);

        T loaded = reload(saved);
        modify(loaded);
        T updated = service().save(loaded);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        T reloaded = reload(saved);
        assertModified(reloaded);
        assertThat(reloaded.getUuid()).isEqualTo(saved.getUuid());
        assertThat(reloaded.getCreation()).hasSameTimeAs(created);
        assertThat(reloaded.getModification()).isAfter(modified);
        assertThat(reloaded.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void softDeleteHidesEntity() throws Exception {
        T saved = create();

        service().delete(reload(saved), false);

        T loaded = reload(saved);
        assertThat(loaded).isNotNull();
        assertThat(loaded.isDeleted()).isTrue();
        assertThat(service().findForUser(saved.getUuid())).isNull();
        assertThat(service().findAllForUser()).extracting(BaseEntity::getId).doesNotContain(saved.getId());
        assertThat(service().findAllIdsForUser()).doesNotContain(saved.getId());
        assertThat(service().findAllIds()).doesNotContain(saved.getId());
        assertThat(service().findAll()).extracting(BaseEntity::getId).doesNotContain(saved.getId());
    }

    @Test
    void hardDeleteRemovesEntity() throws Exception {
        T saved = create();

        service().delete(reload(saved), true);

        assertThat(reload(saved)).isNull();
        assertThat(service().find(saved.getUuid())).isNull();
    }

    @Test
    void otherUsersCannotSeeEntity() throws Exception {
        T saved = create();
        User other = login();

        assertThat(service().findForUser(saved.getUuid())).isNull();
        assertThat(service().findAllForUser()).extracting(BaseEntity::getId).doesNotContain(saved.getId());
        assertThat(service().findAllIdsForUser()).doesNotContain(saved.getId());
        assertThat(service().findAll(owner)).extracting(BaseEntity::getId).contains(saved.getId());
        assertThat(service().findAll(other)).extracting(BaseEntity::getId).doesNotContain(saved.getId());
    }

    @Test
    void savingKeepsExistingOwner() throws Exception {
        T saved = create();
        login();

        T loaded = reload(saved);
        modify(loaded);
        service().save(loaded);

        assertThat(reload(saved).getOwner().getId()).isEqualTo(owner.getId());
    }
}
