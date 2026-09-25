/* ==========================================================================
   外壳页（shell.html）：右侧多标签页容器

   设计要点
   --------
   1. 每个标签页对应一个**常驻 iframe**。切换标签只改 iframe 的可见性，
      不销毁 DOM，所以页面滚动位置、已填表单、已展开的详情都不会丢，
      真正做到"多开"而非"来回重载"。
   2. 标签页状态（开了哪些、当前是哪个）写入 sessionStorage，外壳刷新后
      自动恢复；关掉浏览器标签则自然清空。
   3. iframe 内页面点到**跨模块**的站内链接时（例如任务列表里的"日志"），
      由 app.js 通过 postMessage 通知本页开成新标签页；同模块的链接
      （分页、筛选、重置）留在原标签页内跳转。
   ========================================================================== */
(function () {
    'use strict';

    var STORAGE_KEY = 'ip.shell.tabs.v1';
    var HOME_KEY = 'index';
    var HOME_TITLE = '运行看板';
    var HOME_URL = '/dashboard';

    var tabsEl = document.getElementById('tabs');
    var framesEl = document.getElementById('frames');
    var welcomeEl = document.getElementById('welcome');
    var pageTitleEl = document.getElementById('pageTitle');

    /** key -> {key,title,url}，来自侧边栏 DOM，保证与后端 Menus.ALL 一致 */
    var menusByKey = {};
    /** path -> {key,title,url} */
    var menusByPath = {};

    /** 已打开的标签页：{key,title,url,closable,btn,frame} */
    var tabs = [];
    var activeKey = null;

    /* ------------------------------------------------------------------ */
    /* 菜单索引                                                            */
    /* ------------------------------------------------------------------ */
    Array.prototype.forEach.call(document.querySelectorAll('.sidebar a[data-key]'), function (a) {
        var item = {key: a.getAttribute('data-key'), title: a.getAttribute('data-title'), url: a.getAttribute('data-url')};
        menusByKey[item.key] = item;
        menusByPath[item.url] = item;
    });

    function menuForUrl(url) {
        var path = String(url || '').split('?')[0].split('#')[0];
        if (path.length > 1 && path.charAt(path.length - 1) === '/') {
            path = path.slice(0, -1);
        }
        return menusByPath[path] || null;
    }

    /* ------------------------------------------------------------------ */
    /* 标签页                                                              */
    /* ------------------------------------------------------------------ */
    function findTab(key) {
        for (var i = 0; i < tabs.length; i++) {
            if (tabs[i].key === key) return tabs[i];
        }
        return null;
    }

    function syncMenuHighlight() {
        var target = null;
        var tab = findTab(activeKey);
        if (tab) target = menuForUrl(tab.url);
        Array.prototype.forEach.call(document.querySelectorAll('.sidebar a[data-key]'), function (a) {
            var on = target && a.getAttribute('data-key') === target.key;
            a.classList.toggle('active', !!on);
        });
    }

    function updateWelcome() {
        if (welcomeEl) welcomeEl.style.display = tabs.length ? 'none' : 'flex';
    }

    function updatePageTitle() {
        var tab = findTab(activeKey);
        if (pageTitleEl) pageTitleEl.textContent = tab ? tab.title : '接口交换平台';
        document.title = tab ? (tab.title + ' - 接口交换平台') : '接口交换平台';
    }

    function renderTabTitle(tab) {
        var el = tab.btn.querySelector('.tab-title');
        if (el) el.textContent = tab.title;
        if (tab.key === activeKey) updatePageTitle();
    }

    /** 打开（或激活）一个标签页 */
    function openTab(key, title, url, closable) {
        var exist = findTab(key);
        if (exist) {
            // URL 变了（例如同一菜单换了查询条件）则重新加载，保持一致
            if (url && exist.url !== url) {
                exist.url = url;
                exist.frame.src = url;
            }
            activateTab(key);
            return exist;
        }

        var btn = document.createElement('div');
        btn.className = 'tab';
        btn.setAttribute('data-key', key);

        var dot = document.createElement('span');
        dot.className = 'tab-dot';
        btn.appendChild(dot);

        var titleEl = document.createElement('span');
        titleEl.className = 'tab-title';
        titleEl.textContent = title;
        titleEl.title = title;
        btn.appendChild(titleEl);

        if (closable) {
            var x = document.createElement('button');
            x.type = 'button';
            x.className = 'tab-x';
            x.innerHTML = '&times;';
            x.title = '关闭该标签页';
            x.addEventListener('click', function (e) {
                e.stopPropagation();
                closeTab(key);
            });
            btn.appendChild(x);
        } else {
            btn.classList.add('fixed');
        }

        btn.addEventListener('click', function () {
            activateTab(key);
        });

        // 中键关闭，符合浏览器习惯
        btn.addEventListener('auxclick', function (e) {
            if (e.button === 1 && closable) {
                e.preventDefault();
                closeTab(key);
            }
        });

        var frame = document.createElement('iframe');
        frame.className = 'frame';
        frame.setAttribute('data-key', key);
        frame.setAttribute('frameborder', '0');
        frame.setAttribute('title', title);

        // 只有首页用固定标题；其他标签页加载完成后用页面自身的 <title> 覆盖，
        // 这样 "/task/edit?id=3" 会显示成页面真实标题而不是菜单名。
        var fixedTitle = menuForUrl(url) && key === HOME_KEY;
        frame.addEventListener('load', function () {
            if (fixedTitle) return;
            try {
                var dt = frame.contentDocument && frame.contentDocument.title;
                if (!dt) return;
                // 页面标题形如 "定时任务列表 - 接口交换平台"，去掉后缀只留页面名
                var cleaned = dt.replace(/\s*[-|]\s*\u63A5\u53E3\u4EA4\u6362\u5E73\u53F0\s*$/, '')
                                .replace(/^\s*\u63A5\u53E3\u4EA4\u6362\u5E73\u53F0\s*[-|]\s*/, '')
                                .trim();
                if (!cleaned) return;
                var t = findTab(key);
                if (t && t.title !== cleaned) {
                    t.title = cleaned;
                    renderTabTitle(t);
                    save();
                }
            } catch (err) { /* 跨域或尚未就绪，忽略 */ }
        });

        frame.src = url;

        var tab = {key: key, title: title, url: url, closable: !!closable, btn: btn, frame: frame};
        tabs.push(tab);
        tabsEl.appendChild(btn);
        framesEl.appendChild(frame);

        activateTab(key);
        updateWelcome();
        return tab;
    }

    /**
     * 由 URL 推导标签页的 key / 标题。
     *
     * 这是"能真正多开"的关键规则：
     *  - 无查询串的菜单 URL（如 /task/list）→ 用菜单 key，重复点击只复用同一个标签页；
     *  - 带查询串的菜单 URL（如 /task/edit?id=3、/log/list?taskCode=SO_PUSH）
     *    → 用**完整 URL** 作 key，每个参数组合独立成一个标签页，
     *      于是"编辑任务 A"与"编辑任务 B"、"A 的日志"与"B 的日志"可以并存；
     *  - 非菜单路径 → 用完整 URL 作 key，标题先用路径，加载完再由页面标题覆盖。
     */
    function tabInfoFor(url) {
        var m = menuForUrl(url);
        var hasQuery = String(url).indexOf('?') >= 0;
        if (m && !hasQuery) {
            return {key: m.key, title: m.title, url: m.url};
        }
        if (m) {
            return {key: url, title: m.title, url: url};
        }
        var path = String(url).split('?')[0];
        return {key: url, title: path, url: url};
    }

    /** 按 URL 打开标签页 */
    function openByUrl(url) {
        if (!url) return;
        var info = tabInfoFor(url);
        openTab(info.key, info.title, info.url, info.key !== HOME_KEY);
    }

    function activateTab(key) {
        var tab = findTab(key);
        if (!tab) return;
        activeKey = key;
        tabs.forEach(function (t) {
            var on = t.key === key;
            t.btn.classList.toggle('active', on);
            t.frame.classList.toggle('active', on);
        });
        syncMenuHighlight();
        updatePageTitle();
        scrollTabIntoView(tab.btn);
        save();
    }

    function closeTab(key) {
        var idx = -1;
        for (var i = 0; i < tabs.length; i++) {
            if (tabs[i].key === key) { idx = i; break; }
        }
        if (idx < 0) return;
        var tab = tabs[idx];
        if (!tab.closable) return;

        if (tab.btn.parentNode) tab.btn.parentNode.removeChild(tab.btn);
        if (tab.frame.parentNode) tab.frame.parentNode.removeChild(tab.frame);
        tabs.splice(idx, 1);

        if (activeKey === key) {
            var next = tabs[idx] || tabs[idx - 1] || tabs[0];
            if (next) {
                activateTab(next.key);
            } else {
                activeKey = null;
                updatePageTitle();
                syncMenuHighlight();
            }
        }
        updateWelcome();
        save();
    }

    function scrollTabIntoView(btn) {
        try {
            if (btn && btn.scrollIntoView) {
                btn.scrollIntoView({block: 'nearest', inline: 'nearest'});
            }
        } catch (e) { /* 老浏览器忽略 */ }
    }

    /* ------------------------------------------------------------------ */
    /* 持久化                                                              */
    /* ------------------------------------------------------------------ */
    function save() {
        try {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                active: activeKey,
                tabs: tabs.map(function (t) {
                    return {key: t.key, title: t.title, url: t.url, closable: t.closable};
                })
            }));
        } catch (e) { /* 隐私模式等场景忽略 */ }
    }

    function restore() {
        var raw = null;
        try {
            raw = sessionStorage.getItem(STORAGE_KEY);
        } catch (e) {
            return false;
        }
        if (!raw) return false;
        try {
            var st = JSON.parse(raw);
            if (!st || !st.tabs || !st.tabs.length) return false;
            st.tabs.forEach(function (t) {
                openTab(t.key, t.title, t.url, t.closable);
            });
            if (st.active && findTab(st.active)) {
                activateTab(st.active);
            }
            return tabs.length > 0;
        } catch (e) {
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 事件绑定                                                            */
    /* ------------------------------------------------------------------ */
    // 左键点击菜单 -> 打开标签页。
    // 这里显式 preventDefault：菜单项是 <a href="javascript:void(0)">，
    // 一旦发生实际跳转，外壳页会被重新加载，已开的标签页就"看起来被关掉了"。
    Array.prototype.forEach.call(document.querySelectorAll('.sidebar a[data-key]'), function (a) {
        a.addEventListener('click', function (e) {
            e.preventDefault();
            var m = {
                key: a.getAttribute('data-key'),
                url: a.getAttribute('data-url'),
                title: a.getAttribute('data-title')
            };
            openTab(m.key, m.title, m.url, m.key !== HOME_KEY);
        });
    });

    // 顶栏个人中心
    var btnProfile = document.getElementById('btnProfile');
    if (btnProfile) {
        btnProfile.addEventListener('click', function () {
            openByUrl(btnProfile.getAttribute('data-url'));
        });
    }

    // 刷新当前标签页
    var btnReload = document.getElementById('btnReload');
    if (btnReload) {
        btnReload.addEventListener('click', function () {
            var tab = findTab(activeKey);
            if (!tab) return;
            // 重新赋值 src 比 location.reload() 更可靠（避免跨文档权限问题）
            tab.frame.src = tab.url;
        });
    }

    // 关闭其他标签页（保留当前与首页）
    var btnCloseOthers = document.getElementById('btnCloseOthers');
    if (btnCloseOthers) {
        btnCloseOthers.addEventListener('click', function () {
            tabs.slice().forEach(function (t) {
                if (t.key !== activeKey && t.key !== HOME_KEY) closeTab(t.key);
            });
        });
    }

    // 收起 / 展开侧栏
    var btnCollapse = document.getElementById('btnCollapse');
    var shellEl = document.getElementById('shell');
    if (btnCollapse && shellEl) {
        btnCollapse.addEventListener('click', function () {
            shellEl.classList.toggle('collapsed');
            try {
                localStorage.setItem('ip.shell.collapsed', shellEl.classList.contains('collapsed') ? '1' : '0');
            } catch (e) { /* 忽略 */ }
        });
        try {
            if (localStorage.getItem('ip.shell.collapsed') === '1') {
                shellEl.classList.add('collapsed');
            }
        } catch (e) { /* 忽略 */ }
    }

    // 标签栏支持滚轮横向滚动
    tabsEl.addEventListener('wheel', function (e) {
        if (e.deltaY !== 0) {
            tabsEl.scrollLeft += e.deltaY;
            e.preventDefault();
        }
    }, {passive: false});

    // 标签栏空白处双击 -> 回到首页
    tabsEl.addEventListener('dblclick', function (e) {
        if (e.target === tabsEl) activateTab(HOME_KEY);
    });

    // iframe 内页面请求打开新标签页
    window.addEventListener('message', function (e) {
        if (e.origin !== window.location.origin) return;
        var d = e.data;
        if (!d || d.type !== 'ip:open' || !d.url) return;
        openByUrl(d.url);
    });

    /* ------------------------------------------------------------------ */
    /* 启动                                                                */
    /* ------------------------------------------------------------------ */
    if (!restore()) {
        openTab(HOME_KEY, HOME_TITLE, HOME_URL, false);
    }

    // 深链：/?open=/task/list 之类，直接把目标页开成标签页。
    // 来源包括收藏夹、地址栏直输、新窗口打开，以及会话过期登录后回跳。
    try {
        var openParam = new URLSearchParams(window.location.search).get('open');
        if (openParam) {
            openByUrl(decodeURIComponent(openParam));
            // 去掉参数，避免刷新时重复打开、也避免地址栏一直挂着
            window.history.replaceState(null, '', window.location.pathname);
        }
    } catch (e) { /* 老浏览器忽略深链 */ }

    updateWelcome();
})();
