package com.github.enerccio.lingollm.extensions;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import org.osgi.framework.Bundle;

public interface LingoLLMExtension {

    void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService);

    void onExtensionUnload(Bundle b, OsgiService parentService, ExtensionService extensionService);

}
