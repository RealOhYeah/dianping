package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    // 设置为true可以去除UUID的中线
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 提前启动Lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);

    }

    /**
     * 释放锁(方法二)
     */
    @Override
    public void unlock() {

        // 调用Lua脚本(将判断和删除合为一步，体现原子性)
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

    /**
     * 释放锁(方法一)
     */
/*    @Override
    public void unlock() {

        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        // 判断是否为自己线程的锁
        if (id.equals(threadId)){

            //通过del删除锁(释放锁)
            stringRedisTemplate.delete(KEY_PREFIX + name);

        }

    }*/

    public SimpleRedisLock() {
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
}
