package com.sky.handler;

import com.sky.dto.EmployeeDTO;
import com.sky.exception.BaseException;
import com.sky.controller.admin.EmployeeController;
import com.sky.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证重复员工账号的错误响应；模拟服务异常，不连接或修改真实数据库。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private EmployeeService employeeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", employeeService);
        // 使用真实控制器和异常处理器验证响应格式；此测试不加载 JWT 拦截器。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler).build();
    }

    // 三种索引名称都应返回同一提示，避免 MySQL 返回的名称前缀影响处理。
    @ParameterizedTest
    @ValueSource(strings = {"idx_username", "employee.idx_username", "sky_take_out.idx_username"})
    void duplicateUsernameReturnsFriendlyMessage(String index) {
        SQLIntegrityConstraintViolationException exception = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'zhangsan' for key '" + index + "'", "23000", 1062);

        assertEquals(Integer.valueOf(0), handler.exceptionHandler(exception).getCode());
        assertEquals("用户 zhangsan 已存在", handler.exceptionHandler(exception).getMsg());
    }

    @Test
    void springWrappedDuplicateReturnsMessageExpectedByFrontend() throws Exception {
        // 模拟数据库异常被 Spring 包装后的调用链，确保仍由 SQL 异常处理方法接住。
        SQLIntegrityConstraintViolationException cause = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'zhangsan' for key 'employee.idx_username'", "23000", 1062);
        doThrow(new DuplicateKeyException("Insert failed", cause))
                .when(employeeService).save(any(EmployeeDTO.class));

        mockMvc.perform(employeeRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("用户 zhangsan 已存在"));
    }

    @Test
    void otherUniqueKeyIsNotReportedAsDuplicateUser() {
        // 主键冲突同样使用 1062，不能只凭错误码就判断为用户名重复。
        SQLIntegrityConstraintViolationException exception = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '42' for key 'PRIMARY'", "23000", 1062);

        assertEquals("未知错误", handler.exceptionHandler(exception).getMsg());
    }

    @Test
    void foreignKeyFailureIsNotReportedAsDuplicateUser() {
        SQLIntegrityConstraintViolationException exception = new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: a foreign key constraint fails", "23000", 1452);

        assertEquals("未知错误", handler.exceptionHandler(exception).getMsg());
    }

    @Test
    void missingSqlMessageDoesNotCauseAnotherException() {
        assertEquals("未知错误", handler.exceptionHandler(
                new SQLIntegrityConstraintViolationException(null, "23000", 1062)).getMsg());
    }

    @Test
    void unrecognizedDuplicateMessageDoesNotLeakDatabaseDetails() {
        assertEquals("未知错误", handler.exceptionHandler(
                new SQLIntegrityConstraintViolationException("Unexpected database details", "23000", 1062)).getMsg());
    }

    @Test
    void existingBusinessExceptionHandlingStillWorks() throws Exception {
        doThrow(new BaseException("账号被锁定"))
                .when(employeeService).save(any(EmployeeDTO.class));

        mockMvc.perform(employeeRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("账号被锁定"));
    }

    @Test
    void successfulEmployeeCreationStillWorks() throws Exception {
        // 新增成功仍返回业务码 1，新增的异常处理不影响正常响应。
        mockMvc.perform(employeeRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        verify(employeeService).save(any(EmployeeDTO.class));
    }

    private MockHttpServletRequestBuilder employeeRequest() {
        return post("/admin/employee").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"zhangsan\",\"name\":\"张三\",\"phone\":\"13800138000\","
                        + "\"sex\":\"1\",\"idNumber\":\"350102200501011234\"}");
    }
}
