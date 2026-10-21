package com.sky;

import com.sky.config.WebMvcConfiguration;
import com.sky.controller.admin.EmployeeController;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.json.JacksonObjectMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebMvcDateFormatTest {

    static class TestConfiguration extends WebMvcConfiguration {
        void extend(List<HttpMessageConverter<?>> converters) {
            super.extendMessageConverters(converters);
        }
    }

    @Test
    void customConverterHasPriorityAndPreservesDefaultConverters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        StringHttpMessageConverter textConverter = new StringHttpMessageConverter();
        converters.add(textConverter);
        new TestConfiguration().extend(converters);
        assertEquals(2, converters.size());
        assertSame(textConverter, converters.get(1));
        assertTrue(converters.get(0) instanceof MappingJackson2HttpMessageConverter);
        MappingJackson2HttpMessageConverter jsonConverter =
                (MappingJackson2HttpMessageConverter) converters.get(0);
        assertTrue(jsonConverter.getObjectMapper() instanceof JacksonObjectMapper);
    }

    @Test
    void employeeResponseWritesTimesAsFormattedStrings() throws Exception {
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setCreateTime(LocalDateTime.of(2026, 10, 21, 9, 5, 30));
        employee.setUpdateTime(LocalDateTime.of(2026, 10, 21, 21, 30, 45));
        EmployeeService service = mock(EmployeeService.class);
        when(service.pageQuery(any(EmployeePageQueryDTO.class)))
                .thenReturn(new PageResult(1, Collections.singletonList(employee)));
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new TestConfiguration().extend(converters);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converters.toArray(new HttpMessageConverter<?>[0])).build();
        mvc.perform(get("/admin/employee/page").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].createTime").value("2026-10-21 09:05"))
                .andExpect(jsonPath("$.data.records[0].updateTime").value("2026-10-21 21:30"));
    }
}