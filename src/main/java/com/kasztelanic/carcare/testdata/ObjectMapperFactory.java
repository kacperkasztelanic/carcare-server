package com.kasztelanic.carcare.testdata;

import java.text.SimpleDateFormat;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectMapperFactory {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.registerModule(new JavaTimeModule());
        mapper.setDateFormat(new SimpleDateFormat(DATE_FORMAT));
        mapper.registerModule(new ParameterNamesModule());
        mapper.registerModule(defaultMappingsModule());
        return mapper;
    }

    private static SimpleModule defaultMappingsModule() {
        SimpleModule module = new SimpleModule("CustomModel", Version.unknownVersion());
        module.setAbstractTypes(defaultMappingsResolver());
        return module;
    }

    private static SimpleAbstractTypeResolver defaultMappingsResolver() {
        return new SimpleAbstractTypeResolver();
    }
}
