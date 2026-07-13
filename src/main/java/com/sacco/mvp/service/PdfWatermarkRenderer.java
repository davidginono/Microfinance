package com.sacco.mvp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class PdfWatermarkRenderer {
    private static final float LOGO_ALPHA = 0.075f;
    private static final float STAMP_ALPHA = 0.11f;

    private PdfWatermarkRenderer() {
    }

    static void draw(PDDocument document, PDPageContentStream stream, PDPage page, byte[] logoBytes) throws IOException {
        BufferedImage image = readImage(logoBytes);
        boolean logo = image != null;
        if (!logo) {
            image = systemStampImage();
        }
        PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
        PDRectangle box = page.getMediaBox();
        float size = Math.min(Math.min(box.getWidth(), box.getHeight()) * 0.72f, 430f);
        float x = (box.getWidth() - size) / 2f;
        float y = (box.getHeight() - size) / 2f;

        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(logo ? LOGO_ALPHA : STAMP_ALPHA);
        state.setAlphaSourceFlag(true);
        stream.saveGraphicsState();
        stream.setGraphicsStateParameters(state);
        stream.drawImage(pdfImage, x, y, size, size);
        stream.restoreGraphicsState();
    }

    private static BufferedImage readImage(byte[] logoBytes) {
        if (logoBytes == null || logoBytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(logoBytes));
        } catch (IOException ex) {
            return null;
        }
    }

    private static BufferedImage systemStampImage() {
        int size = 1000;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color blue = new Color(122, 164, 221, 150);
            g.setColor(blue);
            g.setStroke(new BasicStroke(12f));
            g.drawOval(48, 48, 904, 904);
            g.setStroke(new BasicStroke(7f));
            g.drawOval(76, 76, 848, 848);
            g.setStroke(new BasicStroke(7f));
            g.drawOval(210, 210, 580, 580);
            drawCentered(g, "LOAN APPLICATION PORTAL", 500, 345, 72, blue);
            drawCentered(g, "TRUST  -  TRANSPARENCY  -  EMPOWERMENT", 500, 690, 42, blue);
            g.fillOval(130, 490, 24, 24);
            g.fillOval(846, 490, 24, 24);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int y, int size, Color color) {
        g.setColor(color);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics metrics = g.getFontMetrics();
        int x = centerX - metrics.stringWidth(text) / 2;
        g.drawString(text, x, y);
    }
}
