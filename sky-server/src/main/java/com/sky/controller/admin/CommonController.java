package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.LocalUploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {
    private final LocalUploadService uploadService;

    public CommonController(LocalUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ApiOperation("上传图片到本地文件夹")
    public Result<String> upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        return Result.success(uploadService.upload(file));
    }
}