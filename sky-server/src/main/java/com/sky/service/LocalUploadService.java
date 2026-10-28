package com.sky.service;

import com.sky.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class LocalUploadService {
    private final Path root;
    private final String baseUrl;

    public LocalUploadService(@Value("${sky.upload.path:./uploads}") String path,
                              @Value("${sky.upload.base-url:http://localhost/images}") String baseUrl) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BaseException("请选择上传图片");

        //截取文件名后缀
        String originalFilename = file.getOriginalFilename();
        int index = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        String suffix = index < 0 ? "" : originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("jpg", "jpeg", "png", "gif").contains(suffix)) {
            throw new BaseException("仅支持JPG、PNG、GIF图片");
        }

        // 使用 UUID 构建新名称，避免同名文件覆盖
        String filename = UUID.randomUUID().toString() + "." + suffix;
        try {
            Files.createDirectories(root);
            file.transferTo(root.resolve(filename).toFile());
            // 返回 Nginx 对外提供的图片访问路径
            return baseUrl + "/" + filename;
        } catch (IOException | IllegalStateException ex) {
            log.error("本地图片上传失败", ex);
            throw new BaseException("文件上传失败");
        }
    }
}