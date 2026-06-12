package com.example.seckill.common.interceptor;

import com.example.seckill.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * TraceId拦截器 - 为每个请求生成唯一traceId，便于日志追踪
 */
@Slf4j
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取请求中的traceId，不存在则生成新的
        String traceId = request.getHeader(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = String.valueOf(snowflakeIdGenerator.nextId());
        }
        
        // 设置到MDC中，日志中可以通过%X{traceId}引用
        MDC.put(TRACE_ID_KEY, traceId);
        
        // 设置响应头，方便客户端追踪
        response.setHeader(TRACE_ID_KEY, traceId);
        
        log.info("Request started - URI: {}, Method: {}, TraceId: {}", 
                request.getRequestURI(), request.getMethod(), traceId);
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除MDC中的traceId
        MDC.remove(TRACE_ID_KEY);
    }
}