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
        String message=ex.getMessage();
        if(message.contains("Duplicate entry")){
            String[] split=message.split(" ");
            String username=split[2];
            String msg =username+MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }
        else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }

}
