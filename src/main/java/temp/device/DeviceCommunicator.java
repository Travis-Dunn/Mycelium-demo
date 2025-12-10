package temp.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.intermet.mycelium.command_panels.CommandPanel;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.usb4java.*;
import temp.LogLevel;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static temp.ErrorHandler.LogFatalAndExit;
import static temp.Logger.LogSession;
import static temp.Logger.LogSessionExcp;

public class DeviceCommunicator {
    private JsonNode currentLexicon;
    private CommandPanel activePanel;
    private Protocol protocol = Protocol.UNKNOWN;
    private boolean isConnected = false;
    private boolean headless = false;

    /* USB-specific fields */
    private Context context;
    private DeviceHandle deviceHandle;
    private Device usbDevice;
    private byte outEndpoint = -1;
    private byte inEndpoint = -1;
    private short vendorId;
    private short productId;

    /* Serial-specific fields */
    private SerialPort serialPort;

    public DeviceCommunicator() {}

    /* Public API */

    /* Sets lexicon and protocol */
    public void setLexicon(JsonNode lexicon) {
        if (lexicon == null) {
            LogFatalAndExit(ERR_STR_LEXICON_NULL);
        }

        currentLexicon = lexicon;
        setProtocol();
    }

    public void setActivePanel(CommandPanel panel) {
        if (panel == null) {
            LogFatalAndExit(ERR_STR_PANEL_NULL);
        }
        
        activePanel = panel;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean connect(String portOrPath) {
        if (currentLexicon == null) {
            nonFatalError(ERR_STR_LEXICON_NOT_SET);
            return false;
        } else if (isConnected) {
            disconnect();
        }

        logConnectionAttempt();

        switch (protocol) {
            case USB:
                return connectUsb();
            case USB_SERIAL:
                return connectSerial(portOrPath);
            default:
                System.err.println("Unsupported protocol: " + protocol);
                return false;
        }
    }

    public void disconnect() {
        switch (protocol) {
            case USB:
                disconnectUsb();
                break;
            case USB_SERIAL:
                disconnectSerial();
                break;
            default:
                break;
        }
        isConnected = false;
    }

    public void sendCommand(String commandName) {
        if (!isConnected || currentLexicon == null) {
            System.err.println("Not connected or no lexicon loaded");
            return;
        }

        if (handleSpecialCommand(commandName)) {
            return;
        }

        JsonNode command = findCommand(commandName);
        if (command == null) {
            System.err.println("Command not found: " + commandName);
            return;
        }

        if (!command.has("commandCode")) {
            System.err.println("Command [" + commandName + "] has no command code");
            return;
        }

        byte[] commandBytes = parseHexBytes(command.get("commandCode").asText());
        byte[] parameters = (activePanel != null) ? activePanel.getCommandData() : new byte[0];
        byte[] fullCommand = combineBytes(commandBytes, parameters);

        System.out.println("Sending to device: " + bytesToHex(fullCommand));

        switch (protocol) {
            case USB:
                sendUsbCommand(fullCommand, command);
                break;
            case USB_SERIAL:
                sendSerialCommand(fullCommand);
                break;
            default:
                System.err.println("Send not implemented for protocol: " + protocol);
        }
    }

    public boolean sendRawData(byte[] data) {
        if (!isConnected) {
            System.err.println("Not connected");
            return false;
        }

        switch (protocol) {
            case USB:
                return sendRawUsb(data);
            case USB_SERIAL:
                return sendRawSerial(data);
            default:
                System.err.println("Raw send not implemented for protocol: " + protocol);
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // USB Implementation
    // -------------------------------------------------------------------------

    private boolean connectUsb() {
        try {
            String vendorIdStr = getLexiconString("vendorId", "");
            String productIdStr = getLexiconString("productId", "");

            vendorId = parseHexId(vendorIdStr);
            productId = parseHexId(productIdStr);

            context = new Context();
            int result = LibUsb.init(context);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize libusb", result);
            }

            usbDevice = findUsbDevice(vendorId, productId);
            if (usbDevice == null) {
                System.err.println("USB device not found - VID: " + formatHex16(vendorId) +
                        ", PID: " + formatHex16(productId));
                LibUsb.exit(context);
                return false;
            }

            deviceHandle = new DeviceHandle();
            result = LibUsb.open(usbDevice, deviceHandle);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to open USB device", result);
            }

            detachKernelDriverIfNeeded();
            claimInterface();
            findEndpoints();

            isConnected = true;
            System.out.println("Successfully connected to USB device");
            logUsbDeviceInfo();

            return true;

        } catch (Exception e) {
            System.err.println("Failed to connect to USB device: " + e.getMessage());
            e.printStackTrace();
            disconnect();
            return false;
        }
    }

    private void disconnectUsb() {
        try {
            if (deviceHandle != null) {
                if (isConnected) {
                    LibUsb.releaseInterface(deviceHandle, 0);
                    System.out.println("Released USB interface");
                }
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
            System.err.println("Error during USB disconnect: " + e.getMessage());
        } finally {
            outEndpoint = -1;
            inEndpoint = -1;
        }
    }

    private void sendUsbCommand(byte[] fullCommand, JsonNode command) {
        if (outEndpoint == -1) {
            System.err.println("No output endpoint available");
            return;
        }

        try {
            int bytesSent = usbBulkWrite(fullCommand, 5000);
            System.out.println("Sent " + bytesSent + " bytes successfully");

            if (isQueryCommand(command) && inEndpoint != -1) {
                readUsbResponse();
            }

        } catch (Exception e) {
            System.err.println("Failed to send USB command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean sendRawUsb(byte[] data) {
        if (outEndpoint == -1) {
            System.err.println("No output endpoint available");
            return false;
        }

        try {
            System.out.println("Sending " + data.length + " bytes to USB device");
            int bytesSent = usbBulkWrite(data, 10000);
            System.out.println("Sent " + bytesSent + " bytes successfully");
            return true;

        } catch (Exception e) {
            System.err.println("Failed to send raw USB data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private int usbBulkWrite(byte[] data, int timeoutMs) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.rewind();

        IntBuffer transferred = IntBuffer.allocate(1);
        int result = LibUsb.bulkTransfer(deviceHandle, outEndpoint, buffer, transferred, timeoutMs);

        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Failed to send data", result);
        }

        return transferred.get(0);
    }

    private void readUsbResponse() {
        ByteBuffer responseBuffer = ByteBuffer.allocateDirect(64);
        IntBuffer received = IntBuffer.allocate(1);

        int result = LibUsb.bulkTransfer(deviceHandle, inEndpoint, responseBuffer, received, 1000);

        if (result == LibUsb.SUCCESS && received.get(0) > 0) {
            byte[] responseData = new byte[received.get(0)];
            responseBuffer.get(responseData);
            System.out.println("Received response: " + bytesToHex(responseData));

            if (activePanel != null) {
                activePanel.updateResponse(responseData);
            }
        } else if (result == LibUsb.ERROR_TIMEOUT) {
            System.out.println("No response received (timeout)");
        } else if (result != LibUsb.SUCCESS) {
            System.err.println("Error reading response: " + LibUsb.errorName(result));
        }
    }

    private Device findUsbDevice(short vendorId, short productId) {
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(context, list);
        if (result < 0) {
            throw new LibUsbException("Unable to get device list", result);
        }

        try {
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                System.out.println("Found device - VID: " + formatHex16(descriptor.idVendor()) +
                        ", PID: " + formatHex16(descriptor.idProduct()));

                if (result != LibUsb.SUCCESS) {
                    continue;
                }

                if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId) {
                    LibUsb.refDevice(device);
                    return device;
                }
            }
            return null;
        } finally {
            LibUsb.freeDeviceList(list, false);
        }
    }

    private void detachKernelDriverIfNeeded() {
        int result = LibUsb.kernelDriverActive(deviceHandle, 0);
        if (result == 1) {
            result = LibUsb.detachKernelDriver(deviceHandle, 0);
            if (result != LibUsb.SUCCESS) {
                System.err.println("Cannot detach kernel driver: " + LibUsb.errorName(result));
            } else {
                System.out.println("Detached kernel driver");
            }
        }
    }

    private void claimInterface() {
        int result = LibUsb.claimInterface(deviceHandle, 0);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to claim interface", result);
        }
    }

    private void findEndpoints() {
        ConfigDescriptor config = new ConfigDescriptor();
        int result = LibUsb.getActiveConfigDescriptor(usbDevice, config);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to get config descriptor", result);
        }

        try {
            Interface iface = config.iface()[0];
            InterfaceDescriptor setting = iface.altsetting()[0];

            for (int i = 0; i < setting.bNumEndpoints(); i++) {
                EndpointDescriptor endpoint = setting.endpoint()[i];
                byte address = endpoint.bEndpointAddress();
                byte type = endpoint.bmAttributes();

                if ((type & LibUsb.TRANSFER_TYPE_MASK) == LibUsb.TRANSFER_TYPE_BULK) {
                    if ((address & LibUsb.ENDPOINT_IN) != 0) {
                        inEndpoint = address;
                        System.out.println("Found IN endpoint: " + formatHex8(address));
                    } else {
                        outEndpoint = address;
                        System.out.println("Found OUT endpoint: " + formatHex8(address));
                    }
                }
            }
        } finally {
            LibUsb.freeConfigDescriptor(config);
        }
    }

    private void logUsbDeviceInfo() {
        DeviceDescriptor descriptor = new DeviceDescriptor();
        int result = LibUsb.getDeviceDescriptor(usbDevice, descriptor);
        if (result != LibUsb.SUCCESS) {
            return;
        }

        System.out.println("Device info:");
        System.out.println("  USB Version: " + (descriptor.bcdUSB() >> 8) + "." + (descriptor.bcdUSB() & 0xFF));

        String manufacturer = getStringDescriptor(descriptor.iManufacturer());
        String product = getStringDescriptor(descriptor.iProduct());
        String serial = getStringDescriptor(descriptor.iSerialNumber());

        if (manufacturer != null) System.out.println("  Manufacturer: " + manufacturer);
        if (product != null) System.out.println("  Product: " + product);
        if (serial != null) System.out.println("  Serial Number: " + serial);
    }

    private String getStringDescriptor(byte index) {
        if (index == 0) {
            return null;
        }

        StringBuffer buffer = new StringBuffer();
        int result = LibUsb.getStringDescriptorAscii(deviceHandle, index, buffer);
        if (result < 0) {
            return null;
        }

        return buffer.toString();
    }

    // -------------------------------------------------------------------------
    // Serial Implementation
    // -------------------------------------------------------------------------

    private boolean connectSerial(String portStr) {
        try {
            System.out.println("Connecting to serial port: " + portStr);

            serialPort = new SerialPort(portStr);
            serialPort.openPort();
            serialPort.setParams(
                    57600,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE
            );

            isConnected = true;
            System.out.println("Successfully connected to serial port: " + portStr);
            return true;

        } catch (SerialPortException e) {
            System.err.println("Failed to open serial port: " + e.getMessage());
            return false;
        }
    }

    private void disconnectSerial() {
        if (serialPort != null) {
            try {
                if (serialPort.isOpened()) {
                    serialPort.closePort();
                    System.out.println("Closed serial port");
                }
            } catch (SerialPortException e) {
                System.err.println("Error closing serial port: " + e.getMessage());
            } finally {
                serialPort = null;
            }
        }
    }

    private void sendSerialCommand(byte[] data) {
        try {
            serialPort.writeBytes(data);
            System.out.println("Sent " + data.length + " bytes to serial port");

            Thread.sleep(100);
            tryReadSerialResponse();

        } catch (Exception e) {
            System.err.println("Failed to send serial command: " + e.getMessage());
        }
    }

    private boolean sendRawSerial(byte[] data) {
        try {
            serialPort.writeBytes(data);
            System.out.println("Sent " + data.length + " bytes to serial port");
            return true;
        } catch (SerialPortException e) {
            System.err.println("Failed to send raw serial data: " + e.getMessage());
            return false;
        }
    }

    private void tryReadSerialResponse() {
        if (activePanel == null) {
            return;
        }

        try {
            String response = serialPort.readString();
            if (response != null) {
                activePanel.updateResponse(response.getBytes(StandardCharsets.US_ASCII));
            }
        } catch (Exception e) {
            // Response read failure is non-fatal
        }
    }

    // -------------------------------------------------------------------------
    // Special Command Handling
    // -------------------------------------------------------------------------

    private boolean handleSpecialCommand(String commandName) {
        if ("printText".equals(commandName)) {
            return handlePrintText();
        }

        if ("streaming mode".equals(commandName)) {
            return handleStreamingMode();
        }

        if (commandName == null) {
            return handleNullCommand();
        }

        return false;
    }

    private boolean handlePrintText() {
        if (activePanel == null) {
            System.err.println("No active panel for printText command");
            return true;
        }
        byte[] printJobData = activePanel.getCommandData();
        sendRawData(printJobData);
        return true;
    }

    private boolean handleStreamingMode() {
        if (protocol != Protocol.USB_SERIAL || activePanel == null) {
            return true;
        }

        try {
            serialPort.writeString("/SRN?\r\n");
            Thread.sleep(100);
            tryReadSerialResponse();
        } catch (Exception e) {
            System.err.println("Streaming mode error: " + e.getMessage());
        }
        return true;
    }

    private boolean handleNullCommand() {
        if (protocol == Protocol.USB_SERIAL && activePanel != null) {
            tryReadSerialResponse();
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Utility Methods
    // -------------------------------------------------------------------------

    private void setProtocol() {
        if (currentLexicon.has(LexiconStrings.protocol)) {
            protocol = parseProtocol(currentLexicon.get(LexiconStrings.protocol)
                    .asText());
        } else {
            LogFatalAndExit(ErrStrLexiconMissingField(LexiconStrings.protocol));
        }
    }

    /* This does not belong here. Ontologically, it belongs with the lexicon,
    * but we haven't got that entirely planned out yet. */
    private Protocol parseProtocol(String protocolStr) {
        if (protocolStr == null) {
            return Protocol.UNKNOWN;
        }

        switch (protocolStr.toUpperCase()) {
            case "USB":
                return Protocol.USB;
            case "USB_SERIAL":
                return Protocol.USB_SERIAL;
            default:
                return Protocol.UNKNOWN;
        }
    }

    private String getLexiconString(String key, String defaultValue) {
        return currentLexicon.has(key) ? currentLexicon.get(key).asText() : defaultValue;
    }

    private void logConnectionAttempt() {
        String vendorIdStr = getLexiconString("vendorId", "");
        String productIdStr = getLexiconString("productId", "");
        System.out.println("Connecting to device - protocol: " + protocol +
                ", VID: " + vendorIdStr + ", PID: " + productIdStr);
    }

    private boolean isQueryCommand(JsonNode command) {
        if (!command.has("interface")) {
            return false;
        }

        JsonNode interfaceNode = command.get("interface");
        if (!interfaceNode.isArray()) {
            return false;
        }

        for (JsonNode iface : interfaceNode) {
            if ("query".equals(iface.asText())) {
                return true;
            }
        }
        return false;
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

    private short parseHexId(String hexStr) {
        String cleaned = hexStr.replace("0x", "").replace("0X", "");
        return (short) Integer.parseInt(cleaned, 16);
    }

    private byte[] parseHexBytes(String hexStr) {
        String cleaned = hexStr.replace("0x", "")
                .replace("0X", "")
                .replace(" ", "");
        int len = cleaned.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(cleaned.charAt(i), 16) << 4)
                    + Character.digit(cleaned.charAt(i + 1), 16));
        }
        return data;
    }

    private byte[] combineBytes(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private String formatHex16(short value) {
        return String.format("0x%04X", value & 0xFFFF);
    }

    private String formatHex8(byte value) {
        return String.format("0x%02X", value & 0xFF);
    }

    private void nonFatalError(String s) {
        if (headless) {
            LogSession(LogLevel.WARNING, "Non-fatal error\n" + s);
            System.err.println("Non-fatal error\n" + s);
        } else {
            try {
                LogSession(LogLevel.WARNING, "Non-fatal error\n" + s);
                JOptionPane.showMessageDialog(null, s,
                        "Non-fatal error", JOptionPane.ERROR_MESSAGE);
            } catch (RuntimeException e) {
                LogSession(LogLevel.WARNING, "Non-fatal error\n" + s);
                LogSessionExcp(LogLevel.WARNING, e.getMessage(), e);
            }
        }
    }

    private static final String CLASS =
            DeviceCommunicator.class.getSimpleName();
    private static final String ERR_STR_LEXICON_NOT_SET = CLASS + " attempted" +
            " to connect, but currentLexicon has not been set!\n";
    private static final String ERR_STR_LEXICON_NULL = CLASS + " attempted " +
            "to set the lexicon to null!\n";
    private static final String ERR_STR_PANEL_NULL = CLASS + " attempted to " +
            "to set the CommandPanel to null!\n";
    private static String ErrStrLexiconMissingField(String s) {
        return String.format("%s attempted to access a field [%s] in the " +
                "lexicon, but was unable to find it.\n", CLASS, s);
    }
}