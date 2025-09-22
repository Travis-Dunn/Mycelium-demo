package com.intermet.mycelium;

import com.fasterxml.jackson.databind.JsonNode;
import com.intermet.mycelium.command_panels.CommandPanel;

public class DeviceCommunicator {
    private JsonNode currentLexicon;
    private CommandPanel activePanel;
    private String protocol;
    private boolean isConnected = false;

    public DeviceCommunicator() {

    }

    public void setLexicon(JsonNode lexicon) {
        currentLexicon = lexicon;
        if (lexicon.has("protocol")) {
            protocol = lexicon.get("protocol").asText();
        }
    }

    public void setActivePanel(CommandPanel panel) {
        activePanel = panel;
    }

    public boolean connect() {
        if (currentLexicon == null) return false;

        String vendorId = currentLexicon.has("vendorId") ?
                currentLexicon.get("vendorId").asText() : "";
        String productId = currentLexicon.has("productId") ?
                currentLexicon.get("productId").asText() : "";
        System.out.println("Connecting to device - protocol: " + protocol +
                ", VID: " + vendorId + ", PID: " + productId);
        /* this is where USB comms will happen */
        return isConnected = true;
    }

    public void sendCommand(String commandName) {
        if (!isConnected || currentLexicon == null) {
            System.err.println("Not connected, or no lexicon loaded");
            return;
        }

        JsonNode command = findCommand(commandName);
        if (command == null) {
            System.err.println("Command not found " + commandName);
            return;
        }

        if (!command.has("commandCode")) {
            System.err.println("Command [" + commandName +
                    "] has no command code");
            return;
        }

        byte[] commandBytes =
                parseCommandCode(command.get("commandCode").asText());

        byte[] parameters = new byte[0];
        if (activePanel != null) {
            parameters = activePanel.getCommandData();
        }

        byte[] fullCommand = combineBytes(commandBytes, parameters);

        System.out.println("Sending to device: " + bytesToHex(fullCommand));

        /* this is where we would send the data over USB, and perhaps expect
        * a reply */
    }

    private JsonNode findCommand(String commandName) {
        if (!currentLexicon.has("commands")) {
            return null;
        }

        JsonNode commands = currentLexicon.get("commands");
        for (JsonNode cmd : commands) {
            if (cmd.has("name") && cmd.get("name").asText().equals(commandName)) {
                return cmd;
            }
        }
        return null;
    }

    private byte[] parseCommandCode(String hexStr) {
        String cleaned = hexStr.replace("0x", "")
                .replace("0X", "");
        int len = cleaned.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte)((Character.digit(cleaned.charAt(i), 16) << 4) +
                            Character.digit(cleaned.charAt(i + 1), 16));
        }
        return data;
    }

    private byte[] combineBytes(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
