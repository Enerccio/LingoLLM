package com.github.enerccio.lingollm.ui.main;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.page.ColorScheme.Value;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.beans.factory.annotation.Configurable;

@Push
@PWA(name = "LingoLLM", shortName = "LingoLLM")
@StyleSheet(Lumo.STYLESHEET)
@ColorScheme(Value.DARK)
@BodySize(height = "100vh", width = "100vw")
@CssImport(value = "./styles/shared-styles.css")
@Configurable(preConstruction = true)
public class AppShellConfig implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("LingoLLM");
    }

}
