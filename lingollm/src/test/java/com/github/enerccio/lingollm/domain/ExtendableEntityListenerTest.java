package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.listener.ExtendableEntityListener;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.model.ImageAttachment;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.ui.lang.LanguageDefaults;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serialization of {@code @ExtendedAttribute} fields and injected objects into {@code extendedContent}, without
 * database.
 */
class ExtendableEntityListenerTest {

    private final ExtendableEntityListener listener = new ExtendableEntityListener();

    private static JsonObject stored(ExtendableEntity entity) {
        return JsonParser.parseString(new String(entity.getExtendedContent(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void setStored(ExtendableEntity entity, String json) {
        entity.setExtendedContent(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTripsStringsWithSpecialCharacters() throws Exception {
        Language language = new Language();
        language.setNote("Write \"more\"\nwith ünïcödé ✓");
        LanguageDefaults defaults = new LanguageDefaults();
        defaults.setGaugeTestPrompt("Test \"{{language}}\"\nlevel");
        language.setDefaults(defaults);

        listener.serialize(language);

        Language loaded = new Language();
        loaded.setExtendedContent(language.getExtendedContent());
        listener.deserialize(loaded);
        assertThat(loaded.getNote()).isEqualTo("Write \"more\"\nwith ünïcödé ✓");
        assertThat(loaded.getDefaults().getGaugeTestPrompt()).isEqualTo("Test \"{{language}}\"\nlevel");
        assertThat(loaded.getDefaults().getFreeChatTemplate()).isNull();
    }

    @Test
    void nullFieldsAreNotStored() throws Exception {
        Language language = new Language();
        language.setNote("note");

        listener.serialize(language);

        assertThat(stored(language).has("note")).isTrue();
        assertThat(stored(language).has("defaults")).isFalse();
    }

    @Test
    void languageSpecificDataIsStoredWithPrefix() throws Exception {
        Language language = new Language();
        language.getLanguageSpecificData().addProperty("kanaMode", "hiragana");
        language.getLanguageSpecificData().addProperty("level", 3);

        listener.serialize(language);

        JsonObject stored = stored(language);
        assertThat(stored.get("languageSpecificData_kanaMode").getAsString()).isEqualTo("hiragana");
        assertThat(stored.get("languageSpecificData_level").getAsInt()).isEqualTo(3);
        assertThat(stored.has("kanaMode")).isFalse();
    }

    @Test
    void languageSpecificDataKeepsJsonStructureAndIsKeptApartFromAttributes() throws Exception {
        Language language = new Language();
        JsonObject nested = new JsonObject();
        nested.addProperty("known", 12);
        language.getLanguageSpecificData().add("progress", nested);
        language.getAttributes().addProperty("plugin", "value");

        listener.serialize(language);

        Language loaded = new Language();
        loaded.setExtendedContent(language.getExtendedContent());
        listener.deserialize(loaded);
        assertThat(loaded.getLanguageSpecificData().getAsJsonObject("progress").get("known").getAsInt()).isEqualTo(12);
        assertThat(loaded.getLanguageSpecificData().has("plugin")).isFalse();
        assertThat(loaded.getAttributes().get("plugin").getAsString()).isEqualTo("value");
        assertThat(loaded.getAttributes().has("progress")).isFalse();
    }

    @Test
    void deserializeReplacesInjectedObject() throws Exception {
        Language language = new Language();
        language.getLanguageSpecificData().addProperty("stale", true);
        setStored(language, "{\"languageSpecificData_fresh\": \"yes\"}");

        listener.deserialize(language);

        assertThat(language.getLanguageSpecificData().has("stale")).isFalse();
        assertThat(language.getLanguageSpecificData().get("fresh").getAsString()).isEqualTo("yes");
    }

    @Test
    void emptyContentLeavesEntityUntouched() throws Exception {
        Language language = new Language();
        language.setNote("kept");

        listener.deserialize(language);

        assertThat(language.getNote()).isEqualTo("kept");
        assertThat(language.getLanguageSpecificData().size()).isZero();
    }

    @Test
    void attachmentsOfAMessageAreLooseJson() throws Exception {
        FreeChatMessage message = new FreeChatMessage();
        message.setAttachments(List.of(new ImageAttachment("uuid-1", "caption"), new ImageAttachment("uuid-2", null)));

        listener.serialize(message);

        FreeChatMessage loaded = new FreeChatMessage();
        loaded.setExtendedContent(message.getExtendedContent());
        listener.deserialize(loaded);
        assertThat(loaded.getAttachments()).containsExactly(new ImageAttachment("uuid-1", "caption"),
                new ImageAttachment("uuid-2", null));
        assertThat(new FreeChatMessage().getAttachments()).isEmpty();
    }

    public static class Address {
        String city;
        List<String> lines = new ArrayList<>();
    }

    public static class Person {
        String name;
        int age;
        Address home;
        List<Address> pastAddresses = new ArrayList<>();
        Map<String, List<Integer>> scores = new LinkedHashMap<>();
    }

    public static class PojoEntity extends ExtendableEntity {
        @ExtendedAttribute(inject = true, injectPrefix = "person")
        private Person person;

        @ExtendedAttribute(inject = true, injectPrefix = "tags")
        private List<Address> tags;

        @ExtendedAttribute(inject = true, injectPrefix = "missing")
        private Person missing = new Person();
    }

    private static Address address(String city, String... lines) {
        Address a = new Address();
        a.city = city;
        a.lines.addAll(List.of(lines));
        return a;
    }

    @Test
    void injectedPojoWithNestedObjectsAndListsRoundTrips() throws Exception {
        Person person = new Person();
        person.name = "Ann";
        person.age = 33;
        person.home = address("Oslo", "Street 1", "Flat 2");
        person.pastAddresses.add(address("Bergen"));
        person.pastAddresses.add(address("Tromso", "Pier 9"));
        person.scores.put("math", List.of(1, 2, 3));

        PojoEntity entity = new PojoEntity();
        entity.person = person;
        entity.tags = List.of(address("A", "x"), address("B"));

        listener.serialize(entity);

        // object is flattened by top-level property, nested parts stay structured
        JsonObject root = stored(entity);
        assertThat(root.get("person_name").getAsString()).isEqualTo("Ann");
        assertThat(root.get("person_age").getAsInt()).isEqualTo(33);
        assertThat(root.get("person_home").getAsJsonObject().get("lines").getAsJsonArray()).hasSize(2);
        assertThat(root.get("person_pastAddresses").getAsJsonArray()).hasSize(2);
        assertThat(root.get("person_scores").getAsJsonObject().get("math").getAsJsonArray()).hasSize(3);
        // a list cannot be flattened: stored under the plain prefix
        assertThat(root.get("tags").getAsJsonArray()).hasSize(2);
        assertThat(root.has("missing_age")).isTrue();

        PojoEntity loaded = new PojoEntity();
        loaded.setExtendedContent(entity.getExtendedContent());
        listener.deserialize(loaded);

        assertThat(loaded.person).usingRecursiveComparison().isEqualTo(person);
        assertThat(loaded.person.home.lines).containsExactly("Street 1", "Flat 2");
        assertThat(loaded.person.pastAddresses).extracting(a -> a.city).containsExactly("Bergen", "Tromso");
        assertThat(loaded.person.scores.get("math")).containsExactly(1, 2, 3);
        assertThat(loaded.tags).usingRecursiveComparison().isEqualTo(entity.tags);
    }

    @Test
    void injectedPojoMissingFromContentKeepsDefault() throws Exception {
        PojoEntity entity = new PojoEntity();
        Person kept = entity.missing;
        setStored(entity, "{\"unrelated\": 1}");

        listener.deserialize(entity);

        assertThat(entity.person).isNull();
        assertThat(entity.tags).isNull();
        assertThat(entity.missing).isSameAs(kept);
    }
}
