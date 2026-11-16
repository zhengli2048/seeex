package com.seeex.tech.common.api;

import java.util.List;

/**
 * 分页响应结果。
 *
 * @param items 当前页数据
 * @param page  当前页码（从 1 开始）
 * @param size  每页大小
 * @param total 总记录数
 * @param pages 总页数
 * @param <T>   数据类型
 */
public record PageResponse<T>(List<T> items, long page, long size, long total, long pages) {

    /**
     * 根据当前页数据与总记录数构造分页结果。
     *
     * @param items 当前页数据
     * @param page  当前页码
     * @param size  每页大小
     * @param total 总记录数
     * @param <T>   数据类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> of(List<T> items, long page, long size, long total) {
        return new PageResponse<>(items, page, size, total, size == 0 ? 0 : (total + size - 1) / size);
    }
}
