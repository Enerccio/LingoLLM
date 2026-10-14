package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OwnedCrudContract} plus persistence of {@code @ExtendedAttribute} fields and {@link ExtendableEntity#getAttributes()}.
 * Subclasses put at least one extended attribute into {@link #newEntity()} and {@link #modify}, so the inherited
 * create/update tests check those fields after a reload from the database.
 */
public abstract class ExtendableCrudContract<T extends ExtendableEntity> extends OwnedCrudContract<T> {

    private static JsonObject storedJson(ExtendableEntity entity) {
        return JsonParser.parseString(new String(entity.getExtendedContent(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void attributesArePersisted() throws Exception {
        JsonObject nested = new JsonObject();
        nested.addProperty("inner", "value");
        JsonArray list = new JsonArray();
        list.add(1);
        list.add("two");

        T entity = newEntity();
        entity.getAttributes().addProperty("plugin.string", "text ✓");
        entity.getAttributes().addProperty("plugin.number", 12.5);
        entity.getAttributes().addProperty("plugin.flag", true);
        entity.getAttributes().add("plugin.nested", nested);
        entity.getAttributes().add("plugin.list", list);
        entity.getAttributes().add("plugin.null", JsonNull.INSTANCE);
        T saved = service().save(entity);

        T loaded = reload(saved);
        assertThat(loaded.getAttributes()).isEqualTo(entity.getAttributes());
        assertThat(storedJson(loaded).get("attributes_plugin.string").getAsString()).isEqualTo("text ✓");
        assertCreated(loaded);
    }

    @Test
    void attributesCanBeChangedAndRemoved() throws Exception {
        T entity = newEntity();
        entity.getAttributes().addProperty("keep", 1);
        entity.getAttributes().addProperty("change", "old");
        entity.getAttributes().addProperty("remove", "gone");
        T saved = service().save(entity);

        T loaded = reload(saved);
        loaded.getAttributes().addProperty("change", "new");
        loaded.getAttributes().remove("remove");
        loaded.getAttributes().addProperty("add", false);
        service().save(loaded);

        JsonObject attributes = reload(saved).getAttributes();
        assertThat(attributes.keySet()).containsExactlyInAnyOrder("keep", "change", "add");
        assertThat(attributes.get("change").getAsString()).isEqualTo("new");
        assertThat(storedJson(reload(saved)).has("attributes_remove")).isFalse();
    }

    @Test
    void savedInstanceHasDeserializedAttributes() throws Exception {
        T entity = newEntity();
        entity.getAttributes().addProperty("key", "value");

        T saved = service().save(entity);

        assertThat(saved.getAttributes().get("key").getAsString()).isEqualTo("value");
        assertCreated(saved);
    }

    @Test
    void attributesSurviveUnrelatedUpdate() throws Exception {
        T entity = newEntity();
        entity.getAttributes().addProperty("key", "value");
        T saved = service().save(entity);

        T loaded = reload(saved);
        modify(loaded);
        service().save(loaded);

        T reloaded = reload(saved);
        assertThat(reloaded.getAttributes().get("key").getAsString()).isEqualTo("value");
        assertModified(reloaded);
    }

    @Test
    void saveWithoutEventStoresRawExtendedContent() throws Exception {
        // backup restore writes extendedContent directly, fields are populated from it, not the other way around
        T entity = newEntity();
        entity.getAttributes().addProperty("ignored", "field value is not serialized");
        entity.setExtendedContent("{\"attributes_restored\": \"from content\"}".getBytes(StandardCharsets.UTF_8));

        T saved = service().saveWithoutEvent(entity);

        assertThat(saved.getAttributes().keySet()).containsExactly("restored");
        T loaded = reload(saved);
        assertThat(loaded.getAttributes().keySet()).containsExactly("restored");
        assertThat(loaded.getAttributes().get("restored").getAsString()).isEqualTo("from content");
    }
}
