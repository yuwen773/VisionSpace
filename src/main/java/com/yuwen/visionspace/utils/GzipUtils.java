package com.yuwen.visionspace.utils;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class GzipUtils {

    /**
     * 压缩字符串为 byte[]
     * @param data 待压缩的字符串
     * @return 压缩后的 byte[]，null 输入返回空数组
     * @throws IOException 压缩失败时抛出
     */
    public byte[] compress(String data) throws IOException {
        if (data == null) return new byte[0];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    /**
     * 解压 byte[] 为字符串
     * @param compressed 压缩后的 byte[]
     * @return 解压后的字符串，null 或空数组返回空字符串
     * @throws IOException 解压失败时抛出
     */
    public String decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) return "";
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            gis.transferTo(bos);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}
