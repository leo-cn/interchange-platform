package com.interchange.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 接口交换平台启动类。
 *
 * <p>平台定位（类 SAP PO）：
 * <ul>
 *   <li>出向：把本系统数据按定时任务推送给第三方系统；</li>
 *   <li>入向：对外暴露接口，接收第三方系统的请求。</li>
 * </ul>
 */
@EnableAsync
@EnableTransactionManagement
@SpringBootApplication
public class InterchangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterchangeApplication.class, args);
    }
}
