package com.atguigu.tingshu.common.thread;

//import com.atguigu.tingshu.common.zipkin.ZipkinHelper;
//import com.atguigu.tingshu.common.zipkin.ZipkinTaskDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

/**
 * @date: 2024/8/5 10:15
 * @author: yz
 * @version: 1.0
 */
@Configuration
public class ThreadPoolConfig {


    /**
     * 核心线程数的选定：
     *   IO密集型  CPU核心数据*2
     *   CPU密集型 CPU核心数+1
     *
     *
     *
     *
     * @return
     */
    @Bean
    public ThreadPoolExecutor executor(){

        //获取计算机的逻辑CPU核数
        int processors = Runtime.getRuntime().availableProcessors();
        int coreCount=processors*2;


        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreCount,
                coreCount,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                Executors.defaultThreadFactory(),
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        //睡眠
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        //再次提交
                        executor.submit(r);
                    }
                }
        );



        //预先创建一个核心线程数
        executor.prestartCoreThread();
        return executor;

    }



//    @Autowired
//    private ZipkinHelper zipkinHelper;
//
//    /**
//     * 项目中选择spring线程池
//     * Spring提供线程池threadPoolTaskExecutor
//     *
//     * @return
//     */
//    @Bean
//    public Executor threadPoolTaskExecutor() {
//        int count = Runtime.getRuntime().availableProcessors();
//        int threadCount = count*2+1;
//        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
//        // 核心池大小
//        taskExecutor.setCorePoolSize(threadCount);
//        // 最大线程数
//        taskExecutor.setMaxPoolSize(threadCount);
//        // 队列程度
//        taskExecutor.setQueueCapacity(300);
//        // 线程空闲时间
//        taskExecutor.setKeepAliveSeconds(0);
//        // 线程前缀名称
//        taskExecutor.setThreadNamePrefix("sync-tingshu-Executor--");
//        // 该方法用来设置 线程池关闭 的时候 等待 所有任务都完成后，再继续 销毁 其他的 Bean，
//        // 这样这些 异步任务 的 销毁 就会先于 数据库连接池对象 的销毁。
//        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
//        // 任务的等待时间 如果超过这个时间还没有销毁就 强制销毁，以确保应用最后能够被关闭，而不是阻塞住。
//        taskExecutor.setAwaitTerminationSeconds(300);
//        //设置解决zipkin链路追踪不完整装饰器对象
//        taskExecutor.setTaskDecorator(new ZipkinTaskDecorator(zipkinHelper));
//        // 线程不够用时由调用的线程处理该任务
//        taskExecutor.setRejectedExecutionHandler((task, executor)->{
//            //将拒绝任务再次提交给线程池执行
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            executor.submit(task);
//        });
//        return taskExecutor;
//    }
}
