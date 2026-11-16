package com.seeex.tech.common.api;

/**
 * 分页请求参数。
 *
 * <p>统一接收分页查询的页码与每页大小，并对非法取值做收敛：页码小于 {@link #DEFAULT_PAGE}
 * 时置为默认页，每页大小超出 [1, {@link #MAX_SIZE}] 时置为 {@link #DEFAULT_SIZE}。</p>
 *
 * @param page 页码，从 1 开始
 * @param size 每页大小
 */
public record PageRequest(long page, long size) {

    /** 默认页码。 */
    private static final long DEFAULT_PAGE = 1;
    /** 默认每页大小。 */
    private static final long DEFAULT_SIZE = 20;
    /** 每页大小上限。 */
    private static final long MAX_SIZE = 200;

    public PageRequest {
        if (page < DEFAULT_PAGE) {
            page = DEFAULT_PAGE;
        }
        if (size < 1 || size > MAX_SIZE) {
            size = DEFAULT_SIZE;
        }
    }

    /**
     * 计算分页偏移量。
     *
     * @return 从 0 开始的偏移量
     */
    public long offset() {
        return (page - 1) * size;
    }
}
