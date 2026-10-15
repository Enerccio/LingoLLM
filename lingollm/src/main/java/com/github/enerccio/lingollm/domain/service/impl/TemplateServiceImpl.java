package com.github.enerccio.lingollm.domain.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.enerccio.lingollm.domain.service.TemplateService;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.context.MethodValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TemplateServiceImpl implements TemplateService, InitializingBean {

    private static final Pattern ROOT_NAME = Pattern.compile("[./\\[]");

    private Handlebars handlebars;

    private final Cache<String, Template> templateCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(24))
            .build();

    @Override
    public ValidationResult isValidTemplate(String templateContent, String templateName) throws Exception {
        return isValidTemplate(templateContent, templateName, null);
    }

    @Override
    public ValidationResult isValidTemplate(String templateContent, String templateName, Collection<String> knownNames) throws Exception {
        if (templateContent == null || templateContent.isBlank()) {
            return new ValidationResult(false, "Template cannot be empty.");
        }

        try {
            Template template = compile(templateContent);
            List<String> unknownNames = knownNames == null ? List.of() : findUnknownNames(template, knownNames);
            return new ValidationResult(true, null, unknownNames);
        } catch (HandlebarsException e) {
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new ValidationResult(false, errorMsg);
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid template structure: " + e.getMessage());
        }
    }

    @Override
    public String processTemplate(String template, String templateName, Map<String, ?> values) throws Exception {
        if (template == null) {
            return "";
        }

        Template compiled;
        try {
            compiled = compile(template);
        } catch (HandlebarsException e) {
            throw new IllegalArgumentException("The template " + templateName + " is not valid: " + message(e), e);
        }

        Context context = Context.newBuilder(values == null ? Map.of() : values)
                .resolver(MapValueResolver.INSTANCE, JavaBeanValueResolver.INSTANCE, MethodValueResolver.INSTANCE)
                .build();
        try {
            return compiled.apply(context);
        } catch (HandlebarsException e) {
            throw new IllegalArgumentException("The template " + templateName + " can not be filled in: " + message(e), e);
        } finally {
            context.destroy();
        }
    }

    /**
     * Names of variables and sections that are not among the known ones. Checked statically, so names in branches that
     * are not rendered are found too. Paths are checked by their first segment, names relative to the current context
     * ({{this}}, {{.}}, {{../x}}, {{@index}}) are skipped since they depend on the data being iterated.
     */
    private List<String> findUnknownNames(Template template, Collection<String> knownNames) throws Exception {
        Set<String> unknown = new LinkedHashSet<>();
        for (String name : template.collect(TagType.VAR, TagType.TRIPLE_VAR, TagType.SECTION)) {
            String root = rootName(name);
            if (root == null || handlebars.helper(root) != null || knownNames.contains(root)) {
                continue;
            }
            unknown.add(name);
        }
        return new ArrayList<>(unknown);
    }

    private static String rootName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.strip();
        if (n.isEmpty() || n.startsWith(".") || n.startsWith("@") || n.equals("this") || n.startsWith("this.") || n.startsWith("this/")) {
            return null;
        }
        String root = ROOT_NAME.split(n, 2)[0];
        return root.isEmpty() ? null : root;
    }

    private Template compile(String template) throws IOException {
        try {
            return templateCache.get(template, key -> {
                try {
                    return handlebars.compileInline(key);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static String message(Exception e) {
        return e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
    }

    @Override
    public void afterPropertiesSet() {
        // templates produce LLM prompts, not HTML - never escape quotes, ampersands etc.
        handlebars = new Handlebars(new ClassPathTemplateLoader()).with(EscapingStrategy.NOOP);
    }
}
