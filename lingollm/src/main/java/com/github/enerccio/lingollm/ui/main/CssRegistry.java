package com.github.enerccio.lingollm.ui.main;

import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.domain.service.OsgiService.ExtensionListener;
import com.github.enerccio.lingollm.domain.service.OsgiService.LoadedExtension;
import com.github.enerccio.lingollm.extensions.ExtensionHasCss;
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import com.github.enerccio.lingollm.ui.JavascriptLibrary;
import com.vaadin.flow.component.UI;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Keeps the styles of loaded extensions ({@link ExtensionHasCss}) in the page of one entry point. Created by the
 * entry point; {@link #attach} adds the styles of the extensions that are loaded already and subscribes to
 * {@link OsgiService} to add the styles of extensions loaded later and to remove them when the extension is unloaded;
 * {@link #detach} unsubscribes.
 * <p>
 * Every extension has one {@code <style>} element in the page, with the id derived from its class. The element is
 * replaced when it exists, so attaching twice or two registries working on the same page (a navigation between entry
 * points) don't duplicate it. The elements stay in the page when the registry is detached: the next entry point of the
 * same page sets them again.
 */
public class CssRegistry implements ExtensionListener {
    private static final Logger log = LoggerFactory.getLogger(CssRegistry.class);

    private static final String STYLE_ID_PREFIX = "lingollm-extension-css-";

    private final ApplicationEntryPoint entryPoint;
    private final OsgiService osgiService;

    private UI ui;

    public CssRegistry(ApplicationEntryPoint entryPoint, OsgiService osgiService) {
        this.entryPoint = entryPoint;
        this.osgiService = osgiService;
    }

    /**
     * Starts working on the page of the UI; does nothing when it already does. The listener is registered before the
     * loaded extensions are read, so an extension loaded in between is handled twice, which is harmless.
     */
    public synchronized void attach(UI ui) {
        if (ui == null || this.ui == ui) {
            return;
        }
        this.ui = ui;
        osgiService.addExtensionListener(this);
        for (LoadedExtension loaded : osgiService.getLoadedExtensions()) {
            install(loaded.extension());
        }
    }

    public synchronized void detach() {
        osgiService.removeExtensionListener(this);
        ui = null;
    }

    @Override
    public void extensionLoaded(Bundle bundle, LingoLLMExtension extension) {
        install(extension);
    }

    @Override
    public void extensionUnloaded(Bundle bundle, LingoLLMExtension extension) {
        if (extension instanceof ExtensionHasCss) {
            remove(extension);
        }
    }

    private void install(LingoLLMExtension extension) {
        if (!(extension instanceof ExtensionHasCss hasCss)) {
            return;
        }
        String css;
        try {
            css = hasCss.getCss(entryPoint);
        } catch (Exception e) {
            log.error("Extension {} failed to provide its CSS: {}", extension.getClass().getName(), e.getMessage(), e);
            return;
        }
        if (StringUtils.isBlank(css)) {
            // no styles for this page; the page can still hold the ones set by another entry point
            remove(extension);
            return;
        }
        String id = styleId(extension);
        access(page -> JavascriptLibrary.setPageStyle(page.getPage(), id, css));
    }

    private void remove(LingoLLMExtension extension) {
        String id = styleId(extension);
        access(page -> JavascriptLibrary.removePageStyle(page.getPage(), id));
    }

    private void access(Consumer<UI> change) {
        UI current;
        synchronized (this) {
            current = ui;
        }
        if (current == null) {
            return;
        }
        try {
            // the events come from the thread that loads the extension, not from the session of the UI
            current.access(() -> change.accept(current));
        } catch (RuntimeException e) {
            // the UI is closed: its page is gone with the styles
            log.debug("UI is not available, CSS of an extension is not applied: {}", e.getMessage());
        }
    }

    static String styleId(LingoLLMExtension extension) {
        return STYLE_ID_PREFIX + extension.getClass().getName();
    }
}
