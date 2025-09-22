package com.intermet.mycelium;

import com.fasterxml.jackson.databind.JsonNode;
import com.intermet.mycelium.command_panels.CommandPanel;

import javax.swing.*;
import java.awt.*;

public class SimpleCommandPanel implements CommandPanel {
    private JPanel panel;
    private JButton sendButton;
    private JLabel statusLabel;
    private String commandName;
    private String description;
    private DeviceCommunicator communicator;

    public SimpleCommandPanel(JsonNode commandDefinition) {
        /* set name and description */
        this.commandName = commandDefinition.get("name").asText();
        this.description = commandDefinition.has("description")
                ? commandDefinition.get("description").asText()
                : "No description available";

        initializePanel();
    }

    public void setCommunicator(DeviceCommunicator communicator) {
        this.communicator = communicator;
    }

    private void initializePanel() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        /* description */
        JLabel descLabel = new JLabel(description);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(10));

        /* button */
        sendButton = new JButton("Send " + commandName);
        sendButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendButton.addActionListener(e -> onSendCommand());
        panel.add(sendButton);
        panel.add(Box.createVerticalStrut(10));

        /* only used for showing a non-interactive timestamp, when the button is
        actuated */
        statusLabel = new JLabel(" ");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setForeground(Color.GRAY);
        panel.add(statusLabel);
    }

    private void onSendCommand() {
        statusLabel.setText("Command sent at " +
                java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));

        if (communicator != null) {
            communicator.sendCommand(commandName);
        } else {
            System.out.println("Communicator not set");
        }

        /* This is where we will interact with the communication layer to
        * actually send the command, but for now just print something. */
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void updateResponse(byte[] data) {
        /* We do not expect a reply after sending this type of command, so just
        print to console for now, but probably just pass. */
        if (data != null && data.length > 0) {
            System.out.println("Unexpected response for " + commandName + ": " +
                    java.util.Arrays.toString(data));
        }
    }

    @Override
    public byte[] getCommandData() {
        /* So this one is a bit more interesting. It will be called when the
        * user presses the 'send' button, and is used to assemble the blob of
        * data that the communication layer will send. That blob will be a
        * combination of the command itself, plus any parameters. In the case of
        * the LabelWriter 450, commands are all 16 bits, and parameters are
        * mostly either one or two bytes. This method will be responsible for
        * (At least) passing the parameter information, as a blob, to the
        * communication layer. Whether it will also be responsible for the 16
        * command bits is still TBD. */
        return new byte[0];
    }

    @Override
    public String getCommandName() {
        return commandName;
    }
}
