package com.offerget.common;

import lombok.Data;

/**
 * 通用分页参数，页码从 1 开始，单页上限 100。
 */
@Data
public class PageQuery {

    private int page = 1;

    private int size = 10;

    public int safePage() {
        return Math.max(page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size, 1), 100);
    }

}
