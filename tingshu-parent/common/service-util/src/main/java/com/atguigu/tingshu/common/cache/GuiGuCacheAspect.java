package com.atguigu.tingshu.common.cache;

import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.model.album.AlbumInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.message.ReusableMessage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @date: 2024/9/21 14:24
 * @author: yz
 * @version: 1.0
 */
@Aspect
@Slf4j
@Component
public class GuiGuCacheAspect {


    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 缓存切面实现
     * @param joinPoint
     * @param guiGuCache
     * @return
     * @throws Throwable
     */
    @Around("@annotation(guiGuCache)")
    public Object guiGuCacheAdvice(ProceedingJoinPoint joinPoint,GuiGuCache guiGuCache) throws Throwable {

        Object obj = null;
        try {
            obj = new Object();

            //获取前缀前缀
            String prefix = guiGuCache.prefix();
            //定义参数字符串
            String params="none";
            //获取参数
            Object[] args = joinPoint.getArgs();
            //判断
            if(args!=null &&args.length>0){
                params= Arrays.stream(args).map(arg->arg.toString()).collect(Collectors.joining(":"));
            }

            //设置数据key  prefix+id
            String dateKey=prefix+params;
            //获取返回值类型
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class returnType = signature.getReturnType();
            //1.尝试从缓存中获取数据
            obj= redisTemplate.opsForValue().get(dateKey);
            //判断
            if(obj==null){
                //定义锁key
                String lockKey=prefix+params+":"+ RedisConstant.CACHE_LOCK_SUFFIX;
                //获取分布式锁
                RLock lock = redissonClient.getLock(lockKey);
                //加锁
                boolean flag = lock.tryLock(RedisConstant.CACHE_LOCK_EXPIRE_PX1, RedisConstant.CACHE_LOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                //判断
                if(flag){
                    try {
                        //查询数据库
                        obj =joinPoint.proceed();

                        //判断
                        if(obj==null){
                            obj=returnType.newInstance();
                            redisTemplate.opsForValue().set(dateKey,obj,RedisConstant.ALBUM_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            return obj;
                        }else{
                            redisTemplate.opsForValue().set(dateKey,obj,RedisConstant.ALBUM_TIMEOUT,TimeUnit.SECONDS);
                            return obj;
                        }
                    } finally {
                        lock.unlock();
                    }
                }else{
                    Thread.sleep(100);
                    return guiGuCacheAdvice(joinPoint,guiGuCache);
                }
            }else{
                //缓存中有数据直接返回
                return obj;
            }
        } catch (Throwable e) {
            log.error("redis服务器异常。。。。");
        }

        return joinPoint.proceed();
    }




}
