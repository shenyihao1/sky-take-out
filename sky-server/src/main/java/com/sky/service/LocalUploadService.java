package com.sky.service;

import com.sky.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@Slf4j
public class LocalUploadService {
    private static final long MAX_SIZE = 2 * 1024 * 1024;
    private final Path root;
    private final String baseUrl;

    public LocalUploadService(@Value("${sky.upload.path:./uploads}") String path,
                              @Value("${sky.upload.base-url:http://localhost/images}") String baseUrl) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BaseException("请选择上传图片");
        if (file.getSize() > MAX_SIZE) throw new BaseException("图片不能超过2MB");
        String name = file.getOriginalFilename();
        String ext = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("jpg", "jpeg", "png", "gif").contains(ext))
            throw new BaseException("仅支持JPG、PNG、GIF图片");
        Path target = null;
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_SIZE) throw new BaseException("图片不能超过2MB");
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BaseException("图片内容无效");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String expected = "jpg".equals(ext) ? "jpeg" : ext;
                    if (!expected.equals(format) || (long) reader.getWidth(0) * reader.getHeight(0) > 20000000L)
                        throw new BaseException("图片格式不匹配或分辨率过大");
                    if (reader.read(0) == null) throw new BaseException("图片内容无效");
                } finally { reader.dispose(); }
            }
            Files.createDirectories(root);
            target = Files.createTempFile(root, UUID.randomUUID().toString() + "-", "." + ext);
            Files.write(target, bytes);
            return baseUrl + "/" + target.getFileName();
        } catch (IOException ex) {
            if (target != null) {
                try { Files.deleteIfExists(target); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            }
            log.error("本地图片上传失败", ex);
            throw new BaseException("图片上传失败，请检查图片内容或联系管理员");
        }
    }
}