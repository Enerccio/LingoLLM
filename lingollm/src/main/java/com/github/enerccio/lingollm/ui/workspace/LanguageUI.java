package com.github.enerccio.lingollm.ui.workspace;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.collections.Languages;

public interface LanguageUI extends WorkspaceComponent {

    String getIcon();
    Language getLanguage();
    void setLanguage(Language language);

    interface LanguageConstructor {

        LanguageUI createLanguage(Languages language, Workspace workspace);

    }

}
