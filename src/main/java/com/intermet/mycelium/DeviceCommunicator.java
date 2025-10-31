package com.intermet.mycelium;

import com.fasterxml.jackson.databind.JsonNode;
import com.intermet.mycelium.command_panels.CommandPanel;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DeviceCommunicator {
    private JsonNode currentLexicon;
    private CommandPanel activePanel;
    private String protocol;
    private boolean isConnected = false;

    // USB-specific fields
    private Context context;
    private DeviceHandle deviceHandle;
    private Device usbDevice;
    private SerialPort serialPort;
    private byte outEndpoint = -1;  // Endpoint for sending data
    private byte inEndpoint = -1;   // Endpoint for receiving data
    private short vendorId;
    private short productId;

    public DeviceCommunicator() {}

    public void setLexicon(JsonNode lexicon) {
        currentLexicon = lexicon;
        if (lexicon.has("protocol")) {
            protocol = lexicon.get("protocol").asText();
        }
    }

    public void setActivePanel(CommandPanel panel) {
        activePanel = panel;
    }

    public boolean connect(String arg) {
        if (currentLexicon == null) return false;

        // Disconnect any existing connection first
        if (isConnected) {
            disconnect();
        }

        String vendorIdStr = currentLexicon.has("vendorId") ?
                currentLexicon.get("vendorId").asText() : "";
        String productIdStr = currentLexicon.has("productId") ?
                currentLexicon.get("productId").asText() : "";

        System.out.println("Connecting to device - protocol: " + protocol +
                ", VID: " + vendorIdStr + ", PID: " + productIdStr);

        if ("USB".equalsIgnoreCase(protocol)) {
            try {
                // Parse vendor and product IDs
                vendorId = parseHexId(vendorIdStr);
                productId = parseHexId(productIdStr);

                // Initialize the libusb context
                context = new Context();
                int result = LibUsb.init(context);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to initialize libusb", result);
                }

                // Find and open the device
                usbDevice = findDevice(vendorId, productId);
                if (usbDevice == null) {
                    System.err.println("USB device not found with VID: " +
                            String.format("0x%04X", vendorId) +
                            ", PID: " + String.format("0x%04X", productId));
                    LibUsb.exit(context);
                    return false;
                }

                // Open the device
                deviceHandle = new DeviceHandle();
                result = LibUsb.open(usbDevice, deviceHandle);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to open USB device", result);
                }

                // Detach kernel driver if necessary (Linux)
                boolean detached = false;
                result = LibUsb.kernelDriverActive(deviceHandle, 0);
                if (result == 1) {
                    result = LibUsb.detachKernelDriver(deviceHandle, 0);
                    if (result != LibUsb.SUCCESS) {
                        System.err.println("Cannot detach kernel driver: " +
                                LibUsb.errorName(result));
                    } else {
                        detached = true;
                        System.out.println("Detached kernel driver");
                    }
                }

                // Claim interface 0 (typical for printers)
                result = LibUsb.claimInterface(deviceHandle, 0);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to claim interface", result);
                }

                // Get the configuration and find endpoints
                findEndpoints();

                isConnected = true;
                System.out.println("Successfully connected to USB device");

                // Try to get device strings
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(usbDevice, descriptor);
                if (result == LibUsb.SUCCESS) {
                    System.out.println("Device info:");
                    System.out.println("  USB Version: " +
                            String.format("%d.%d",
                                    descriptor.bcdUSB() >> 8,
                                    descriptor.bcdUSB() & 0xFF));

                    // Try to get string descriptors
                    String manufacturer = getStringDescriptor(descriptor.iManufacturer());
                    String product = getStringDescriptor(descriptor.iProduct());
                    String serial = getStringDescriptor(descriptor.iSerialNumber());

                    if (manufacturer != null) System.out.println("  Manufacturer: " + manufacturer);
                    if (product != null) System.out.println("  Product: " + product);
                    if (serial != null) System.out.println("  Serial Number: " + serial);
                }

                return true;

            } catch (Exception e) {
                System.err.println("Failed to connect to USB device: " + e.getMessage());
                e.printStackTrace();
                disconnect();  // Clean up on failure
                return false;
            }
        } else if ("USB_SERIAL".equalsIgnoreCase(protocol)) {
            return connectSerial(arg);
        }else {
            System.out.println("Non-USB protocols not yet implemented");
            return false;
        }
    }

    private boolean connectSerial(String portStr) {
        try {
            // FTDI devices show up as /dev/ttyUSB* on Linux, COM* on Windows
            String portName = currentLexicon.has("serialPort")
                    ? currentLexicon.get("serialPort").asText()
                    : null;

            if (portName == null) {
                System.err.println("No serial port specified in lexicon");
                return false;
            }
            System.out.println("Connecting to serial port: " + portName);

            /* skip the above and get from gui instead */
            serialPort = new SerialPort(portStr);
            serialPort.openPort();

            // Configure from lexicon
            serialPort.setParams(
                    57600,  // from serialConfig.baudRate
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE
            );

            isConnected = true;
            return true;
        } catch (SerialPortException e) {
            System.err.println("Failed to open serial port: " + e.getMessage());
            return false;
        }
    }

    private void findEndpoints() {
        ConfigDescriptor config = new ConfigDescriptor();
        int result = LibUsb.getActiveConfigDescriptor(usbDevice, config);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to get config descriptor", result);
        }

        try {
            // Typically we want interface 0 for printers
            Interface iface = config.iface()[0];
            InterfaceDescriptor setting = iface.altsetting()[0];

            // Find bulk endpoints
            for (int i = 0; i < setting.bNumEndpoints(); i++) {
                EndpointDescriptor endpoint = setting.endpoint()[i];
                byte address = endpoint.bEndpointAddress();
                byte type = endpoint.bmAttributes();

                // Check if it's a bulk endpoint
                if ((type & LibUsb.TRANSFER_TYPE_MASK) == LibUsb.TRANSFER_TYPE_BULK) {
                    if ((address & LibUsb.ENDPOINT_IN) != 0) {
                        inEndpoint = address;
                        System.out.println("Found IN endpoint: " +
                                String.format("0x%02X", address));
                    } else {
                        outEndpoint = address;
                        System.out.println("Found OUT endpoint: " +
                                String.format("0x%02X", address));
                    }
                }
            }
        } finally {
            LibUsb.freeConfigDescriptor(config);
        }
    }

    private String getStringDescriptor(byte index) {
        if (index == 0) return null;

        StringBuffer buffer = new StringBuffer();
        int result = LibUsb.getStringDescriptorAscii(deviceHandle, index, buffer);
        if (result < 0) return null;

        return buffer.toString();
    }

    private Device findDevice(short vendorId, short productId) {
        // Get the list of USB devices
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(context, list);
        if (result < 0) {
            throw new LibUsbException("Unable to get device list", result);
        }

        try {
            // Iterate through all devices
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                System.out.println("vid [" + descriptor.idVendor() + "] " +
                        "pid [" + descriptor.idProduct() + "]");
                if (result != LibUsb.SUCCESS) {
                    continue;
                }


                if (descriptor.idVendor() == vendorId &&
                        descriptor.idProduct() == productId) {
                    // Found our device, increase reference count before returning
                    LibUsb.refDevice(device);
                    return device;
                }
            }
            return null;
        } finally {
            // Free the device list but not the devices themselves
            LibUsb.freeDeviceList(list, false);
        }
    }

    public void disconnect() {
        try {
            if (deviceHandle != null) {
                if (isConnected) {
                    // Release the interface
                    LibUsb.releaseInterface(deviceHandle, 0);
                    System.out.println("Released USB interface");
                }

                // Close the device handle
                LibUsb.close(deviceHandle);
                deviceHandle = null;
                System.out.println("Closed device handle");
            }

            if (usbDevice != null) {
                LibUsb.unrefDevice(usbDevice);
                usbDevice = null;
            }

            if (context != null) {
                LibUsb.exit(context);
                context = null;
                System.out.println("Exited libusb context");
            }
        } catch (Exception e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        } finally {
            outEndpoint = -1;
            inEndpoint = -1;
            isConnected = false;
        }
    }

    public void sendCommand(String commandName) {
        if (!isConnected || currentLexicon == null) {
            System.err.println("Not connected, or no lexicon loaded");
            return;
        }

        // Special handling for print functionality
        if ("printText".equals(commandName)) {
            if (activePanel != null) {
                byte[] printJobData = activePanel.getCommandData();
                sendRawData(printJobData);
            } else {
                System.err.println("No active panel for printText command");
            }
            return;
        }
        if ("streaming mode".equals(commandName)) {
            if (activePanel != null) {
                try {
                    serialPort.writeString("/SRN?\r\n");
                    Thread.sleep(100);
                    String response = serialPort.readString();
                    activePanel.updateResponse(response.getBytes(StandardCharsets.US_ASCII));
                } catch (Exception ignored) {}
            }
        } else if (commandName == null) {
            if (activePanel != null) {
                try {
                    String response = serialPort.readString();
                    activePanel.updateResponse(response.getBytes(StandardCharsets.US_ASCII));
                } catch (Exception ignored) {}
            }
        }

        JsonNode command = findCommand(commandName);
        if (command == null) {
            System.err.println("Command not found: " + commandName);
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

        // Actually send the command over USB
        if ("USB".equalsIgnoreCase(protocol) && outEndpoint != -1) {
            try {
                ByteBuffer buffer = ByteBuffer.allocateDirect(fullCommand.length);
                buffer.put(fullCommand);
                buffer.rewind();

                IntBuffer transferred = IntBuffer.allocate(1);
                int result = LibUsb.bulkTransfer(deviceHandle, outEndpoint, buffer,
                        transferred, 5000);  // 5 second timeout

                if (result == LibUsb.SUCCESS) {
                    System.out.println("Sent " + transferred.get(0) + " bytes successfully");
                } else {
                    System.err.println("Failed to send data: " + LibUsb.errorName(result));
                }

                // If this is a query command, we might expect a response
                if (command.has("interface")) {
                    JsonNode interfaceNode = command.get("interface");
                    boolean isQuery = false;
                    if (interfaceNode.isArray()) {
                        for (JsonNode iface : interfaceNode) {
                            if ("query".equals(iface.asText())) {
                                isQuery = true;
                                break;
                            }
                        }
                    }

                    if (isQuery && inEndpoint != -1) {
                        // Read response for query commands
                        ByteBuffer responseBuffer = ByteBuffer.allocateDirect(64);
                        IntBuffer received = IntBuffer.allocate(1);
                        result = LibUsb.bulkTransfer(deviceHandle, inEndpoint,
                                responseBuffer, received, 1000);  // 1 second timeout

                        if (result == LibUsb.SUCCESS && received.get(0) > 0) {
                            byte[] responseData = new byte[received.get(0)];
                            responseBuffer.get(responseData);
                            System.out.println("Received response: " + bytesToHex(responseData));

                            if (activePanel != null) {
                                activePanel.updateResponse(responseData);
                            }
                        } else if (result == LibUsb.ERROR_TIMEOUT) {
                            // Timeout is OK for commands that don't respond
                            System.out.println("No response received (timeout)");
                        } else if (result != LibUsb.SUCCESS) {
                            System.err.println("Error reading response: " + LibUsb.errorName(result));
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("Failed to send command: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (!("USB".equalsIgnoreCase(protocol))) {
            System.out.println("Protocol " + protocol + " not yet implemented");
        } else {
            System.err.println("No output endpoint available");
        }
    }

    /**
     * Sends raw data to the printer without looking up commands in lexicon
     * @param data Complete byte array to send
     * @return true if successful, false otherwise
     */
    public boolean sendRawData(byte[] data) {
        if (!isConnected) {
            System.err.println("Not connected");
            return false;
        }

        if (!"USB".equalsIgnoreCase(protocol)) {
            System.err.println("Protocol " + protocol + " not yet implemented");
            return false;
        }

        if (outEndpoint == -1) {
            System.err.println("No output endpoint available");
            return false;
        }

        try {
            System.out.println("Sending " + data.length + " bytes to device");

            ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
            buffer.put(data);
            buffer.rewind();

            IntBuffer transferred = IntBuffer.allocate(1);
            int result = LibUsb.bulkTransfer(deviceHandle, outEndpoint, buffer,
                    transferred, 10000);  // 10 second timeout for print jobs

            if (result == LibUsb.SUCCESS) {
                System.out.println("Sent " + transferred.get(0) + " bytes successfully");
                return true;
            } else {
                System.err.println("Failed to send data: " + LibUsb.errorName(result));
                return false;
            }

        } catch (Exception e) {
            System.err.println("Failed to send raw data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private short parseHexId(String hexStr) {
        String cleaned = hexStr.replace("0x", "")
                .replace("0X", "");
        return (short) Integer.parseInt(cleaned, 16);
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
                .replace("0X", "")
                .replace(" ", "");
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

    public boolean isConnected() {
        return isConnected;
    }
}