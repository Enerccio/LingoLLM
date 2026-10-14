package com.github.enerccio.lingollm.extensions.gemma4toolsfix;

import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Gemma4ToolsFixActivator implements BundleActivator {

    private ServiceRegistration<LingoLLMExtension> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        registration = context.registerService(LingoLLMExtension.class, new Gemma4ToolsFixExtension(), null);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }
}
