package com.github.enerccio.lingollm.ui.lang.languages;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.ui.workspace.LanguageUIBase;
import com.github.enerccio.lingollm.ui.workspace.Workspace;

public class Japanese extends LanguageUIBase {

    public Japanese(Languages language, Workspace workspace) {
        super(language, workspace);
        assert language == Languages.JAPANESE;
    }

}
