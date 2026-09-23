package com.mercenarywardrobe.data;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class SkinClassifier {
    public static String classify(NativeImage img) {
        if (img == null) return "skin";
        int head = 0;
        int face = 0;
        int torso = 0;
        int arms = 0;
        int legs = 0;
        int w = img.getWidth();
        int h = img.getHeight();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int pixel = img.getPixelRGBA(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha > 10) {
                    if (y < 16) {
                        head++;
                        if (x >= 8 && x < 16 && y >= 8 && y < 16) {
                            face++;
                        }
                    } else {
                        if (x >= 32) {
                            arms++;
                        } else if (y >= 48 || x < 16) {
                            legs++;
                        } else {
                            torso++;
                        }
                    }
                }
            }
        }

        int body = torso + arms + legs;
        if (body < 50 || (legs == 0 && body < 350)) {
            return "hair";
        } else if (face < 15) {
            return "clothing";
        } else {
            return "skin";
        }
    }

    public static String classify(BufferedImage img) {
        if (img == null) return "skin";
        int head = 0;
        int face = 0;
        int torso = 0;
        int arms = 0;
        int legs = 0;
        int w = img.getWidth();
        int h = img.getHeight();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha > 10) {
                    if (y < 16) {
                        head++;
                        if (x >= 8 && x < 16 && y >= 8 && y < 16) {
                            face++;
                        }
                    } else {
                        if (x >= 32) {
                            arms++;
                        } else if (y >= 48 || x < 16) {
                            legs++;
                        } else {
                            torso++;
                        }
                    }
                }
            }
        }

        int body = torso + arms + legs;
        if (body < 50 || (legs == 0 && body < 350)) {
            return "hair";
        } else if (face < 15) {
            return "clothing";
        } else {
            return "skin";
        }
    }

    public static String classify(File file) {
        if (file == null || !file.exists()) return null;
        try {
            BufferedImage img = ImageIO.read(file);
            return classify(img);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
