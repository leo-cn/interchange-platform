package com.interchange.platform.common;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装。页面表格与接口共用。
 */
public class PageResult<T> {

    private List<T> rows = Collections.emptyList();
    private long total;
    private int page = 1;
    private int size = 20;

    public PageResult() {
    }

    public PageResult(List<T> rows, long total, int page, int size) {
        this.rows = rows;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> rows, long total, int page, int size) {
        return new PageResult<>(rows, total, page, size);
    }

    /** 总页数 */
    public int getPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    /** 是否有上一页 */
    public boolean isHasPrev() {
        return page > 1;
    }

    /** 是否有下一页 */
    public boolean isHasNext() {
        return page < getPages();
    }

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
