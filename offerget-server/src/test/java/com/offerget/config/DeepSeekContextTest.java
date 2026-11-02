package com.offerget.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * 校验占位密钥下 Spring AI 自动配置仍能装配出 ChatClient / tools 客户端，
 * 保证「不配 key 也能开箱即跑」。
 */
class DeepSeekContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ToolCallingAutoConfiguration.class,
                    DeepSeekChatAutoConfiguration.class,
                    ChatClientAutoConfiguration.class))
            .withUserConfiguration(DeepSeekConfig.class)
            .withPropertyValues(
                    "spring.ai.deepseek.api-key=" + DeepSeekConfig.PLACEHOLDER_API_KEY,
                    "spring.ai.deepseek.base-url=https://api.deepseek.com",
                    "spring.ai.deepseek.chat.model=deepseek-flash");

    @Test
    void contextStartsWithPlaceholderApiKey() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChatClient.class);
            assertThat(context).hasSingleBean(RestClient.class);
        });
    }

}
