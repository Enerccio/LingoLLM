package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Shown while the model writes a test: a progress bar, how many questions are written and how many are left, and a
 * button that stops the generation. It is modal, so nothing else can be used meanwhile, and can't be closed by the user
 * other than with the button; whoever opened it closes it when the generation ends.
 */
@Configurable
public class TestGenerationDialog extends Dialog {

    @Autowired
    private Localization loc;

    private final Runnable onStop;

    private ProgressBar progress;
    private Span counts;
    private Button stopButton;

    /**
     * @param onStop called when the user presses the button; the dialog is closed by the caller
     */
    public TestGenerationDialog(Runnable onStop) {
        this.onStop = onStop;
    }

    public void create() {
        setHeaderTitle(loc.getValue(L.LABEL_GENERATING_TEST));
        setModality(ModalityMode.STRICT);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setDraggable(false);
        setResizable(false);
        // never wider than the screen, a phone gets all of its width
        setWidth("420px");

        progress = new ProgressBar();
        progress.setIndeterminate(true);
        progress.addClassName(CssStyles.FULL_WIDTH);

        counts = new Span();
        counts.addClassNames(CssStyles.TEXT_CENTER, CssStyles.WHITE_SPACE_NORMAL);

        VerticalLayout content = new VerticalLayout(progress, counts);
        content.setPadding(false);
        content.setSpacing(true);
        content.setAlignItems(Alignment.STRETCH);
        add(content);

        stopButton = new Button(loc.getValue(L.LABEL_STOP_GENERATION), event -> {
            stopButton.setEnabled(false);
            onStop.run();
        });
        stopButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        getFooter().add(stopButton);
    }

    /**
     * @param done  questions written so far
     * @param total questions the test has
     */
    public void setProgress(int done, int total) {
        progress.setIndeterminate(false);
        progress.setMin(0);
        progress.setMax(Math.max(1, total));
        progress.setValue(done);
        counts.setText(String.format(loc.getValue(L.MSG_QUESTIONS_PROGRESS), done, total, Math.max(0, total - done)));
    }
}
