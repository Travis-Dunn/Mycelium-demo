package com.intermet.mycelium.lexicons;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class AbstractLexicon {
    /* Per-version attribs */
    protected final String DEVICE_NAME_STR = "deviceName";

    /* Per-instance attribs */
    public final String deviceName;

    protected AbstractLexicon(JsonNode root) {
        if (root.has(DEVICE_NAME_STR)) {
            deviceName = root.get(DEVICE_NAME_STR).asText();
        } else {
            throw new RuntimeException("TODO: Make better error handling");
        }
    }

    public abstract int getVersion();
}
