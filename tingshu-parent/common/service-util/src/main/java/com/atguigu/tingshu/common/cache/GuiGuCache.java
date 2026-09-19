package com.atguigu.tingshu.common.cache;

import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @date: 2024/9/21 14:16
 * @author: yz
 * @version: 1.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface GuiGuCache {
    String prefix() default "cache";
    String suffix() default ":lock";
}
