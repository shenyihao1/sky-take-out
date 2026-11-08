package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /** 新增菜品，并使用回填的菜品 ID 保存口味。 */
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dish.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }
    /**
     * 分页查询
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO){
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> page=dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }
    @Autowired
    private com.sky.mapper.SetmealDishMapper setmealDishMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        List<Long> uniqueIds = new java.util.ArrayList<>(new java.util.TreeSet<>(ids));
        for (Long id : uniqueIds) {
            Dish dish = dishMapper.getByIdForUpdate(id);
            if (dish == null) {
                throw new com.sky.exception.DeletionNotAllowedException("菜品不存在");
            }
            if (com.sky.constant.StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new com.sky.exception.DeletionNotAllowedException(com.sky.constant.MessageConstant.DISH_ON_SALE);
            }
        }
        if (setmealDishMapper.countByDishIds(uniqueIds) > 0) {
            throw new com.sky.exception.DeletionNotAllowedException(com.sky.constant.MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        dishFlavorMapper.deleteByDishIds(uniqueIds);
        dishMapper.deleteByIds(uniqueIds);
    }

    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = dishMapper.getById(id);
        if (dish == null) throw new com.sky.exception.BaseException("菜品不存在");
        DishVO vo = new DishVO();
        BeanUtils.copyProperties(dish, vo);
        vo.setFlavors(dishFlavorMapper.getByDishId(id));
        return vo;
    }
    /** 口味全量替换，空列表或null表示清空。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithFlavor(DishDTO dto) {
        if (dto == null || dto.getId() == null)
            throw new com.sky.exception.BaseException("菜品ID不能为空");
        if (dishMapper.getByIdForUpdate(dto.getId()) == null)
            throw new com.sky.exception.BaseException("菜品不存在");
        Dish dish = new Dish();
        BeanUtils.copyProperties(dto, dish);
        dishMapper.update(dish);
        dishFlavorMapper.deleteByDishIds(java.util.Collections.singletonList(dto.getId()));
        List<DishFlavor> flavors = dto.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dto.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }
}