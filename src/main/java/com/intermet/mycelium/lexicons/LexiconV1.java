package com.intermet.mycelium.lexicons;

import com.fasterxml.jackson.databind.JsonNode;

public class LexiconV1 extends AbstractLexicon {
    public static final int VERSION = 1;

    //DEVICE_NAME_STR = "device_name";

    public LexiconV1(JsonNode root) {
        super(root);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
