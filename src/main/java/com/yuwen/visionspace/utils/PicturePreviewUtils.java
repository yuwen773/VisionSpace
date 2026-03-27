package com.yuwen.visionspace.utils;

public final class PicturePreviewUtils {

    private PicturePreviewUtils() {
    }

    public static int[] limitDimensions(int width, int height, int maxEdge) {
        if (width <= 0 || height <= 0 || maxEdge <= 0) {
            throw new IllegalArgumentException("width, height, and maxEdge must be positive");
        }
        int longestEdge = Math.max(width, height);
        if (longestEdge <= maxEdge) {
            return new int[]{width, height};
        }
        double scale = maxEdge * 1.0 / longestEdge;
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        return new int[]{scaledWidth, scaledHeight};
    }
}
