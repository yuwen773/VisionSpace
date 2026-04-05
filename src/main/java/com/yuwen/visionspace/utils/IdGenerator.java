package com.yuwen.visionspace.utils;

import java.util.UUID;

/**
 * ID generation utility class
 */
public class IdGenerator {

    /**
     * Generate a unique ID string (UUID without dashes)
     */
    public static String idStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate a simple UUID string (with dashes)
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}