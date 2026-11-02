package com.offerget.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeepSeekConfigTest {

    @Test
    void nullOrBlankKeyIsUnconfigured() {
        assertThat(DeepSeekConfig.configured(null)).isFalse();
        assertThat(DeepSeekConfig.configured("")).isFalse();
        assertThat(DeepSeekConfig.configured("   ")).isFalse();
    }

    @Test
    void placeholderKeyIsUnconfigured() {
        assertThat(DeepSeekConfig.configured(DeepSeekConfig.PLACEHOLDER_API_KEY)).isFalse();
        assertThat(DeepSeekConfig.configured("  " + DeepSeekConfig.PLACEHOLDER_API_KEY + "  ")).isFalse();
    }

    @Test
    void realKeyIsConfigured() {
        assertThat(DeepSeekConfig.configured("sk-1234567890")).isTrue();
    }

}
