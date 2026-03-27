package com.yuwen.visionspace.utils;

public final class PictureVariantPathUtils {

    private PictureVariantPathUtils() {
    }

    public static String buildPreviewPath(String originalPath) {
        int lastSlash = originalPath.lastIndexOf('/');
        String parent = lastSlash >= 0 ? originalPath.substring(0, lastSlash) : "";
        String filename = lastSlash >= 0 ? originalPath.substring(lastSlash + 1) : originalPath;
        int lastDot = filename.lastIndexOf('.');
        String mainName = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        return parent + "/" + mainName + "_preview.webp";
    }
}
