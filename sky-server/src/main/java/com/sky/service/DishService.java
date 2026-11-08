package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;

public interface DishService {
    /** 新增菜品及其口味。 */
    void saveWithFlavor(DishDTO dishDTO);
    /**分页查询*/
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);
    /** 删除菜品及口味；在售或关联套餐时拒绝整批删除。 */
    void deleteBatch(java.util.List<Long> ids);

    com.sky.vo.DishVO getByIdWithFlavor(Long id);
    void updateWithFlavor(DishDTO dto);
}