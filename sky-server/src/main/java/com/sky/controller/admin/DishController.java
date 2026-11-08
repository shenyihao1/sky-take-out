package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import com.sky.result.Result;
import com.sky.service.DishService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/admin/dish")
@RestController
@Api(tags="菜品相关接口")
public class DishController {
    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @Autowired
    private DishService dishService;
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品:{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }
    /**
     * 分页查询
     */
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public  Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("分页查询：{}",dishPageQueryDTO);
        PageResult pageResult=dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }
    /** 批量删除菜品，支持单个 ID。 */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result delete(@RequestParam("ids") java.util.List<Long> ids) {
        log.info("菜品删除：{}",ids);
        dishService.deleteBatch(ids);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("查询菜品及口味")
    public Result<com.sky.vo.DishVO> getById(@PathVariable("id") Long id) {
        return Result.success(dishService.getByIdWithFlavor(id));
    }
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dto) {
        dishService.updateWithFlavor(dto);
        return Result.success();
    }
}
