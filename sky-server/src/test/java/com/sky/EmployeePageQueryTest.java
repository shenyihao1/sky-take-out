package com.sky;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.controller.admin.EmployeeController;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import com.sky.service.impl.EmployeeServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmployeePageQueryTest {

    @AfterEach
    void clearMockPaginationContext() {
        PageHelper.clearPage();
    }

    @Test
    void endpointBindsQueryAndReturnsResultPageResult() throws Exception {
        EmployeeService service = mock(EmployeeService.class);
        when(service.pageQuery(any())).thenAnswer(invocation -> {
            EmployeePageQueryDTO dto = invocation.getArgument(0);
            assertEquals(2, dto.getPage());
            assertEquals(5, dto.getPageSize());
            assertEquals("张", dto.getName());
            return new PageResult(12, Collections.emptyList());
        });
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/admin/employee/page")
                        .param("page", "2").param("pageSize", "5").param("name", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.records").isArray());
        verify(service).pageQuery(any());
    }

    @Test
    void serviceStartsPaginationAndKeepsTotalSeparateFromPageSize() {
        EmployeePageQueryDTO dto = new EmployeePageQueryDTO();
        dto.setPage(2);
        dto.setPageSize(5);
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        Employee employee = new Employee();
        employee.setId(7L);
        when(mapper.pageQuery(dto)).thenAnswer(invocation -> {
            Page<Employee> page = PageHelper.getLocalPage();
            assertEquals(2, page.getPageNum());
            assertEquals(5, page.getPageSize());
            page.setTotal(12);
            page.add(employee);
            return page;
        });
        EmployeeServiceImpl service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        PageResult result = service.pageQuery(dto);
        assertEquals(12, result.getTotal());
        assertEquals(Collections.singletonList(employee), result.getRecords());
    }

    @Test
    void dynamicSqlOmitsNameFilterForNullEmptyAndWhitespace() throws Exception {
        for (String name : new String[]{null, "", "   "}) {
            BoundSql boundSql = sqlFor(name);
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            assertFalse(sql.contains("where"));
            assertTrue(sql.endsWith("order by create_time desc, id desc"));
            assertTrue(boundSql.getParameterMappings().isEmpty());
        }
    }

    @Test
    void dynamicSqlBindsNameAsParameter() throws Exception {
        String name = "张' OR 1=1 --";
        BoundSql boundSql = sqlFor(name);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("WHERE name like concat('%', ?, '%')"));
        assertFalse(sql.contains(name));
        assertFalse(sql.contains("password"));
        assertEquals("name", boundSql.getParameterMappings().get(0).getProperty());
    }

    private BoundSql sqlFor(String name) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/EmployeeMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        EmployeePageQueryDTO dto = new EmployeePageQueryDTO();
        dto.setName(name);
        return configuration.getMappedStatement("com.sky.mapper.EmployeeMapper.pageQuery")
                .getBoundSql(dto);
    }
}
