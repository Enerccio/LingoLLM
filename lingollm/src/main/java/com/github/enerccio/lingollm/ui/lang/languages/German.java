package com.github.enerccio.lingollm.ui.lang.languages;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.ui.workspace.LanguageUIBase;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Qualifier;

@Configurable
public class German extends LanguageUIBase {

    @Autowired
    @Qualifier("germanLanguageDefinitionService")
    protected LanguageDefinitionService definition;

    public German(Languages language, Workspace workspace) {
        super(language, workspace);
    }

}
