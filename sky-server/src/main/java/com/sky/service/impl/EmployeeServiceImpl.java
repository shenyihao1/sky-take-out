package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.BaseException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;


@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        // 对前端过来的明文进行MD5加密处理
        password=DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }
    /**
     * 新增员工
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee=new Employee();
        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO,employee);
        //设置账号状态，默认正常
        employee.setStatus(StatusConstant.ENABLE);
        //设置密码，默认密码123456
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        employeeMapper.insert(employee);
    }

    /**
     * 员工分页查询
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 校验状态和目标员工，只更新状态及操作信息，保留账号的其他资料。
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException("员工状态只能为0（禁用）或1（启用）");
        }
        if (id == null || id <= 0) {
            throw new BaseException("员工ID必须为正整数");
        }
        Long operatorId = BaseContext.getCurrentId();
        if (operatorId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        // 前端已禁止操作 admin，后端同样校验，防止直接调用接口绕过页面限制。
        if ("admin".equals(employee.getUsername())) {
            throw new BaseException("管理员账号不允许修改状态");
        }
        if (status.equals(employee.getStatus())) {
            return;
        }
        Employee update = Employee.builder()
                .id(id)
                .status(status)
                .build();
        // 查询后记录可能被删除或变为受保护账号，不能把未更新的情况当作成功。
        if (employeeMapper.updateStatus(update) != 1) {
            throw new BaseException("员工状态更新失败，请刷新后重试");
        }
    }

    @Override
    public Employee getById(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException("员工ID必须为正整数");
        }
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        employee.setPassword("*****");
        return employee;
    }

    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee existing = getById(employeeDTO.getId());
        Long operatorId = BaseContext.getCurrentId();
        if (operatorId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        if ((employeeDTO.getUsername() != null && employeeDTO.getUsername().trim().isEmpty())
                || (employeeDTO.getName() != null && employeeDTO.getName().trim().isEmpty())) {
            throw new BaseException("员工账号和姓名不能为空");
        }
        // 保持管理员账号标识，避免绕过已有的管理员状态保护。
        if ("admin".equals(existing.getUsername()) && employeeDTO.getUsername() != null
                && !"admin".equals(employeeDTO.getUsername())) {
            throw new BaseException("管理员账号不允许修改登录账号");
        }
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        if (employeeMapper.update(employee) != 1) {
            throw new BaseException("员工信息更新失败，请刷新后重试");
        }
    }
}
