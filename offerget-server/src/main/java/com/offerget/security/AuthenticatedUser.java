package com.offerget.security;

/**
 * 写入 SecurityContext 的当前登录用户。
 */
public record AuthenticatedUser(Long id, String username) {
}
