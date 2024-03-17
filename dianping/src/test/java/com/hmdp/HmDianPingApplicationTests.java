package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    private static Long nums = 0L;

    @Test
    void testIdWorker() throws InterruptedException {
        //构造方法，创建一个值为count 的计数器。
        CountDownLatch latch = new CountDownLatch(300);

        //此处定义执行任务
        Runnable task = () -> {
            long ids = 0;
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
//              此处将生成的id进行打印输出
                System.out.println("id = " + id);
                ids++;

            }
            nums+=ids;
            //对计数器进行递减1操作，当计数器递减至0时，当前线程会去唤醒阻塞队列里的所有线程。
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //这里将完成任务的线程加入阻塞队列,最后所有300个线程执行完后，再全部唤醒
        latch.await();
        long end = System.currentTimeMillis();
        //这里打印出300个线程执行完成之后的最终时间
        System.out.println("time = " + (end - begin));
        //最终打印输出数量(这样就解决了线程并发产生的计数问题)
        System.out.println("总共生成的id数：" + nums);
    }



    /**
     * 为逻辑过期添加旧数据
     */
    @Test
    void testSaveShop() throws InterruptedException {
        //下方代码已经注释，是逻辑过期的预热代码
//        shopService.saveShop2Redis(1L, 10L);

        //上下两行预热的代码都是直接传的1L作为id，正常来都是查到id然后传入再拼接
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shopService.getById(1L),1L,TimeUnit.SECONDS);
    }

}
