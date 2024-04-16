package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    // 设置为true可以去除UUID的中线
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);

    }

    @Override
    public void unlock() {

        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        if (id.equals(threadId)){

            //通过del删除锁(释放锁)
            stringRedisTemplate.delete(KEY_PREFIX + name);

        }

    }

    public SimpleRedisLock() {
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
}
