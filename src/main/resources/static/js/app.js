/* ==========================================================================
   接口交换平台 公共 JS：请求封装、提示、弹窗、通用交互
   ========================================================================== */

/** 统一 POST（表单参数） */
async function postForm(url, params) {
    // 【两个都必须对，缺一个就是 P0 故障】
    // 1) body 必须是 URLSearchParams 对象本身，绝不能 .toString()。
    //    传字符串时浏览器会把 Content-Type 当成 text/plain，
    //    Spring 的 @RequestParam 读不到任何参数 —— 表现为"所有字段都是 null"，
    //    登录报"请输入用户名"、任务启停/删除、改密全部静默失效。
    // 2) 显式声明 Content-Type，不依赖浏览器的自动推断（隐式行为已经坑过一次）。
    const body = new URLSearchParams(params || {});
    const resp = await fetch(url, {
        method: 'POST',
        headers: {
            'X-Requested-With': 'XMLHttpRequest',
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        },
        body: body
    });
    return handleResponse(resp);
}

/** 统一 POST（JSON 请求体） */
async function postJson(url, data) {
    const resp = await fetch(url, {
        method: 'POST',
        headers: {'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/json;charset=UTF-8'},
        body: JSON.stringify(data || {})
    });
    return handleResponse(resp);
}

/** 统一 GET */
async function getJson(url) {
    const resp = await fetch(url, {
        headers: {'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json'}
    });
    return handleResponse(resp);
}

async function handleResponse(resp) {
    if (resp.status === 401) {
        toast('登录已失效，正在跳转登录页…', 'error');
        setTimeout(() => location.href = '/login', 800);
        throw new Error('unauthorized');
    }
    const text = await resp.text();
    try {
        return JSON.parse(text);
    } catch (e) {
        return {code: 500, message: '响应解析失败: ' + text.substring(0, 200)};
    }
}

/** 提示条 */
function toast(message, type) {
    let box = document.querySelector('.toast-box');
    if (!box) {
        box = document.createElement('div');
        box.className = 'toast-box';
        document.body.appendChild(box);
    }
    const el = document.createElement('div');
    el.className = 'toast ' + (type || 'info');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(() => {
        el.style.transition = 'opacity .3s';
        el.style.opacity = '0';
        setTimeout(() => el.remove(), 320);
    }, 3200);
}

/** 打开弹窗 */
function openModal(title, contentHtml) {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = contentHtml;
    document.getElementById('modalMask').classList.add('show');
}

function closeModal() {
    document.getElementById('modalMask').classList.remove('show');
}

document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
        closeModal();
    }
});

/** 状态徽标 */
function statusBadge(status) {
    const map = {
        SUCCESS: ['成功', 'badge-success'],
        FAIL: ['失败', 'badge-fail'],
        PARTIAL: ['部分成功', 'badge-partial'],
        RUNNING: ['执行中', 'badge-info']
    };
    const item = map[status] || [status || '-', 'badge-gray'];
    return '<span class="badge ' + item[1] + '">' + item[0] + '</span>';
}

function escapeHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text).replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[s]);
}

/** 弹出日志详情 */
async function showTaskLogDetail(id) {
    const r = await getJson('/api/log/' + id);
    if (r.code !== 0) {
        toast(r.message, 'error');
        return;
    }
    const d = r.data;
    let html = '<div class="detail-list">'
        + row('任务', escapeHtml(d.taskCode) + ' / ' + escapeHtml(d.taskName))
        + row('第三方系统', escapeHtml(d.partnerName))
        + row('traceId', '<span class="mono">' + escapeHtml(d.traceId) + '</span>')
        + row('触发方式', d.triggerType === 'MANUAL' ? '手工触发' : '定时触发')
        + row('执行结果', statusBadge(d.status))
        + row('开始时间', escapeHtml(d.startTime))
        + row('结束时间', escapeHtml(d.endTime))
        + row('耗时', d.costMs + ' ms')
        + row('数据条数', (d.totalCount || 0) + '（成功 ' + (d.successCount || 0) + ' / 失败 ' + (d.failCount || 0) + '）')
        + row('目标地址', '<span class="mono">' + escapeHtml(d.targetUrl) + '</span>')
        + '</div>'
        + section('请求报文', d.requestBody)
        + section('响应报文', d.responseBody)
        + (d.errorMsg ? section('错误信息', d.errorMsg) : '')
        + traceButtonHtml(d.taskCode, d.traceId, dateOf(d.startTime));
    openModal('执行日志详情 #' + id, html);
    bindTraceButton();
}

/** 弹出接收日志详情 */
async function showReceiveLogDetail(id) {
    const r = await getJson('/api/receive-log/' + id);
    if (r.code !== 0) {
        toast(r.message, 'error');
        return;
    }
    const d = r.data;
    let html = '<div class="detail-list">'
        + row('接口编码', escapeHtml(d.apiCode))
        + row('traceId', '<span class="mono">' + escapeHtml(d.traceId) + '</span>')
        + row('请求方式', escapeHtml(d.httpMethod))
        + row('来源IP', escapeHtml(d.remoteIp))
        + row('调用方', escapeHtml(d.caller))
        + row('处理结果', statusBadge(d.status))
        + row('接收时间', escapeHtml(d.receiveTime))
        + row('耗时', d.costMs + ' ms')
        + '</div>'
        + section('请求头', d.headers)
        + section('请求报文', d.requestBody)
        + section('响应报文', d.responseBody)
        + (d.errorMsg ? section('错误信息', d.errorMsg) : '')
        + traceButtonHtml(d.apiCode, d.traceId, dateOf(d.receiveTime));
    openModal('接收日志详情 #' + id, html);
    bindTraceButton();
}

/**
 * 详情弹窗里的「从接口日志读取」按钮。
 * 数据库里的报文可能被 logBodyLimit 截断、或压根没落库，
 * 接口日志文件里还留着完整的那一份——这里是兜底入口。
 */
function traceButtonHtml(iface, traceId, date) {
    return '<div class="trace-entry" style="margin:10px 0 4px;">'
        + '<button class="btn btn-sm" type="button" id="btnTrace"'
        + ' data-iface="' + escapeHtml(iface || '') + '"'
        + ' data-trace="' + escapeHtml(traceId || '') + '"'
        + ' data-date="' + escapeHtml(date || '') + '">从接口日志读取完整报文</button>'
        + '<div id="traceResult"></div>'
        + '</div>';
}

/** 取日期部分：startTime 形如 2026-09-22 10:11:12 */
function dateOf(datetime) {
    return datetime && datetime.length >= 10 ? datetime.substring(0, 10) : '';
}

/** 绑定按钮：openModal 写的是 innerHTML，必须插入后再挂事件 */
function bindTraceButton() {
    const btn = document.getElementById('btnTrace');
    if (btn) {
        btn.addEventListener('click', () => {
            loadTracePayload(btn.dataset.iface, btn.dataset.trace, btn.dataset.date);
        });
    }
}

/** 调 trace 接口并把请求/响应报文渲染到详情弹窗里 */
async function loadTracePayload(iface, traceId, date) {
    const box = document.getElementById('traceResult');
    const btn = document.getElementById('btnTrace');
    if (!traceId) {
        if (box) box.innerHTML = '<div class="section-title">接口日志</div><pre class="code">这条日志没有 traceId，无法定位</pre>';
        return;
    }
    if (btn) {
        btn.disabled = true;
        btn.textContent = '读取中…';
    }
    try {
        let url = '/api/iface-log/trace?traceId=' + encodeURIComponent(traceId);
        if (iface) {
            url += '&iface=' + encodeURIComponent(iface);
        }
        if (date) {
            url += '&date=' + encodeURIComponent(date);
        }
        const r = await getJson(url);
        if (r.code !== 0) {
            if (box) box.innerHTML = '<div class="section-title">接口日志</div><pre class="code">' + escapeHtml(r.message) + '</pre>';
            toast(r.message, 'error');
            return;
        }
        const d = r.data || {};
        if (!d.found) {
            if (box) box.innerHTML = '<div class="section-title">接口日志</div><pre class="code">'
                + escapeHtml(d.message || '没有找到对应的接口日志') + '</pre>';
            toast('接口日志里没有这次执行的记录', 'error');
            return;
        }
        if (box) {
            box.innerHTML = payloadBlocks('请求报文', d.requests)
                + payloadBlocks('响应报文', d.responses)
                + '<div class="section-title">接口日志原文</div><pre class="code">'
                + escapeHtml(d.raw || '') + '</pre>'
                + '<div class="text-sub" style="font-size:12px;">来源文件：'
                + escapeHtml(d.file) + (d.archived ? '（归档）' : '') + '</div>';
        }
        toast('已从接口日志读取到 ' + (d.lineCount || 0) + ' 行', 'success');
    } catch (e) {
        if (box) box.innerHTML = '<div class="section-title">接口日志</div><pre class="code">读取失败：'
            + escapeHtml(e && e.message ? e.message : String(e)) + '</pre>';
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = '重新读取';
        }
    }
}

/** 多条报文按序号分段渲染；一条就不显示序号 */
function payloadBlocks(title, list) {
    if (!list || !list.length) {
        return '';
    }
    return list.map((p, i) => '<div class="section-title">' + title
        + (list.length > 1 ? '（第 ' + (i + 1) + ' / ' + list.length + ' 条）' : '')
        + '</div><pre class="code">' + escapeHtml(p) + '</pre>').join('');
}

function row(k, v) {
    return '<div class="k">' + k + '</div><div class="v">' + (v === null || v === undefined || v === '' ? '-' : v) + '</div>';
}

function section(title, content) {
    const body = content ? escapeHtml(content) : '（空）';
    return '<div class="section-title">' + title + '</div><pre class="code">' + body + '</pre>';
}

/** 读取查询表单为 URLSearchParams */
function formParams(formEl) {
    return new URLSearchParams(new FormData(formEl));
}

/** 下载：把当前筛选条件带到下载接口 */
function downloadWithFilter(formId, url, format) {
    const form = document.getElementById(formId);
    const params = formParams(form);
    params.delete('page');
    params.delete('size');
    params.set('format', format);
    location.href = url + '?' + params.toString();
}

/** 复制文本 */
function copyText(text) {
    navigator.clipboard.writeText(text).then(() => toast('已复制到剪贴板', 'success'))
        .catch(() => toast('复制失败，请手动选择', 'error'));
}

/* ==========================================================================
   外壳页（shell.html）联动：被 iframe 嵌入时的站内导航

   外壳页把每个功能页放在**独立的 iframe** 里（只有这样才能真正多开、
   切换时保留滚动位置与已填表单）。于是 iframe 内的链接分两种情况：

     · 目标与本页同路径（分页、筛选、重置）—— 留在本标签页内跳转，
       标签页数量不会爆炸；
     · 目标是别的模块（如任务列表里的"日志"、看板的"新建任务"）——
       交给外壳开成新标签页，否则当前标签页会被"改头换面"，
       出现标签名与内容对不上的情况。
   ========================================================================== */
(function () {
    if (window.self === window.top) return;   // 独立打开时不做任何干预

    // 这些地址涉及登录态切换，必须整体跳转，不能开成标签页
    const FULL_PAGE = ['/logout', '/login', '/doLogin'];

    document.addEventListener('click', function (e) {
        const a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
        if (!a) return;
        if (a.target === '_blank' || a.hasAttribute('download') || a.hasAttribute('data-no-shell')) return;

        const raw = a.getAttribute('href') || '';
        if (!raw || raw.charAt(0) === '#' || raw.indexOf('javascript:') === 0) return;

        let url;
        try {
            url = new URL(a.href, location.href);
        } catch (err) {
            return;
        }
        if (url.origin !== location.origin) return;                 // 外链不管
        if (FULL_PAGE.indexOf(url.pathname) >= 0) return;          // 登录态类：整体跳转
        if (url.pathname === location.pathname) return;            // 同模块：本标签页内跳转

        e.preventDefault();
        window.parent.postMessage({type: 'ip:open', url: url.pathname + url.search}, location.origin);
    }, true);
})();
