package com.intermet.mycelium.command_panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class LabelRenderer {
    /* Device specific for Dymo LabelWriter 450 */
    private static final int DPI = 300;
    private static final int PRINT_HEAD_DOTS = 672;

    /* Renders a str (user input) to a bitmap, stored as a BufferedImage.
       To later be ingested by PrintLabelPanel.imageToRasterLines */
    public static BufferedImage renderTextToImage(String text, double labelHeightInches) {
        int imageWidth = PRINT_HEAD_DOTS;
        int imageHeight = (int)(labelHeightInches * DPI);

        BufferedImage image = new BufferedImage(
                imageWidth,
                imageHeight,
                BufferedImage.TYPE_BYTE_BINARY
        );

        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, imageWidth, imageHeight);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 48));  // Adjust size as needed

        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (imageWidth - textWidth) / 2;
        int y = (imageHeight + fm.getAscent()) / 2;

        g2d.drawString(text, x, y);
        g2d.dispose();

        return image;
    }

    /**
     * Converts BufferedImage to array of raster lines
     * @param image The image to convert
     * @return Array where each element is one raster line (84 bytes)
     */
    public static byte[][] imageToRasterLines(BufferedImage image) {
        int height = image.getHeight();
        byte[][] rasterLines = new byte[height][84];  // 84 bytes per line

        // Convert each horizontal line of pixels to bytes
        for (int y = 0; y < height; y++) {
            for (int byteIdx = 0; byteIdx < 84; byteIdx++) {
                byte currentByte = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = byteIdx * 8 + bit;
                    if (x < image.getWidth()) {
                        int pixel = image.getRGB(x, y);
                        // Black pixel = 1 bit, white = 0
                        if ((pixel & 0xFFFFFF) == 0) {  // Check if black
                            currentByte |= (1 << (7 - bit));  // MSB first
                        }
                    }
                }
                rasterLines[y][byteIdx] = currentByte;
            }
        }

        return rasterLines;
    }

    /* The lexicon files needs to encode how to do the work done here */
    public static byte[] buildPrintJob(byte[][] rasterLines, double labelHeightInches) {
        int numLines = rasterLines.length;
        int labelLength = numLines;  // Label length in dot lines

        // Calculate total buffer size
        int bufferSize = 3           // ESC D (set bytes per line)
                + 4           // ESC L (set label length)
                + 2           // ESC h (set 300x300 dpi)
                + (numLines * 85)  // Each line: SYN + 84 bytes
                + 2;          // ESC E (form feed)

        byte[] printJob = new byte[bufferSize];
        int pos = 0;

        // ESC D 84 - Set bytes per line to 84 (full width)
        printJob[pos++] = 0x1B;  // ESC
        printJob[pos++] = 0x44;  // D
        printJob[pos++] = 84;    // 84 bytes per line

        // ESC L n1 n2 - Set label length (LSB, MSB)
        printJob[pos++] = 0x1B;  // ESC
        printJob[pos++] = 0x4C;  // L
        printJob[pos++] = (byte)(labelLength & 0xFF);        // LSB
        printJob[pos++] = (byte)((labelLength >> 8) & 0xFF); // MSB

        // ESC h - Set to 300x300 dpi text mode
        printJob[pos++] = 0x1B;  // ESC
        printJob[pos++] = 0x68;  // h

        // Send each raster line: SYN + 84 bytes
        for (int i = 0; i < numLines; i++) {
            printJob[pos++] = 0x16;  // SYN
            System.arraycopy(rasterLines[i], 0, printJob, pos, 84);
            pos += 84;
        }

        // ESC E - Form feed (eject label)
        printJob[pos++] = 0x1B;  // ESC
        printJob[pos++] = 0x45;  // E

        return printJob;
    }

    /**
     * Test method - saves rendered image to file for visual verification
     * @param text Text to render
     * @param labelHeightInches Label height in inches
     * @param outputPath Where to save the image (e.g., "test_label.png")
     */
    public static void saveTestImage(String text, double labelHeightInches, String outputPath) {
        try {
            BufferedImage image = renderTextToImage(text, labelHeightInches);
            File outputFile = new File(outputPath);
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Test image saved to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save test image: " + e.getMessage());
            e.printStackTrace();
        }
    }
}