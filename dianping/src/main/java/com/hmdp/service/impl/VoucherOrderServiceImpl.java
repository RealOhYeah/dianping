package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 创建Lua脚本并引入
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {


        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {

                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >  (注意在队列处：这里是">")
                    // 为什么这里返回的是list：因为在count的指定中，如果不是1，返回的值可能是多个，所以给list来装结果
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()) //ReadOffset.lastConsumed() => ">";
                    );

//                    System.out.println("调入循环中。。。。。");

                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }

                    System.out.println("输出list内容：");
                    System.out.println(list);
                    /**
                    *  下方是输出的示例结果：
                    *  输出list内容：
                    *  [MapBackedRecord{recordId=1713712871650-0, kvMap={userId=1, voucherId=14, id=13444957033463809}}]
                    */

                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);

                    System.out.println("输出record内容：");
                    System.out.println(record);
                    /**
                     *  下方是输出的示例结果：
                     *  输出record内容：
                     *  MapBackedRecord{recordId=1713712871650-0, kvMap={userId=1, voucherId=14, id=13444957033463809}}
                     */


                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    System.out.println("输出value内容：");
                    System.out.println(value);


                    // 3.创建订单
                    createVoucherOrder(voucherOrder);

                    // 4.确认消息 XACK
                    Long ack = stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                    System.out.println("确认消息返回值：" + ack);


                } catch (Exception e) {
                    System.out.println("真的是关闭的异常？？？---1");
                    log.error("处理订单异常", e);
                    //处理异常消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0   (注意在pending-list处：这里是"0")
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    System.out.println("真的是关闭的异常？？？---2");
                    log.error("处理pendding订单异常", e);
                    try {
                        //防止死循环太过频繁，休眠20毫秒
                        Thread.sleep(20);

                        System.out.println("真的是关闭的异常？？？---3");
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException(interruptedException);
                    }

                }
            }
        }

/*
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从对列中去拿信息
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取jdk中阻塞队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
*/


   /*     private void handleVoucherOrder(VoucherOrder voucherOrder) {
            //1.获取用户
            Long userId = voucherOrder.getUserId();
            // 2.创建锁对象
            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
            // 3.尝试获取锁
            boolean isLock = redisLock.tryLock();
            // 4.判断是否获得锁成功
            if (!isLock) {
                // 获取锁失败，直接返回失败或者重试
                log.error("不允许重复下单！");
                return;
            }
            try {
                //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                // 释放锁
                redisLock.unlock();
            }
        }
*/
    }

    /**
     * 限时抢购优惠券
     * ***使用lua脚本实现***
     * @param voucherId
     * @return
     */
    // 获取主线程的代理对象
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //3.获取代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }


    /**
     * 限时抢购优惠券
     * 仅仅使用StringRedisTemplate实现
     * @param voucherId
     * @return
     */
/*    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        //创建锁对象(方法一)
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        //使用Redission(方法二)
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        // 获取锁(方法一)
//        boolean isLock = lock.tryLock(1200);

        //Redission(方法二)
        boolean isLock = lock.tryLock();

        // 判断锁是否获取成功
        if (!isLock){
            // 获取锁失败，返货错误或者重试
            return Result.fail("不允许重复下单");
        }

        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }

    }


*/


      /**
       * //     * 抽取---判断用户的id来加锁的方式来实现一人一单
       * 仅仅使用StringRedisTemplate实现
       * //     * @param voucherId
       * //     * @return
       */
      @Transactional
      public void createVoucherOrder(VoucherOrder voucherOrder) {
          Long userId = voucherOrder.getUserId();
          // 5.1.查询订单
          int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
          // 5.2.判断是否存在
          if (count > 0) {
              // 用户已经购买过了
              log.error("用户已经购买过了");
              return ;
          }

          // 6.扣减库存
          boolean success = seckillVoucherService.update()
                  .setSql("stock = stock - 1") // set stock = stock - 1
                  .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                  .update();
          if (!success) {
              // 扣减失败
              log.error("库存不足");
              return ;
          }

          // 创建订单并保存到数据库
          save(voucherOrder);

      }

}
