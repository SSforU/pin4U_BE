package io.github.ssforu.pin4u.common.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true) // Swagger UI에서 파라미터로 노출되지 않게 설정
public @interface LoginUser {
    /**
     * true면 로그인하지 않은 경우 예외(401)를 발생시킴.
     * false면 null을 반환 (기본값).
     */
    boolean required() default false;
}