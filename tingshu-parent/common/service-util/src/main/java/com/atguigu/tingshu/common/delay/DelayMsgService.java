package com.atguigu.tingshu.common.delay;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @date: 2024/9/25 11:36
 * @author: yz
 * @version: 1.0
 */
@Service
@Slf4j
public class DelayMsgService {


    @Autowired
    private RedissonClient redissonClient;

    /**
     * 发送延迟消息
     * @param queueName
     * @param message
     * @param delayTime
     * @return
     */
    public boolean sendDelayMessage(String queueName,String message,int delayTime){

        try {
            //获取阻塞队列
            RBlockingDeque<String> blockingDeque = redissonClient.getBlockingDeque(queueName);
            //获取延迟队列
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
            //发送消息
            delayedQueue.offer(message, delayTime, TimeUnit.SECONDS);
            log.info("延迟消息发送成功消息内容:{}",message);
        } catch (Exception e) {
            log.info("延迟消息发送失败消息内容:{}",message);
            throw new RuntimeException(e);
        }


        return true;
    }
}
