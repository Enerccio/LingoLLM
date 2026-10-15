package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.vaadin.flow.component.html.Div;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * A text to read, shown once for all the questions of the group.
 */
@Configurable
public class ReadingTextGroupHandler extends TestQuestionGroupHandler<ReadingTextGroupData> {

    public ReadingTextGroupHandler(Test test, TestQuestionGroup group) {
        super(test, group, ReadingTextGroupData.class, ReadingTextGroupData::new);
    }

    public void setText(LanguageString text) {
        ReadingTextGroupData data = getGroupData();
        data.setText(text);
        setGroupData(data);
    }

    @Override
    protected void renderContent(Div content) {
        LanguageString text = getGroupData().getText();
        if (text != null) {
            Div passage = new Div(new LanguageStringComponent(text));
            passage.addClassName(CssStyles.TEST_PASSAGE);
            content.add(passage);
        }
    }
}
