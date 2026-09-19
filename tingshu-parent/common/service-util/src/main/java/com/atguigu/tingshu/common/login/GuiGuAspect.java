package com.atguigu.tingshu.common.login;

import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * @date: 2024/7/27 15:14
 * @author: yz
 * @version: 1.0
 */
@Aspect
@Component
@Slf4j
public class GuiGuAspect {


    @Autowired
    private RedisTemplate redisTemplate;
    /**
     *
     * 用户拦截切面定义处理
     *
     * @param joinPoint
     * @param guiGuLogin
     * @return
     */
    @Around("execution(* com.atguigu.tingshu.*.api.*.*(..)) && @annotation(guiGuLogin)")
    @SneakyThrows
    public Object doConcurrentOperation(ProceedingJoinPoint joinPoint,GuiGuLogin guiGuLogin)  {
        Object proceed =null;

        //获取用户请求对象
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes attributes= (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = attributes.getRequest();
        //获取请求头中存储的token
        String token = request.getHeader("token");
        //指定存储key
        String loginKey=RedisConstant.USER_LOGIN_KEY_PREFIX + token;
        //尝试充redis中获取user信息
        UserInfoVo userInfoVo= (UserInfoVo) redisTemplate.opsForValue().get(loginKey);

        //判断是否需求拦截
        if(guiGuLogin.required()&& userInfoVo==null){

            //拦截--响应208
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);

        }




        //登录--存储userid--threadlocal
        if(userInfoVo!=null){

            AuthContextHolder.setUserId(userInfoVo.getId());
            //滑动续期：用户只要还在使用，就把登录态的有效期往后顺延，避免正用着突然掉线
            redisTemplate.expire(loginKey, RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        }

        try {
            //执行目标方法
            proceed = joinPoint.proceed();
        } finally {
            //清除threadlocal--防止内存泄漏 --防止信息泄露（必须放finally：目标方法抛异常时也要清理，否则线程复用会串号）
            AuthContextHolder.removeUserId();
        }




        return proceed;
    }




}
