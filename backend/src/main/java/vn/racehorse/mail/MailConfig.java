package vn.racehorse.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** A small bounded executor so the mail never delays the answer of a recovery request (TECH-226). */
@Configuration
public class MailConfig {

    public static final String EXECUTOR = "recoveryMailExecutor";

    @Bean(name = EXECUTOR)
    TaskExecutor recoveryMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("recovery-mail-");
        return executor;
    }
}
