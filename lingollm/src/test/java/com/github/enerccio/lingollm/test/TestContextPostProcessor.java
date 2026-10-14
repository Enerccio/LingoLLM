package com.github.enerccio.lingollm.test;

import com.github.enerccio.lingollm.domain.service.impl.OsgiServiceImpl;
import com.github.enerccio.lingollm.instruct.RuntimeInstrumentationInitializer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Adapts the production Spring XML for tests:
 * <ul>
 *     <li>removes the OSGi framework and the {@code @Extendable} bytecode instrumentation</li>
 *     <li>points {@code Configuration} (and so the SQLite database, data and backup folders) to a fresh folder under
 *     {@code lingollm.test.home} (default {@code target/test-home}) instead of {@code ~/.lingollm}</li>
 * </ul>
 * Each Spring context gets its own folder, so a context dirtied with {@code @DirtiesContext} starts on an empty
 * database.
 */
public class TestContextPostProcessor implements BeanDefinitionRegistryPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(TestContextPostProcessor.class);

    public static final String TEST_HOME_PROPERTY = "lingollm.test.home";

    private static final Set<String> EXCLUDED_CLASSES = Set.of(
            OsgiServiceImpl.class.getName(),
            RuntimeInstrumentationInitializer.class.getName()
    );

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        for (String name : registry.getBeanDefinitionNames()) {
            BeanDefinition definition = registry.getBeanDefinition(name);
            if (EXCLUDED_CLASSES.contains(definition.getBeanClassName())) {
                registry.removeBeanDefinition(name);
            }
        }

        File folder = createTestFolder();
        log.info("Test application folder: {}", folder.getAbsolutePath());
        registry.getBeanDefinition("configuration").getPropertyValues().add("folder", folder);
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    private static File createTestFolder() {
        try {
            Path root = Path.of(System.getProperty(TEST_HOME_PROPERTY, "target/test-home"));
            Files.createDirectories(root);
            return Files.createTempDirectory(root, "ctx-").toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create test folder", e);
        }
    }
}
