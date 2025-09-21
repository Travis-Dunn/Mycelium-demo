package com.intermet.mycelium.command_panels;

import javax.swing.*;

public interface CommandPanel {
    JPanel getPanel();

    /**
     * Called when the device sends a response to this command.
     * For commands that don't expect responses, this may be empty.
     *
     * @param data Raw bytes received from the device
     */
    void updateResponse(byte[] data);

    /**
     * Builds the data blob to send to the device.
     *
     * @return Byte array to send to device
     */
    byte[] getCommandData();

    String getCommandName();
}
