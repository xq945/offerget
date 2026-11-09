package com.offerget.common;

import java.util.Locale;

import org.springframework.util.StringUtils;

/**
 * 邮箱规范化：统一 trim + 小写。
 *
 * <p>数据库排序规则本身大小写不敏感，但 Redis key 必须完全一致，
 * 因此注册、资料修改、发码、校验全部先经过这里。</p>
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

}
