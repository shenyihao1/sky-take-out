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
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        log.error("SQL约束异常：{}", ex.getMessage());
        // MySQL 1062 表示唯一键冲突，仅将用户名索引冲突提示为用户已存在。
        if (ex.getErrorCode() == 1062 && ex.getMessage() != null) {
            Matcher matcher = Pattern.compile(
                    "^Duplicate entry '(.+)' for key '(?:[^']+\\.)?idx_username'$",
                    Pattern.DOTALL).matcher(ex.getMessage());
            if (matcher.matches()) {
                return Result.error("用户 " + matcher.group(1) + " 已存在");
            }
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
