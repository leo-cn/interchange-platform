package com.interchange.platform.standard.sys.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装。页面表格与接口共用。
 *
 * <p>页码对外用<b>从 0 开始</b>的下标（{@link #number}），与模板里的
 * {@code page.number} 保持一致；构造时传入的是从 1 开始的页码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> rows = Collections.emptyList();
    private long total;
    /** 当前页，从 0 开始 */
    private int number;
    private int size = 20;

    /** @param page 从 1 开始的页码 */
    public static <T> PageResult<T> of(List<T> rows, long total, int page, int size) {
        return new PageResult<>(rows, total, Math.max(page - 1, 0), size);
    }

    public List<T> getContent() {
        return rows;
    }

    public long getTotalElements() {
        return total;
    }

    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
