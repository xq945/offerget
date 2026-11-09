package com.offerget.dto.auth;

/**
 * 发码结果：把冷却与有效期交给前端，避免前端硬编码倒计时秒数。
 */
public record EmailCodeSentVO(int cooldownSeconds, int expiresInSeconds) {
}
