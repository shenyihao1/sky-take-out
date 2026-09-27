package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL约束异常，向前端返回重复账号提示。
     * Spring 会沿异常原因链匹配此方法，兼容 DuplicateKeyException 包装的 SQL 异常。
     * @param ex 数据库返回的完整性约束异常
     * @return 业务失败结果；前端读取 code 和 msg 显示提示，不代表插入成功
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        log.error("SQL约束异常：{}", ex.getMessage());
        // MySQL 1062 表示唯一键冲突，仅将用户名索引冲突提示为用户已存在。
        if (ex.getErrorCode() == 1062 && ex.getMessage() != null) {
            // 兼容 idx_username 和 employee.idx_username 等索引名称，捕获组 1 为重复账号。
            Matcher matcher = Pattern.compile(
                    "^Duplicate entry '(.+)' for key '(?:[^']+\\.)?idx_username'$",
                    Pattern.DOTALL).matcher(ex.getMessage());
            if (matcher.matches()) {
                // 沿用前端现有约定：HTTP 200、业务码 0，并通过 msg 展示失败原因。
                return Result.error("用户 " + matcher.group(1) + " 已存在");
            }
        }
        // 其他约束错误或无法识别的消息不误报为用户重复，也不向前端暴露 SQL 细节。
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
