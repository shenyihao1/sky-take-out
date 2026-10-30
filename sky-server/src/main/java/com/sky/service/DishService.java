package com.sky.service;

import com.sky.dto.DishDTO;

public interface DishService {
    /** 新增菜品及其口味。 */
    void saveWithFlavor(DishDTO dishDTO);
}