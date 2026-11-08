package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.exception.BaseException;
import com.sky.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 店铺营业状态业务实现，营业状态保存在 Redis 中。
 */
@Service
@Slf4j
public class ShopServiceImpl implements ShopService {

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    private final RedisTemplate<String, Object> redisTemplate;

    public ShopServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取店铺营业状态；Redis 中尚无记录时默认按打烊处理。
     *
     * @return 1 表示营业中，0 表示打烊
     */
    @Override
    public Integer getStatus() {
        Object status = redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status instanceof Integer) {
            return (Integer) status;
        }
        return StatusConstant.DISABLE;
    }

    /**
     * 设置店铺营业状态。
     *
     * @param status 1 表示营业中，0 表示打烊
     */
    @Override
    public void setStatus(Integer status) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException(MessageConstant.SHOP_STATUS_ERROR);
        }

        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status);
        log.info("店铺营业状态已更新为：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊");
    }
}
