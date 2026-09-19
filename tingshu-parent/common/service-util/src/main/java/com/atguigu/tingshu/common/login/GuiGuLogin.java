package com.atguigu.tingshu.common.login;

import java.lang.annotation.*;

/**
 * @date: 2024/7/27 15:07
 * @author: yz
 * @version: 1.0
 *
 * @Target:注解作用的目标
 * @Retention:生命周期
 * @Inherited：注解是否能够被继承
 * @Documented:javadoc 生成文档时，该注解是否生成说明文档
 *
 *
 */

@Target({ ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface GuiGuLogin {

    /**
     * 是否需要拦截
     * @return
     */
    boolean required() default true;


}
