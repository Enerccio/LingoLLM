package com.github.enerccio.lingollm.ui.main;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.domain.service.OsgiService.ExtensionListener;
import com.github.enerccio.lingollm.domain.service.OsgiService.LoadedExtension;
import com.github.enerccio.lingollm.extensions.ExtensionHasCss;
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.server.Command;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the registry puts into the page, with a UI that records the scripts and an {@link OsgiService} that only keeps
 * the listeners and the loaded extensions.
 */
class CssRegistryTest {

    private static class StyledExtension implements LingoLLMExtension, ExtensionHasCss {

        String css = "a { color: red; }";

        @Override
        public String getCss(ApplicationEntryPoint entryPoint) {
            return css;
        }

        @Override
        public void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        }

        @Override
        public void onExtensionUnload(Bundle b, OsgiService osgiService, ExtensionService extensionService) {
        }
    }

    private static class PlainExtension implements LingoLLMExtension {

        @Override
        public void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        }

        @Override
        public void onExtensionUnload(Bundle b, OsgiService osgiService, ExtensionService extensionService) {
        }
    }

    private static class RecordingUI extends UI {

        final List<String> scripts = new ArrayList<>();
        final List<Object[]> parameters = new ArrayList<>();

        private final Page page = new Page(this) {
            @Override
            public PendingJavaScriptResult executeJs(String expression, Object... params) {
                scripts.add(expression);
                parameters.add(params);
                return null;
            }
        };

        @Override
        public Page getPage() {
            return page;
        }

        @Override
        public Future<Void> access(Command command) {
            command.execute();
            return null;
        }
    }

    private static class FakeOsgi {

        final List<ExtensionListener> listeners = new ArrayList<>();
        final List<LoadedExtension> loaded = new ArrayList<>();

        final OsgiService service = (OsgiService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{OsgiService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addExtensionListener" -> listeners.add((ExtensionListener) args[0]);
                    case "removeExtensionListener" -> listeners.remove(args[0]);
                    case "getLoadedExtensions" -> new ArrayList<>(loaded);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private final ApplicationEntryPoint entryPoint = new ApplicationEntryPoint() {
    };

    @Test
    void extensionsLoadedBeforeTheAttachGetTheirStyle() {
        FakeOsgi osgi = new FakeOsgi();
        StyledExtension styled = new StyledExtension();
        osgi.loaded.add(new LoadedExtension(null, styled));
        osgi.loaded.add(new LoadedExtension(null, new PlainExtension()));
        RecordingUI ui = new RecordingUI();
        CssRegistry registry = new CssRegistry(entryPoint, osgi.service);

        registry.attach(ui);

        assertThat(ui.scripts).hasSize(1);
        assertThat(ui.parameters.getFirst()).containsExactly(CssRegistry.styleId(styled), "a { color: red; }");
        assertThat(osgi.listeners).containsExactly(registry);

        // the same UI again is not set up twice
        registry.attach(ui);
        assertThat(ui.scripts).hasSize(1);
        assertThat(osgi.listeners).hasSize(1);
    }

    @Test
    void extensionLoadedAndUnloadedWhileAttachedChangesThePage() {
        FakeOsgi osgi = new FakeOsgi();
        RecordingUI ui = new RecordingUI();
        CssRegistry registry = new CssRegistry(entryPoint, osgi.service);
        registry.attach(ui);
        StyledExtension styled = new StyledExtension();

        registry.extensionLoaded(null, styled);
        registry.extensionLoaded(null, new PlainExtension());
        assertThat(ui.scripts).hasSize(1);
        assertThat(ui.scripts.getFirst()).contains("createElement('style')");

        registry.extensionUnloaded(null, styled);
        registry.extensionUnloaded(null, new PlainExtension());
        assertThat(ui.scripts).hasSize(2);
        assertThat(ui.scripts.get(1)).contains(".remove()");
        assertThat(ui.parameters.get(1)).containsExactly(CssRegistry.styleId(styled));
    }

    @Test
    void blankCssRemovesTheStyleOfAnotherEntryPoint() {
        FakeOsgi osgi = new FakeOsgi();
        RecordingUI ui = new RecordingUI();
        CssRegistry registry = new CssRegistry(entryPoint, osgi.service);
        registry.attach(ui);
        StyledExtension styled = new StyledExtension();
        styled.css = null;

        registry.extensionLoaded(null, styled);

        assertThat(ui.scripts).hasSize(1).first().asString().contains(".remove()");
    }

    @Test
    void detachedRegistryDoesNothing() {
        FakeOsgi osgi = new FakeOsgi();
        RecordingUI ui = new RecordingUI();
        CssRegistry registry = new CssRegistry(entryPoint, osgi.service);
        registry.attach(ui);

        registry.detach();
        registry.extensionLoaded(null, new StyledExtension());

        assertThat(osgi.listeners).isEmpty();
        assertThat(ui.scripts).isEmpty();
    }

    @Test
    void failingExtensionDoesNotBreakTheRegistry() {
        FakeOsgi osgi = new FakeOsgi();
        RecordingUI ui = new RecordingUI();
        CssRegistry registry = new CssRegistry(entryPoint, osgi.service);
        registry.attach(ui);
        StyledExtension broken = new StyledExtension() {
            @Override
            public String getCss(ApplicationEntryPoint entryPoint) {
                throw new IllegalStateException("no css");
            }
        };

        registry.extensionLoaded(null, broken);
        registry.extensionLoaded(null, new StyledExtension());

        assertThat(ui.scripts).hasSize(1);
    }
}
