package com.intermet.mycelium.command_panels;
import com.intermet.mycelium.DeviceCommunicator;

import com.fasterxml.jackson.databind.JsonNode;
import javax.swing.*;
import java.awt.*;

public class PrintLabelPanel implements CommandPanel {
    private JPanel panel;
    private JTextField textInput;
    private JButton printButton;
    private JLabel statusLabel;
    private String commandName;
    private String description;
    private DeviceCommunicator communicator;

    public PrintLabelPanel(JsonNode commandDefinition, DeviceCommunicator communicator) {
        this.commandName = commandDefinition.get("name").asText();
        this.description = commandDefinition.has("description")
                ? commandDefinition.get("description").asText()
                : "No description available";
        this.communicator = communicator;

        initializePanel();
    }

    private void initializePanel() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel descLabel = new JLabel(description);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(10));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputPanel.add(new JLabel("Text to print:"));

        textInput = new JTextField(30);
        textInput.setPreferredSize(new Dimension(300, 25));
        textInput.setMinimumSize(new Dimension(300, 25));
        inputPanel.add(textInput);
        inputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        panel.add(inputPanel);
        panel.add(Box.createVerticalStrut(10));

        printButton = new JButton("Print Label");
        printButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        printButton.addActionListener(e -> onPrintLabel());
        panel.add(printButton);
        panel.add(Box.createVerticalStrut(10));

        statusLabel = new JLabel(" ");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setForeground(Color.GRAY);
        panel.add(statusLabel);
    }

    private void onPrintLabel() {
        String text = textInput.getText().trim();

        if (text.isEmpty()) {
            statusLabel.setText("Error: No text entered");
            statusLabel.setForeground(Color.RED);
            return;
        }

        if (communicator != null && communicator.isConnected()) {
            statusLabel.setText("Printing label...");
            statusLabel.setForeground(Color.BLUE);

            SwingUtilities.invokeLater(() -> {
                try {
                    /* TODO: font rendering */
                    communicator.sendCommand(commandName);
                    statusLabel.setText("Label printed at " +
                            java.time.LocalTime.now().format(
                                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                    statusLabel.setForeground(new Color(0, 128, 0));
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                }
            });
        } else {
            statusLabel.setText("Error: Not connected to device");
            statusLabel.setForeground(Color.RED);
        }
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void updateResponse(byte[] data) {}

    @Override
    public byte[] getCommandData() {

        return new byte[0];
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    public String getInputText() {
        return textInput.getText();
    }
}
