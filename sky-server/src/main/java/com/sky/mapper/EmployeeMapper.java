package com.sky.mapper;

import com.sky.entity.Employee;
import com.github.pagehelper.Page;
import com.sky.dto.EmployeePageQueryDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
    //TODO无快捷提示需要修改
    @Insert("insert into employee (name, username, password, phone, sex, id_number, create_time, update_time, create_user, update_user, status) " +
            "values " +
            "(#{name}, #{username}, #{password}, #{phone}, #{sex}, #{idNumber}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{status})")
    void insert(Employee employee);

    /**
     * 按姓名条件分页查询员工
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 查询状态切换所需的账号信息，不读取密码。
     */
    @Select("select id, username, status from employee where id = #{id}")
    Employee getById(@Param("id") Long id);

    /**
     * 只更新状态与审计字段，并在 SQL 层保护管理员账号。
     * @return 匹配更新的记录数，用于判断操作是否成功
     */
    @Update("update employee set status = #{status}, update_time = #{updateTime}, " +
            "update_user = #{updateUser} where id = #{id} and username <> 'admin'")
    int updateStatus(Employee employee);
}
