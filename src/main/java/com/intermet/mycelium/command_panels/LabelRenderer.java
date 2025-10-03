package com.intermet.mycelium.command_panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class LabelRenderer {
    private static final int DPI = 300;
    private static final int PRINT_HEAD_DOTS = 672;

    /**
     * Renders text to a BufferedImage at 300 DPI
     * @param text The text to render
     * @param labelHeightInches Height of the label in inches
     * @return BufferedImage containing the rendered text
     */
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
        /* TODO: Next step */
        return null;
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