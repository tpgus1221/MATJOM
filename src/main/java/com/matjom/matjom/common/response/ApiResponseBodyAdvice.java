package com.matjom.matjom.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        if (ApiResponse.class.isAssignableFrom(parameterType)) {
            return false;
        }
        if (ResponseEntity.class.isAssignableFrom(parameterType)) {
            return false;
        }
        if (String.class.isAssignableFrom(parameterType)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?> apiResponse) {
            return apiResponse;
        }

        if (body instanceof ResponseEntity<?> responseEntity) {
            return responseEntity;
        }

        if (body == null) {
            return ApiResponse.ok();
        }

        return ApiResponse.ok(body);
    }
}