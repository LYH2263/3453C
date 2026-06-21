package com.club.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    @DisplayName("Result.success() 应返回 code=0 且 message=success")
    void success_shouldReturnCorrectCodeAndMessage() {
        Result<String> result = Result.success("hello");
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    @DisplayName("Result.success(null) 应允许空数据")
    void success_withNullData_shouldSucceed() {
        Result<?> result = Result.success(null);
        assertEquals(0, result.getCode());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("Result.error() 应返回 code=-1 且携带错误消息")
    void error_shouldReturnErrorCodeAndMessage() {
        String errorMsg = "something went wrong";
        Result<?> result = Result.error(errorMsg);
        assertEquals(-1, result.getCode());
        assertEquals(errorMsg, result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("Result 无参构造（MyBatis/JSON 需要）应能正常实例化")
    void noArgsConstructor_shouldInstantiate() {
        Result<Object> result = new Result<>();
        assertNotNull(result);
    }
}
