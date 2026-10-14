package com.github.enerccio.lingollm.domain.model.converter;

import com.github.enerccio.lingollm.Configuration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stores a secret encrypted with the installation key ({@link Configuration#encrypt}). Created by Spring through
 * Hibernate's bean container ({@code hibernate.resource.beans.container} in datasources-config.xml).
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private Configuration configuration;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return configuration.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return configuration.decrypt(dbData);
    }
}
