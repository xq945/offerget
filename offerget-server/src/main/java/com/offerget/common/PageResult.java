package com.offerget.common;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.Data;

/**
 * 通用分页响应。
 */
@Data
public class PageResult<T> {

    private List<T> records;

    private long page;

    private long size;

    private long total;

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setPage(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        return result;
    }

    public static <T> PageResult<T> of(List<T> records, long page, long size, long total) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }

}
