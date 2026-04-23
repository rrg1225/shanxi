package com.aidecomposer.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Md5Utils {
    public static String md5Hex(String text) {
        if (text == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("md5 compute failed", e);
        }
    }
}

