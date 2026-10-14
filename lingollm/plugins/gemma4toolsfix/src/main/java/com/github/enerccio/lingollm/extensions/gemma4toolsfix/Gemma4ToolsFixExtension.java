package com.github.enerccio.lingollm.extensions.gemma4toolsfix;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.service.InferenceServices.ToolRoleResolver;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.Locale;

/**
 * Some APIs of Gemma 4 expect the results of function calls in messages with the role {@code tool_responses} instead
 * of {@code tool}. Port of the SillyTavern extension SillyTavern-Gemma4-Tools-Fix: when the model of the AI is a
 * Gemma 4, the role of the tool messages is changed.
 */
@Configurable
public class Gemma4ToolsFixExtension implements LingoLLMExtension, ToolRoleResolver {

    static final String TOOL_ROLE = "tool_responses";

    /** The logger of this extension, its lines go to the extension log (Admin → Extensions → Log). */
    private Logger log;

    @Autowired
    private InferenceServices inferenceServices;

    @Override
    public void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        log = parentService.createLogger(bundle);
        inferenceServices.addToolRoleResolver(this);
        log.info("Tool messages of Gemma 4 models are sent with the role {}", TOOL_ROLE);
    }

    @Override
    public void onExtensionUnload(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        inferenceServices.removeToolRoleResolver(this);
    }

    @Override
    public String toolRole(AI ai) {
        return ai instanceof OpenAICompatible compatible && isGemma4(compatible.getModel()) ? TOOL_ROLE : null;
    }

    static boolean isGemma4(String model) {
        if (model == null) {
            return false;
        }
        String lower = model.toLowerCase(Locale.ROOT);
        return lower.contains("gemma4") || lower.contains("gemma-4");
    }

}
