package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.Employee;
import com.github.pagehelper.Page;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee
     */
    @Insert("insert into employee (name, username, password, phone, sex, id_number, create_time, update_time, create_user, update_user, status) " +
            "values " +
            "(#{name}, #{username}, #{password}, #{phone}, #{sex}, #{idNumber}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{status})")
    @AutoFill(OperationType.INSERT)
    void insert(Employee employee);

    /**
     * 按姓名条件分页查询员工
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 查询员工资料，用于状态切换和编辑回显，不读取密码；SQL 定义在 EmployeeMapper.xml。
     */
    Employee getById(@Param("id") Long id);

    /**
     * 只更新状态与审计字段；EmployeeMapper.xml 中的 SQL 同时保护管理员账号。
     * @return 匹配更新的记录数，用于判断操作是否成功
     */
    @AutoFill(OperationType.UPDATE)
    int updateStatus(Employee employee);

    /** 只更新员工资料及操作信息。 */
    @AutoFill(OperationType.UPDATE)
    int update(Employee employee);
}
