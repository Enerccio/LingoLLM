package com.github.enerccio.lingollm.domain.model.impl.settings;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
@DiscriminatorValue("UserSetting")
public class UserSetting extends Setting {

    @ExtendedAttribute
    @Transient
    @CleanupReference(value = Policy.WEAK, target = AI.class)
    private Long defaultModel;

    /**
     * The language profile the workspace was on when the user left, opened again at the next login. Null until the
     * user creates the first profile.
     */
    @ExtendedAttribute
    @Transient
    @CleanupReference(value = Policy.WEAK, target = Language.class)
    private Long lastLanguage;

    /**
     * Code of the language of the UI, {@code null} for the default one.
     */
    @ExtendedAttribute
    @Transient
    private String language;

    public Long getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(Long defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Long getLastLanguage() {
        return lastLanguage;
    }

    public void setLastLanguage(Long lastLanguage) {
        this.lastLanguage = lastLanguage;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

}
