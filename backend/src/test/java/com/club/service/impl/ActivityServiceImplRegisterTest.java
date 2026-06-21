package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.common.Result;
import com.club.entity.Activity;
import com.club.entity.ActivityRegistration;
import com.club.mapper.ActivityMapper;
import com.club.mapper.RegistrationMapper;
import com.club.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceImplRegisterTest {

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private RegistrationMapper registrationMapper;

    @Mock
    private UserMapper userMapper;

    private ActivityServiceImpl activityService;

    @BeforeEach
    void setUp() throws Exception {
        activityService = new ActivityServiceImpl();
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(activityService, activityMapper);

        Field registrationMapperField = ActivityServiceImpl.class.getDeclaredField("registrationMapper");
        registrationMapperField.setAccessible(true);
        registrationMapperField.set(activityService, registrationMapper);

        Field userMapperField = ActivityServiceImpl.class.getDeclaredField("userMapper");
        userMapperField.setAccessible(true);
        userMapperField.set(activityService, userMapper);
    }

    @Test
    @DisplayName("register - 活动不存在，应返回错误")
    void register_whenActivityNotExists_shouldReturnError() {
        Integer activityId = 999;
        Integer userId = 1;

        when(activityMapper.selectById(activityId)).thenReturn(null);

        Result<?> result = activityService.register(activityId, userId);

        assertEquals(500, result.getCode());
        assertEquals("活动不存在", result.getMessage());
        verify(activityMapper).selectById(activityId);
        verifyNoInteractions(registrationMapper);
    }

    @Test
    @DisplayName("register - 重复报名，应返回错误")
    void register_whenAlreadyRegistered_shouldReturnError() {
        Integer activityId = 1;
        Integer userId = 1;
        Activity activity = buildActivity(activityId, 100);

        when(activityMapper.selectById(activityId)).thenReturn(activity);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        Result<?> result = activityService.register(activityId, userId);

        assertEquals(500, result.getCode());
        assertEquals("已报过名", result.getMessage());
        verify(activityMapper).selectById(activityId);
        verify(registrationMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(registrationMapper, never()).insert(any(ActivityRegistration.class));
    }

    @Test
    @DisplayName("register - 活动已满员，应返回错误")
    void register_whenActivityFull_shouldReturnError() {
        Integer activityId = 1;
        Integer userId = 2;
        Integer maxCount = 50;
        Activity activity = buildActivity(activityId, maxCount);

        when(activityMapper.selectById(activityId)).thenReturn(activity);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(Long.valueOf(maxCount));

        Result<?> result = activityService.register(activityId, userId);

        assertEquals(500, result.getCode());
        assertEquals("活动人数已报满", result.getMessage());
        verify(activityMapper).selectById(activityId);
        verify(registrationMapper, times(2)).selectCount(any(LambdaQueryWrapper.class));
        verify(registrationMapper, never()).insert(any(ActivityRegistration.class));
    }

    @Test
    @DisplayName("register - 成功报名，应返回成功并插入记录")
    void register_whenSuccess_shouldInsertAndReturnSuccess() {
        Integer activityId = 1;
        Integer userId = 3;
        Integer maxCount = 50;
        Activity activity = buildActivity(activityId, maxCount);

        when(activityMapper.selectById(activityId)).thenReturn(activity);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(Long.valueOf(maxCount - 1));
        when(registrationMapper.insert(any(ActivityRegistration.class))).thenReturn(1);

        Result<?> result = activityService.register(activityId, userId);

        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
        verify(activityMapper).selectById(activityId);
        verify(registrationMapper, times(2)).selectCount(any(LambdaQueryWrapper.class));
        verify(registrationMapper).insert(argThat(reg ->
                activityId.equals(reg.getActivityId())
                        && userId.equals(reg.getUserId())
                        && "REGISTERED".equals(reg.getStatus())
        ));
    }

    private Activity buildActivity(Integer id, Integer maxCount) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle("测试活动");
        activity.setMaxCount(maxCount);
        activity.setStatus("APPROVED");
        return activity;
    }
}
