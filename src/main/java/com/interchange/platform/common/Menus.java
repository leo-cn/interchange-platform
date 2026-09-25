package com.interchange.platform.common;

import java.util.List;

/**
 * 平台菜单元数据（唯一数据源）。
 *
 * <p>外壳页（shell.html）与内页侧边栏（fragments/layout.html）都从这份定义渲染，
 * 避免"两处各写一份菜单"导致内容不一致。
 *
 * <p>同时，外壳页的多标签页逻辑依赖 {@link #matchByPath(String)}：
 * iframe 内页面里点到的站内链接，若路径命中菜单项，就按该菜单的 key/标题
 * 复用或新开标签页，这样"任务列表 → 该任务的日志"能开成一个独立标签页。
 */
public final class Menus {

    private Menus() {
    }

    /** 首页标签页的 key：固定存在、不可关闭。 */
    public static final String HOME_KEY = "index";

    /** 单个菜单项。 */
    public static final class Item {
        private final String key;
        private final String title;
        private final String url;
        private final String icon;
        private final String group;

        public Item(String key, String title, String url, String icon, String group) {
            this.key = key;
            this.title = title;
            this.url = url;
            this.icon = icon;
            this.group = group;
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getIcon() {
            return icon;
        }

        public String getGroup() {
            return group;
        }
    }

    public static final List<Item> ALL = List.of(
            new Item(HOME_KEY, "运行看板", "/dashboard", "◎", "概览"),
            new Item("task", "定时任务列表", "/task/list", "▤", "出向 · 定时推送"),
            new Item("task-edit", "新建任务配置", "/task/edit", "＋", "出向 · 定时推送"),
            new Item("partner", "第三方系统", "/partner/list", "⇄", "出向 · 定时推送"),
            new Item("log", "执行日志查询", "/log/list", "≣", "日志中心"),
            new Item("receive", "接收日志", "/receive/list", "⇩", "日志中心"),
            new Item("iface-log", "接口日志", "/iface-log/list", "▣", "日志中心"),
            new Item("receive-api", "接收接口", "/receive-api/list", "⇥", "入向 · 接收接口"),
            new Item("token", "接口令牌", "/token/list", "⌘", "入向 · 接收接口"),
            new Item("profile", "个人中心", "/profile", "☺", "系统")
    );

    /**
     * 按 URL 路径匹配菜单项。忽略首尾斜杠差异与查询串。
     *
     * @param path 形如 {@code /task/list} 的路径
     * @return 命中的菜单项；未命中返回 {@code null}
     */
    public static Item matchByPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String p = path.trim();
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        int h = p.indexOf('#');
        if (h >= 0) {
            p = p.substring(0, h);
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        for (Item it : ALL) {
            if (it.getUrl().equals(p)) {
                return it;
            }
        }
        return null;
    }
}
