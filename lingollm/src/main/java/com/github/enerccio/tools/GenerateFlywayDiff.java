package com.github.enerccio.tools;

import com.github.enerccio.lingollm.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.hibernate.jpa.boot.spi.PersistenceXmlParser;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenerateFlywayDiff {

    @SuppressWarnings("unchecked")
    static void main(String[] args) throws Exception {
        PersistenceXmlParser parser = PersistenceXmlParser.create();

        URL xmlUrl = new File("lingollm/src/main/webapp/config/persistence.xml").toURI().toURL();

        Map<String, PersistenceUnitDescriptor> persistenceUnits = parser.parse(List.of(xmlUrl));
        PersistenceUnitDescriptor puDescriptor = persistenceUnits.get("PU");

        if (puDescriptor == null) {
            throw new IllegalArgumentException("Persistence unit 'PU' not found in config/persistence.xml");
        }

        Map<String, Object> settings = new HashMap<>();
        if (puDescriptor.getProperties() != null) {
            settings.putAll((Map) puDescriptor.getProperties());
        }

        Configuration configuration = new Configuration();
        configuration.afterPropertiesSet();

        settings.put(AvailableSettings.DIALECT, "com.github.enerccio.lingollm.SaneSQLiteDialect");
        settings.put(AvailableSettings.JAKARTA_JDBC_URL, configuration.resolveDb("lingollm.sqlite"));
        settings.put(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.sqlite.JDBC");

        File tempFile = File.createTempFile("schema", ".sql");
        settings.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_ACTION, "update");
        settings.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_CREATE_TARGET, tempFile.getAbsolutePath());
        settings.put(AvailableSettings.HBM2DDL_DELIMITER, ";");
        settings.put(AvailableSettings.FORMAT_SQL, "true");

        var serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        var metadataSources = new MetadataSources(serviceRegistry);

        for (String className : puDescriptor.getManagedClassNames()) {
            metadataSources.addAnnotatedClassName(className);
        }

        var metadata = metadataSources.buildMetadata();

        SchemaManagementToolCoordinator.process(
                metadata,
                serviceRegistry,
                settings,
                _ -> {}
        );

        System.out.println(IOUtils.toString(tempFile.toURI(), StandardCharsets.UTF_8));
        FileUtils.deleteQuietly(tempFile);
    }
}