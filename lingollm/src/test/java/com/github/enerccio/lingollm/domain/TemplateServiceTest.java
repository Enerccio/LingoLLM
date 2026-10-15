package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.service.TemplateService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateServiceTest extends LingoLLMTestBase {

    @Autowired
    private TemplateService templates;

    @org.junit.jupiter.api.Test
    void fillsInVariables() throws Exception {
        assertThat(templates.processTemplate("Hello {{name}}, {{count}} questions", "t", Map.of("name", "Anna", "count", 20)))
                .isEqualTo("Hello Anna, 20 questions");
    }

    @org.junit.jupiter.api.Test
    void doesNotEscapeThePrompt() throws Exception {
        assertThat(templates.processTemplate("{{json}}", "t", Map.of("json", "{\"a\": \"b\" & <c>}"))).isEqualTo("{\"a\": \"b\" & <c>}");
    }

    @org.junit.jupiter.api.Test
    void supportsSectionsAndLists() throws Exception {
        String template = """
                {{#if details}}Details: {{details}}{{/if}}
                {{#each levels}}
                - {{name}}: {{description}}
                {{/each}}""";
        String without = templates.processTemplate(template, "t", Map.of("details", "",
                "levels", List.of(Map.of("name", "A1", "description", "basic"), Map.of("name", "A2", "description", "more"))));
        assertThat(without.lines().filter(line -> !line.isBlank())).containsExactly("- A1: basic", "- A2: more");
        assertThat(templates.processTemplate(template, "t", Map.of("details", "slow", "levels", List.of())))
                .startsWith("Details: slow");
    }

    @org.junit.jupiter.api.Test
    void emptyTemplateIsEmpty() throws Exception {
        assertThat(templates.processTemplate(null, "t", Map.of())).isEmpty();
        assertThat(templates.processTemplate("", "t", Map.of())).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void validatesTemplates() throws Exception {
        assertThat(templates.isValidTemplate("{{name}}", "t").isValid()).isTrue();
        assertThat(templates.isValidTemplate("{{#if x}}never closed", "t").isValid()).isFalse();
        assertThat(templates.isValidTemplate("{{#if x}}never closed", "t").errorMessage()).isNotBlank();
        assertThat(templates.isValidTemplate(" ", "t").isValid()).isFalse();
        assertThat(templates.isValidTemplate(null, "t").isValid()).isFalse();
        assertThatThrownBy(() -> templates.processTemplate("{{#each}}", "t", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void reportsUnknownNames() throws Exception {
        String template = "{{language}} {{languge}} {{#if userGrade}}{{userGrade.name}}{{/if}} {{#each levels}}{{this}} {{@index}} {{../language}}{{/each}} {{typo.deep}}";

        TemplateService.ValidationResult result = templates.isValidTemplate(template, "t", List.of("language", "userGrade", "levels"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.unknownNames()).containsExactly("languge", "typo.deep");
        // without names nothing is checked
        assertThat(templates.isValidTemplate(template, "t").unknownNames()).isEmpty();
        // an invalid template has nothing to report
        assertThat(templates.isValidTemplate("{{#if x}}", "t", List.of()).unknownNames()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void sameTemplateIsRenderedWithDifferentVariables() throws Exception {
        assertThat(templates.processTemplate("{{x}}", "t", Map.of("x", 1))).isEqualTo("1");
        assertThat(templates.processTemplate("{{x}}", "t", Map.of("x", 2))).isEqualTo("2");
    }
}
