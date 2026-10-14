package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.google.gson.JsonObject;
import jakarta.persistence.*;

/**
 * A language profile of a user: the language being learned (from a template, see {@link Languages}) with the name the
 * user gave it and its own settings. Everything a user learns is bound to a profile ({@link LanguageBoundEntity}).
 */
@Entity
@Table(name = "languages")
public class Language extends ExtendableEntity {

    @Enumerated(EnumType.STRING)
    private Languages language;

    @Lob
    private String nameOverride;

    @ExtendedAttribute
    @Transient
    private String note;

    /** JSON of the defaults the profile was created with ({@code LanguageDefaults}). */
    @ExtendedAttribute
    @Transient
    private String defaults;

    /**
     * Data only the {@link Languages language} knows (its own exercises, progress...). Injected, so its keys are
     * stored as {@code languageSpecificData_*} attributes of the extended content.
     */
    @ExtendedAttribute(inject = true, injectPrefix = "languageSpecificData")
    @Transient
    private JsonObject languageSpecificData = new JsonObject();

    public Languages getLanguage() {
        return language;
    }

    public void setLanguage(Languages language) {
        this.language = language;
    }

    public String getNameOverride() {
        return nameOverride;
    }

    public void setNameOverride(String nameOverride) {
        this.nameOverride = nameOverride;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getDefaults() {
        return defaults;
    }

    public void setDefaults(String defaults) {
        this.defaults = defaults;
    }

    public JsonObject getLanguageSpecificData() {
        return languageSpecificData;
    }

    public void setLanguageSpecificData(JsonObject languageSpecificData) {
        this.languageSpecificData = languageSpecificData;
    }
}
