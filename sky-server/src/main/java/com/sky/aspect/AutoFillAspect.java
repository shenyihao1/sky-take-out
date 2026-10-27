package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import com.sky.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 自定义切面，在 Mapper 执行前统一填充公共字段。 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void AutoFillPointCut() {}

    @Before("AutoFillPointCut()")
    public void AutoFillBefore(JoinPoint joinPoint) {
        // 获取 Mapper 方法上的操作类型和第一个参数（实体对象）。
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);//反射获取注解
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || args[0] == null) {
            throw new IllegalArgumentException("公共字段自动填充需要非空实体参数");
        }
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        Object entity = args[0];
        // 同一次操作使用同一时间，保证新增时创建时间和修改时间一致。
        LocalDateTime now = LocalDateTime.now();
        try {
            if (autoFill.value() == OperationType.INSERT) {
                entity.getClass().getMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class)
                        .invoke(entity, now);//getClass获取类的信息getMethod负责找方法invoke负责调用方法
                entity.getClass().getMethod(AutoFillConstant.SET_CREATE_USER, Long.class)
                        .invoke(entity, currentId);
            }
            // INSERT、UPDATE 都填充修改信息；UPDATE 不改动创建信息。
            entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class)
                    .invoke(entity, now);
            entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_USER, Long.class)
                    .invoke(entity, currentId);
        } catch (ReflectiveOperationException ex) {
            // 填充失败必须阻止 SQL 执行，不能保存缺失审计信息的数据。
            throw new IllegalStateException("公共字段自动填充失败：" + entity.getClass().getName(), ex);
        }
        log.debug("公共字段填充完成：{}，操作类型：{}", signature.getName(), autoFill.value());
    }
}