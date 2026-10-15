package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.github.enerccio.lingollm.ui.lang.LanguageDefaults;
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

    /**
     * What the user changed of the defaults of the language ({@code Languages.defaults}): a text that is not set (null
     * or blank) is taken from the defaults of the language, see {@code LanguageService}. Injected, so its keys are stored
     * as {@code defaults_*} attributes of the extended content.
     */
    @ExtendedAttribute(inject = true, injectPrefix = "defaults")
    @Transient
    private LanguageDefaults defaults = new LanguageDefaults();

    /** The level the user says they have in this language. */
    @ExtendedAttribute
    @Transient
    private CEFR userGrade;

    /** Free text on top of {@link #userGrade}: what the user can and cannot do, strong and weak points. */
    @ExtendedAttribute
    @Transient
    private String userGradeFineDetails;

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

    public LanguageDefaults getDefaults() {
        return defaults;
    }

    public void setDefaults(LanguageDefaults defaults) {
        this.defaults = defaults == null ? new LanguageDefaults() : defaults;
    }

    public JsonObject getLanguageSpecificData() {
        return languageSpecificData;
    }

    public void setLanguageSpecificData(JsonObject languageSpecificData) {
        this.languageSpecificData = languageSpecificData;
    }

    public CEFR getUserGrade() {
        return userGrade;
    }

    public void setUserGrade(CEFR userGrade) {
        this.userGrade = userGrade;
    }

    public String getUserGradeFineDetails() {
        return userGradeFineDetails;
    }

    public void setUserGradeFineDetails(String userGradeFineDetails) {
        this.userGradeFineDetails = userGradeFineDetails;
    }
}
