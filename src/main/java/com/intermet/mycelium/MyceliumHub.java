package com.intermet.mycelium;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intermet.mycelium.command_panels.CommandPanel;
import com.intermet.mycelium.command_panels.SimpleCommandPanel;

public class MyceliumHub extends JFrame {
    private JPanel mainPanel;
    private JPanel deviceInfoPanel;
    private JPanel commandsPanel;
    private JButton selectLexiconButton;
    private JButton connectButton;
    private ObjectMapper objectMapper;

    private JLabel deviceNameValue;
    private JLabel protocolValue;
    private JLabel vendorIdValue;
    private JLabel productIdValue;
    private JLabel descriptionValue;
    private JLabel connectionStatusLabel;

    private JsonNode currentLexicon;
    private CommandPanel activeCommandPanel;
    private Map<String, CommandPanel> commandPanelCache;
    private DeviceCommunicator deviceCommunicator;

    private Map<String, URLClassLoader> pluginLoaders; // Track loaded plugins

    /* This is the dropdown widget used for displaying any number of commands,
    * and selecting one */
    private JComboBox<String> commandDropdown;

    public MyceliumHub() {
        initializeGUI();
        objectMapper = new ObjectMapper();
        commandPanelCache = new HashMap<String, CommandPanel>();
        deviceCommunicator = new DeviceCommunicator();
        pluginLoaders = new HashMap<>();
    }

    private void initializeGUI() {
        setTitle("Mycelium Device Hub");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        /* Panels have a nested structure - the main panel has children.
        * This one is using "BoxLayout", which stacks children.
        * In this case since we passed "BoxLayout.Y_AXIS" it stacks them
        * vertically. */
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        /* small border around the main panel, dimensions are pixels. */
        mainPanel.setBorder(BorderFactory.createEmptyBorder
                (10, 10, 10, 10));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        selectLexiconButton = new JButton("Select Lexicon File");
        /* Here we pass a function as an argument, analogous to a function
        * pointer in C. Swing has an event system running behind the scenes, and
        * what we are doing here is adding onSelectLexiconFile as a listener for
        * events broadcast by the button. When the button is pressed, this
        * function is called, and passed an ActionEvent object, which is the
        * underlying event type of Swing's event system. */
        selectLexiconButton.addActionListener(this::onSelectLexiconFile);
        /* Now we add the button to the main panel. */
        topPanel.add(selectLexiconButton);

        connectButton = new JButton("Connect");
        connectButton.setEnabled(false);
        connectButton.addActionListener(this::onConnectToggle);
        topPanel.add(connectButton);

        connectionStatusLabel = new JLabel();
        connectionStatusLabel.setForeground(Color.GRAY);
        topPanel.add(connectionStatusLabel);

        mainPanel.add(topPanel);

        /* And space it out a little. Struts are invisible widgets that do
        * nothing but push widgets apart, i.e., a spacer. */
        mainPanel.add(Box.createVerticalStrut(20));

        /* Code for setting this panel up is verbose, and is pulled out */
        createDeviceInfoPanel();
        /* add it to the main panel, just like with the button */
        mainPanel.add(deviceInfoPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        /* Set up a placeholder panel that we'll fill out in demo v2 */
        commandsPanel = new JPanel();
        commandsPanel.setLayout(new BoxLayout(commandsPanel, BoxLayout.Y_AXIS));
        commandsPanel.setBorder(BorderFactory.createTitledBorder("Device Commands"));

        /* Create a JPanel to hold the dropdown widget we declared earlier.
        * Because this is only going to hold one thing, we just make it a local
        * variable, and give it FlowLayout. */
        JPanel commandSelectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        /* Give it a label */
        commandSelectorPanel.add(new JLabel("Select Command:"));
        commandDropdown = new JComboBox<>();
        /* Default to disabled - we'll enable it once a valid lexicon file has
        * been loaded */
        commandDropdown.setEnabled(false);
        commandDropdown.addActionListener(this::onCommandSelected);
        /* Add our dropdown widget to it */
        commandSelectorPanel.add(commandDropdown);
        /* Then add it to the commandsPanel */
        commandsPanel.add(commandSelectorPanel);

        /* Add a small separator between the dropdown and what's below it.
        * Right now, nothing is below it, but soon there will be all of the
        * command-specific info. */
        commandsPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        commandsPanel.add(Box.createVerticalStrut(10));

        mainPanel.add(commandsPanel);

        /* A scroll pane holds something, and if what it holds is too large, it
        * creates scroll bars so that you can see everything without needing
        * more screen space. Here we are putting the entire main panel in one
        * to avoid having the information being inaccessible off the edge of the
        * window. This is probably not necessary once we iron the GUI out. */
        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        /* Give the window a size, although it is still resizable */
        setSize(640, 480);

        /* Calling this with null puts the window in the center of the screen.
        * You don't have to call this, and if you don't, our program window will
        * start at 0, 0 screen space, i.e., the top left. */
        setLocationRelativeTo(null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (deviceCommunicator != null && deviceCommunicator.isConnected()) {
                deviceCommunicator.disconnect();
            }
        }));
    }

    private void onConnectToggle(ActionEvent e) {
        if (deviceCommunicator.isConnected()) {
            deviceCommunicator.disconnect();
            connectButton.setText("Connect");
            connectionStatusLabel.setText("Disconnected");
            connectionStatusLabel.setForeground(Color.GRAY);
            commandDropdown.setEnabled(false);
        } else {
            boolean connected = deviceCommunicator.connect();
            if (connected) {
                connectButton.setText("Disconnect");
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setForeground(new Color(22, 117, 11));
                commandDropdown.setEnabled(true);
            } else {
                connectionStatusLabel.setText("Connection failed");
                connectionStatusLabel.setForeground(Color.RED);
                JOptionPane.showMessageDialog(
                        this, "Failed to connect to device.\n" +
                                "Please ensure the device is connected and powered on\n",
                        "Connection Error", JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void onSelectLexiconFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Lexicon File");

        /* Configure Swing's FileFilter widget, which the JFileChooser owns. */
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            /* This method is used to filter out the files that show up in the
            * dialog box. We want to allow json files obviously, but also
            * directories, so that the user can traverse the whole file system,
            * and not just the default directory. */
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }

            /* This is the string to put in the dropdown menu labeled
            * "Files of type" */
            @Override
            public String getDescription() {
                return "JSON Files (*.json)";
            }
        });

        /* If the user has selected a valid file, call parseLexiconFile and pass
        * it the selectedFile. */
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            parseLexiconFile(selectedFile);
        }
    }

    private void parseLexiconFile(File file) {
        try {
            if (deviceCommunicator.isConnected()) {
                deviceCommunicator.disconnect();
                connectButton.setText("Connect");
                connectionStatusLabel.setText("Device loaded, not connected");
                connectionStatusLabel.setForeground(Color.GRAY);
            }

            /* Dump the file to a string */
            String content = Files.readString(file.toPath());
            /* interpret it as a JSON object */
            JsonNode root = objectMapper.readTree(content);
            currentLexicon = root;

            // NEW: Load plugin if specified
            if (root.has("pluginJar")) {
                String jarFileName = root.get("pluginJar").asText();
                URLClassLoader pluginLoader = loadPlugin(file.getAbsolutePath(), jarFileName);
                if (pluginLoader != null) {
                    pluginLoaders.put(file.getName(), pluginLoader);
                    System.out.println("Plugin loaded successfully");
                }
            }

            deviceCommunicator.setLexicon(root);

            /* This function takes a JPanel, the JSON object, and a string.
            * It finds the JSON node of the same name as the string argument,
            * and set's the JPanel's label to the node's value */
            updateDeviceField(deviceNameValue, root, "deviceName");
            updateDeviceField(protocolValue, root, "protocol");
            updateDeviceField(vendorIdValue, root, "vendorId");
            updateDeviceField(productIdValue, root, "productId");
            updateDeviceField(descriptionValue, root, "description");

            /* We're not doing any actual communication yet, so just print
            * something if we found that the protocol was "USB". In a future
            * demo, we'll check the found protocol against an array of supported
            * protocols, and open communication accordingly. */
            if (root.has("protocol")) {
                String protocol = root.get("protocol").asText();
                if ("USB".equalsIgnoreCase(protocol)) {
                    connectButton.setEnabled(true);
                    connectionStatusLabel.setText("Ready to connect");
                    System.out.println("USB protocol detected - ready for device communication");
                } else if ("USB_SERIAL".equalsIgnoreCase(protocol)) {
                    connectButton.setEnabled(true);
                    connectionStatusLabel.setText("Ready to connect");
                    System.out.println("USB_SERIAL protocol detected - ready for device communication");
                } else {
                    connectButton.setEnabled(false);
                    connectionStatusLabel.setText("Protocol [" + protocol +
                            "] not yet supported");
                    connectionStatusLabel.setForeground(Color.ORANGE);
                }
            }

            if (root.has("commands")) {
                JsonNode commandsArray = root.get("commands");
                if (commandsArray.isArray() && !commandsArray.isEmpty()) {
                    // Clear and repopulate the dropdown
                    commandDropdown.removeAllItems();
                    commandDropdown.addItem("-- Select Command --");

                    for (JsonNode command : commandsArray) {
                        if (command.has("name")) {
                            String commandName = command.get("name").asText();
                            commandDropdown.addItem(commandName);
                        }
                    }

                    /* We need to enable the dropdown widget */
                    commandDropdown.setEnabled(false);
                    System.out.println("Loaded " + (commandDropdown.getItemCount() - 1) + " commands");
                } else {
                    commandDropdown.setEnabled(false);
                    System.out.println("No commands found in lexicon");
                }
            } else {
                commandDropdown.setEnabled(false);
                System.out.println("No commands node found in lexicon");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error reading file: " + ex.getMessage(),
                    "File Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error parsing JSON: " + ex.getMessage(),
                    "Parse Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateDeviceField(JLabel label, JsonNode root, String fieldName) {
        if (root.has(fieldName)) {
            label.setText(root.get(fieldName).asText());
        } else {
            label.setText("--");
        }
    }

    private void onCommandSelected(ActionEvent e) {
        String selectedCommand = (String) commandDropdown.getSelectedItem();
        if (selectedCommand != null && !selectedCommand.equals("-- Select Command --")) {
            System.out.println("Selected command: " + selectedCommand);
            /* lazy init the CommandPanel */
            if (!commandPanelCache.containsKey(selectedCommand)) {
                CommandPanel newPanel = createPanelForCommand1(selectedCommand);
                if (newPanel != null) {
                    commandPanelCache.put(selectedCommand, newPanel);
                }
            }

            /* Get the CommandPanel from the hashmap, assign it to the active
            reference */
            activeCommandPanel = commandPanelCache.get(selectedCommand);

            deviceCommunicator.setActivePanel(activeCommandPanel);

            if (activeCommandPanel != null) {
                updateCommandsDisplay();
            }
        }
    }

    private CommandPanel createPanelForCommand(String commandName) {
        if (currentLexicon == null || !currentLexicon.has("commands")) {
            return null;
        }

        /* We don't parse the lexicon file into a class or anything, just leave
        it as a Json node */
        JsonNode commandsArray = currentLexicon.get("commands");
        for (JsonNode command : commandsArray) {
            if (command.has("name") && command.get("name").asText().equals(commandName)) {
                /* Determine type based on the presence of parameters and return
                type */
                boolean hasParameters = command.has("parameters") &&
                        command.get("parameters").size() > 0;
                boolean hasResponse = command.has("responseType");

                /* Only handle the type of command which has no params or return
                 */
                if (!hasParameters && !hasResponse) {
                    return new SimpleCommandPanel(command, deviceCommunicator);
                } else if (hasParameters && !hasResponse) {
                    if (command.has("name") && command.get("name").asText().equals("printText")) {
                        try {
                            Class<?> printLabelPanelClass = Class.forName(
                                    "com.intermet.mycelium.plugins.labelwriter.PrintLabelPanel");
                            return (CommandPanel)printLabelPanelClass.getConstructor(
                                    JsonNode.class, DeviceCommunicator.class).
                                    newInstance(command, deviceCommunicator);
                        } catch (Exception e) {
                            System.err.println("Failed to load class dynamically");
                            e.printStackTrace();
                        }
                    }
                } else if (!hasParameters && hasResponse) {
                    if (command.has("name") && command.get("name").asText().equals("getSerialNumber")) {
                        try {
                            Class<?> printLabelPanelClass = Class.forName(
                                    "com.intermet.mycelium.plugins.XQ2.SerialNumberPanel");
                            return (CommandPanel)printLabelPanelClass.getConstructor(
                                            JsonNode.class, DeviceCommunicator.class).
                                    newInstance(command, deviceCommunicator);
                        } catch (Exception e) {
                            System.err.println("Failed to load class dynamically");
                            e.printStackTrace();
                        }
                    }
                } else {
                    System.out.println("Command type not yet implemented, using SimpleCommandPanel");
                    return new SimpleCommandPanel(command, deviceCommunicator);
                }
            }
        }

        return null;
    }

    private CommandPanel createPanelForCommand1(String commandName) {
        if (currentLexicon == null || !currentLexicon.has("commands")) {
            return null;
        }

        JsonNode commandsArray = currentLexicon.get("commands");
        for (JsonNode command : commandsArray) {
            if (command.has("name") && command.get("name").asText().equals(commandName)) {

                // Check if command specifies a custom panel class
                if (command.has("panelClass")) {
                    String className = command.get("panelClass").asText();
                    try {
                        // Get the plugin loader for this lexicon
                        URLClassLoader pluginLoader = pluginLoaders.values().stream()
                                .findFirst()
                                .orElse(null);

                        Class<?> panelClass;
                        if (pluginLoader != null) {
                            // Load from plugin
                            panelClass = pluginLoader.loadClass(className);
                        } else {
                            // Try loading from main classpath (fallback)
                            panelClass = Class.forName(className);
                        }

                        return (CommandPanel) panelClass
                                .getConstructor(JsonNode.class, DeviceCommunicator.class)
                                .newInstance(command, deviceCommunicator);

                    } catch (Exception e) {
                        System.err.println("Failed to load panel class: " + className);
                        e.printStackTrace();
                    }
                }

                // Default to SimpleCommandPanel
                return new SimpleCommandPanel(command, deviceCommunicator);
            }
        }
        return null;
    }

    private void updateCommandsDisplay() {
        Component[] components = commandsPanel.getComponents();
        if (components.length > 3) {
            for (int i = 3; i < components.length; i++) {
                commandsPanel.remove(components[i]);
            }
        }

        if (activeCommandPanel != null) {
            commandsPanel.add(activeCommandPanel.getPanel());
        }

        commandsPanel.revalidate();
        commandsPanel.repaint();
    }

    public static void main(String[] args) {
        /* All that must be done in 'main' is to instantiate a MyceliumHub and
        * call setVisible, and the program would work correctly. However, Swing
        * maintains its own thread for the GUI event system, in addition to
        * any others that our program may create. The point of only using our
        * main thread to hand off execution to Swing's thread (Referred to as
        * the event dispatch thread (EDT)) is to guarantee thread safety.
        * That said, we also avoid having the main thread performance slightly
        * hindered in unpredictable ways, which is a side benefit that may be
        * appreciated at some point, when doing a lot of fast IO or running on
        * lightweight hardware.
        *
        * It's worth mentioning that some more modern GUI libs are better in
        * this regard and come with out-of-the-box multithreading and thread
        * safety. However, Swing is ancient and therefor predictable, reliable,
        * and flawless documented. It is also perfectly understood by all major
        * AIs, which can't be said for many of the newer GUI libs. It's also
        * significantly faster than tkinter, not a pain in the ass to use like
        * QT, and free, unlike the C# libs you were interested in at one time. */
        SwingUtilities.invokeLater(() -> {
            MyceliumHub hub = new MyceliumHub();
            hub.setVisible(true);
        });
    }

    /* This is a load of GUI configuration */
    private void createDeviceInfoPanel() {
        deviceInfoPanel = new JPanel();
        /* Note that we use GridBagLayout here rather than BoxLayout, because
        * it's a lot more configurable. */
        deviceInfoPanel.setLayout(new GridBagLayout());
        deviceInfoPanel.setBorder(BorderFactory.createTitledBorder(
                "Device agnostic fields"
        ));

        /* This object is kind of like a "properties struct". You manipulate
        * this and connect it to the object that is using GridBagLayout. */
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        /* First row - Device name */
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        addFieldLabel("Device Name:", deviceInfoPanel, gbc);

        /* Same row, 1th (second) column is the value */
        gbc.gridx = 1;
        deviceNameValue = createValueLabel();
        deviceInfoPanel.add(deviceNameValue, gbc);

        /* Repeat for each of the other 4 items */
        gbc.gridx = 0;
        gbc.gridy = 1;
        addFieldLabel("Protocol:", deviceInfoPanel, gbc);

        gbc.gridx = 1;
        protocolValue = createValueLabel();
        deviceInfoPanel.add(protocolValue, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        addFieldLabel("Vendor ID:", deviceInfoPanel, gbc);

        gbc.gridx = 1;
        vendorIdValue = createValueLabel();
        deviceInfoPanel.add(vendorIdValue, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        addFieldLabel("Product ID:", deviceInfoPanel, gbc);

        gbc.gridx = 1;
        productIdValue = createValueLabel();
        deviceInfoPanel.add(productIdValue, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        addFieldLabel("Description:", deviceInfoPanel, gbc);

        gbc.gridx = 1;
        /* This may end up being larger, so we set its width to two so that the
        * text can spill over and occupy up to two columns */
        gbc.gridwidth = 2;
        /* Then we allow it to stretch horizontally */
        gbc.fill = GridBagConstraints.HORIZONTAL;
        descriptionValue = createValueLabel();
        deviceInfoPanel.add(descriptionValue, gbc);
    }

    private void addFieldLabel(String text, JPanel panel, GridBagConstraints gbc) {
        JLabel label = new JLabel(text);
        /* Make the font the bold version of whatever font we are using */
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);
    }

    /* Helper to reduce boilerplate and insure the same formatting */
    private JLabel createValueLabel() {
        /* Two dashes make it more clear that a value goes here than if we left
        it blank, in the case that there is not yet a value to put here. */
        JLabel label = new JLabel("--");
        label.setForeground(Color.DARK_GRAY);
        /* If you don't give them a minimum size, the GUI will re-arrange
        * itself when there's nothing there, which looks bad. */
        label.setMinimumSize(new Dimension(200, 20));
        label.setPreferredSize(new Dimension(200, 20));
        return label;
    }

    private URLClassLoader loadPlugin(String lexiconPath, String jarFileName) {
        try {
            // Get directory containing the lexicon file
            File lexiconFile = new File(lexiconPath);
            File deviceDir = lexiconFile.getParentFile();

            // Construct path to plugin JAR
            File jarFile = new File(deviceDir, jarFileName);

            if (!jarFile.exists()) {
                System.err.println("Plugin JAR not found: " + jarFile.getAbsolutePath());
                return null;
            }

            System.out.println("Loading plugin from: " + jarFile.getAbsolutePath());

            // Create URLClassLoader for this plugin
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    this.getClass().getClassLoader()
            );

            return loader;

        } catch (Exception e) {
            System.err.println("Failed to load plugin: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}