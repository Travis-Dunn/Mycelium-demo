package com.intermet.mycelium.command_panels;

import com.fasterxml.jackson.databind.JsonNode;
import com.intermet.mycelium.DeviceCommunicator;
import com.intermet.mycelium.MyceliumColors;

import javax.swing.*;
import java.awt.*;

public class SimpleCommandPanel implements CommandPanel {
    private JPanel panel;
    private JButton sendButton;
    private JLabel statusLabel;
    private String commandName;
    private String description;
    private DeviceCommunicator communicator;

    public SimpleCommandPanel(JsonNode commandDefinition, DeviceCommunicator communicator) {
        /* set name and description */
        this.commandName = commandDefinition.get("name").asText();
        this.description = commandDefinition.has("description")
                ? commandDefinition.get("description").asText()
                : "No description available";
        this.communicator = communicator;

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
        if (communicator != null && communicator.isConnected()) {
            statusLabel.setText("Sending command...");
            statusLabel.setForeground(MyceliumColors.primary);

            // Send the command in a separate thread to avoid blocking the GUI
            SwingUtilities.invokeLater(() -> {
                try {
                    communicator.sendCommand(commandName);
                    statusLabel.setText("Command sent at " +
                            java.time.LocalTime.now().format(
                                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                    statusLabel.setForeground(new Color(0, 128, 0));  // Dark green
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(MyceliumColors.tertiary);
                }
            });
        } else if (communicator == null) {
            statusLabel.setText("Error: No communicator set");
            statusLabel.setForeground(MyceliumColors.tertiary);
            System.err.println("Communicator not set");
        } else {
            statusLabel.setText("Error: Not connected to device");
            statusLabel.setForeground(Color.RED);
            System.err.println("Not connected to device");
        }
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void updateResponse(byte[] data) {
        // SimpleCommand doesn't expect responses, but log if we get one
        if (data != null && data.length > 0) {
            System.out.println("Unexpected response for " + commandName + ": " +
                    bytesToHex(data));
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Received unexpected response");
                statusLabel.setForeground(Color.ORANGE);
            });
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

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
