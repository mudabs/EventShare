package com.eventshare.api.demo;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

/**
 * Draws simple, deterministic "landscape" JPEGs for the demo gallery: a gradient sky,
 * a sun or moon, layered hills and a few bokeh lights. No text is drawn, so it works
 * on slim JRE images without fonts, and no third-party photos are needed (nothing to
 * license). The same seed always produces the same bytes, which the seeder uses to
 * create one intentional exact duplicate.
 */
final class DemoImageGenerator {

    private static final Color[][] PALETTES = {
            {new Color(0xFFB88C), new Color(0xDE6262), new Color(0x4A2C4F)},  // sunset
            {new Color(0x89F7FE), new Color(0x66A6FF), new Color(0x1F3B73)},  // daylight
            {new Color(0xFDE68A), new Color(0xF59E0B), new Color(0x7C2D12)},  // golden hour
            {new Color(0x2E3192), new Color(0x1BFFFF), new Color(0x0B1D3A)},  // dusk
            {new Color(0xF8CDDA), new Color(0x1D2B64), new Color(0x2B1B3D)},  // evening
            {new Color(0xC6FFDD), new Color(0xFBD786), new Color(0x3F5E3A)},  // garden
    };

    private DemoImageGenerator() {
    }

    /** Returns JPEG bytes for the given seed; every third image (index % 3 == 2) is portrait. */
    static byte[] jpeg(long seed, int index) {
        boolean landscape = index % 3 != 2;
        int width = landscape ? 1600 : 1067;
        int height = landscape ? 1067 : 1600;
        Random random = new Random(seed);
        Color[] palette = PALETTES[(int) Math.floorMod(seed, (long) PALETTES.length)];

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Sky
            g.setPaint(new GradientPaint(0, 0, palette[0], 0, height * 0.75f, palette[1]));
            g.fillRect(0, 0, width, height);

            // Sun or moon
            int sunR = (int) (Math.min(width, height) * (0.08 + random.nextDouble() * 0.07));
            int sunX = (int) (width * (0.15 + random.nextDouble() * 0.7));
            int sunY = (int) (height * (0.15 + random.nextDouble() * 0.3));
            g.setColor(new Color(255, 250, 230, 220));
            g.fill(new Ellipse2D.Double(sunX - sunR, sunY - sunR, sunR * 2.0, sunR * 2.0));

            // Bokeh lights
            for (int i = 0; i < 14; i++) {
                int r = 10 + random.nextInt(40);
                g.setColor(new Color(255, 255, 255, 25 + random.nextInt(50)));
                g.fill(new Ellipse2D.Double(random.nextInt(width), random.nextInt((int) (height * 0.6)), r, r));
            }

            // Three layers of hills, darker toward the front
            for (int layer = 0; layer < 3; layer++) {
                float t = (layer + 1) / 3f;
                g.setColor(blend(palette[1], palette[2], 0.35f + 0.65f * t));
                double base = height * (0.55 + 0.13 * layer);
                Path2D hill = new Path2D.Double();
                hill.moveTo(0, height);
                hill.lineTo(0, base);
                double amp = height * (0.04 + random.nextDouble() * 0.06);
                double freq = 1.5 + random.nextDouble() * 2.5;
                double phase = random.nextDouble() * Math.PI * 2;
                for (int x = 0; x <= width; x += 20) {
                    hill.lineTo(x, base - Math.sin(x / (double) width * Math.PI * freq + phase) * amp);
                }
                hill.lineTo(width, height);
                hill.closePath();
                g.fill(hill);
            }

            // A thin string of "fairy lights" across the frame
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 255, 255, 90));
            double sag = height * 0.08;
            Path2D wire = new Path2D.Double();
            wire.moveTo(0, height * 0.12);
            wire.quadTo(width / 2.0, height * 0.12 + sag * 2, width, height * 0.1);
            g.draw(wire);
            for (int i = 1; i < 12; i++) {
                double x = width * i / 12.0;
                double tt = x / width;
                double y = (1 - tt) * (1 - tt) * height * 0.12 + 2 * (1 - tt) * tt * (height * 0.12 + sag * 2)
                        + tt * tt * height * 0.1;
                g.setColor(new Color(255, 236, 170, 230));
                g.fill(new Ellipse2D.Double(x - 7, y - 2, 14, 14));
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024)) {
            if (!ImageIO.write(image, "jpg", out)) {
                throw new IOException("No JPEG writer available");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
