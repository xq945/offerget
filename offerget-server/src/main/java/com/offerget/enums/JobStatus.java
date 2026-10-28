package com.offerget.enums;

import com.offerget.common.BusinessException;

/**
 * 投递状态。数据库以字符串存储，前端按 name 映射标签。
 */
public enum JobStatus {

    PENDING("待投递"),
    APPLIED("已投递"),
    WRITTEN_TEST("笔试"),
    INTERVIEW("面试"),
    OFFER("Offer"),
    REJECTED("未通过");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static JobStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest("状态不能为空");
        }
        try {
            return JobStatus.valueOf(value.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("未知的投递状态：" + value);
        }
    }

}
