package com.larcane.realtimepostalarmapplication.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public enum JacksonUtil {
    INSTANCE;

    private final ObjectMapper objectMapper;

    JacksonUtil() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public ObjectMapper getInstance() {
        return objectMapper;
    }
}
