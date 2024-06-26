package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        // 连接redis地址(带密码)
        // config.useSingleServer().setAddress("redis://192.168.150.101:6379")
        //     .setPassword("123321");

        config.useSingleServer().setAddress("redis://192.168.88.130:6379").setPassword("1234");

        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
