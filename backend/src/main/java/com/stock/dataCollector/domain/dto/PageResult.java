package com.stock.dataCollector.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果响应类
 *
 * @param <T> 数据类型
 * @author mwangli
 * @since 2026-04-01
 */
@Data
public class PageResult<T> {

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int pages;

    /**
     * 当前页码
     */
    private int current;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 构造方法
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param current 当前页码
     * @param size    每页大小
     */
    public PageResult(List<T> records, long total, int current, int size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = (int) Math.ceil((double) total / size);
    }
}
