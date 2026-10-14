package com.github.enerccio.lingollm.extensions;

import com.github.enerccio.lingollm.ui.main.ApplicationEntryPoint;

/**
 * Implemented by a {@link LingoLLMExtension} that has styles of its own. A plugin can't add to the application's
 * frontend bundle, so the application injects the CSS into the page of every UI as a {@code <style>} element while the
 * extension is loaded: into the pages that are open when the extension is loaded, into the pages opened later, and the
 * element is removed again when the extension is unloaded. See {@code CssRegistry}.
 */
public interface ExtensionHasCss {

    /**
     * The CSS text for a page, {@code null} or blank when the extension has no styles there. Called once for every
     * page (entry point) when the extension is loaded or when the page is opened, so it should return a cached
     * string. The entry point is the root component of the page, e.g. {@code Main} (the workspace) or {@code Viewer}.
     */
    String getCss(ApplicationEntryPoint entryPoint);

}
