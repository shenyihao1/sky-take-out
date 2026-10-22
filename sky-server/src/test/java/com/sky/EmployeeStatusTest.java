package com.sky;

import com.sky.context.BaseContext;
import com.sky.controller.admin.EmployeeController;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.impl.EmployeeServiceImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 使用真实控制器和服务验证状态切换，模拟 Mapper，不连接或修改真实数据库。
 * JWT 鉴权不在本测试范围内，操作人通过 BaseContext 模拟。
 */
class EmployeeStatusTest {

    private EmployeeMapper mapper;
    private EmployeeServiceImpl service;
    private Employee employee;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
        mapper = mock(EmployeeMapper.class);
        service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        employee = Employee.builder().id(7L).username("zhangsan").status(1)
                .password(DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8))).build();
        when(mapper.getById(7L)).thenReturn(employee);
        when(mapper.updateStatus(any(Employee.class))).thenAnswer(invocation -> {
            Employee update = invocation.getArgument(0);
            employee.setStatus(update.getStatus());
            return 1;
        });
    }

    @AfterEach
    void clearCurrentUser() {
        BaseContext.removeCurrentId();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void togglesStatusUsingFrontendRequestAndRecordsOperator(int targetStatus) throws Exception {
        employee.setStatus(1 - targetStatus);
        LocalDateTime before = LocalDateTime.now();
        mvc.perform(post("/admin/employee/status/{status}", targetStatus).param("id", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(mapper).updateStatus(captor.capture());
        Employee update = captor.getValue();
        assertEquals(7L, update.getId().longValue());
        assertEquals(targetStatus, update.getStatus().intValue());
        assertEquals(1L, update.getUpdateUser().longValue());
        assertFalse(update.getUpdateTime().isBefore(before));
        assertFalse(update.getUpdateTime().isAfter(LocalDateTime.now()));
        assertNull(update.getPassword());
        assertNull(update.getUsername());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void repeatingSameStatusSucceedsWithoutUnnecessaryWrite(int targetStatus) throws Exception {
        employee.setStatus(targetStatus);
        mvc.perform(post("/admin/employee/status/{status}", targetStatus).param("id", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        verify(mapper, never()).updateStatus(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void invalidStatusDoesNotReachDatabase(int invalidStatus) throws Exception {
        mvc.perform(post("/admin/employee/status/{status}", invalidStatus).param("id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("员工状态只能为0（禁用）或1（启用）"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonpositiveIdIsRejected(String id) throws Exception {
        mvc.perform(post("/admin/employee/status/0").param("id", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(mapper);
    }

    @Test
    void missingIdIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(post("/admin/employee/status/0")).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }

    @Test
    void malformedIdIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(post("/admin/employee/status/0").param("id", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }

    @Test
    void malformedStatusIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(post("/admin/employee/status/abc").param("id", "7"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }

    @Test
    void nullArgumentsAreRejectedByService() {
        assertThrows(BaseException.class, () -> service.startOrStop(null, 7L));
        assertThrows(BaseException.class, () -> service.startOrStop(0, null));
        verifyNoInteractions(mapper);
    }

    @Test
    void nonexistentEmployeeIsNotReportedAsSuccess() throws Exception {
        mvc.perform(post("/admin/employee/status/0").param("id", "999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("账号不存在"));
        verify(mapper, never()).updateStatus(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void adminCannotBeChangedByCallingEndpointDirectly(int targetStatus) throws Exception {
        employee.setUsername("admin");
        mvc.perform(post("/admin/employee/status/{status}", targetStatus).param("id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("管理员账号不允许修改状态"));
        verify(mapper, never()).updateStatus(any());
    }

    @Test
    void missingOperatorDoesNotWriteNullAuditUser() {
        BaseContext.removeCurrentId();
        BaseException error = assertThrows(BaseException.class, () -> service.startOrStop(0, 7L));
        assertEquals("用户未登录", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void zeroAffectedRowsIsNotReportedAsSuccess() throws Exception {
        when(mapper.updateStatus(any())).thenReturn(0);
        mvc.perform(post("/admin/employee/status/0").param("id", "7"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void disablingBlocksLoginAndEnablingRestoresLogin() {
        when(mapper.getByUsername("zhangsan")).thenReturn(employee);
        EmployeeLoginDTO credentials = new EmployeeLoginDTO();
        credentials.setUsername("zhangsan");
        credentials.setPassword("123456");
        assertSame(employee, service.login(credentials));
        service.startOrStop(0, 7L);
        assertThrows(AccountLockedException.class, () -> service.login(credentials));
        service.startOrStop(1, 7L);
        assertSame(employee, service.login(credentials));
    }

    @Test
    void mybatisBindsStatusAuditFieldsAndTargetId() throws Exception {
        // 验证真实 MyBatis 参数绑定；只更新状态与审计字段，避免覆盖员工其他资料。
        Configuration configuration = new Configuration();
        configuration.addMapper(EmployeeMapper.class);
        MappedStatement statement = configuration.getMappedStatement(
                "com.sky.mapper.EmployeeMapper.updateStatus");
        Employee update = Employee.builder().id(7L).status(0).updateUser(1L)
                .updateTime(LocalDateTime.of(2026, 10, 22, 12, 0)).build();
        BoundSql sql = statement.getBoundSql(update);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, update, sql).setParameters(preparedStatement);
        verify(preparedStatement).setInt(1, 0);
        verify(preparedStatement).setObject(2, update.getUpdateTime());
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).setLong(4, 7L);
        assertTrue(sql.getSql().contains("where id = ?"));
        assertTrue(sql.getSql().contains("username <> 'admin'"));
        assertFalse(sql.getSql().contains("password"));
        assertFalse(sql.getSql().contains("create_user"));
    }
}
