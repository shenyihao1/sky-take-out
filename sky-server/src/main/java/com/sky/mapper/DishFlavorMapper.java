package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishFlavorMapper {
    /** 批量插入菜品口味。 */
    void insertBatch(@Param("flavors") List<DishFlavor> flavors);
}