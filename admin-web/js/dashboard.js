(async function () {
    const user = await requireAuth();
    if (!user) {
        return;
    }

    initLayout("dashboard", "Tổng quan", "Theo dõi hoạt động proxy");
    document.getElementById("refresh-dashboard")?.addEventListener("click", load);

    function renderStats(data) {
        const container = document.getElementById("stat-grid");
        const cards = [
            {
                tone: "blue",
                label: "Tổng rule",
                value: formatNumber(data.totalRules),
                hint: `<strong>${formatNumber(data.enabledRules)}</strong> rule đang bật`,
                icon: "⚙"
            },
            {
                tone: "violet",
                label: "Tổng kết nối",
                value: formatNumber(data.totalLogs),
                hint: `<strong>${formatNumber(data.last24hLogs)}</strong> trong 24 giờ`,
                icon: "⇄"
            },
            {
                tone: "red",
                label: "Bị chặn",
                value: formatNumber(data.blockedLogs),
                hint: `<strong>${formatPercent(data.blockedLogs, data.totalLogs)}</strong> tổng lưu lượng`,
                icon: "⛔"
            },
            {
                tone: "green",
                label: "Client",
                value: formatNumber(data.distinctClients),
                hint: "địa chỉ IP duy nhất",
                icon: "◉"
            }
        ];

        container.innerHTML = cards.map((card) => `
            <article class="stat-card stat-card--${card.tone}">
                <div class="stat-top">
                    <span class="stat-label">${card.label}</span>
                    <span class="stat-icon">${card.icon}</span>
                </div>
                <div class="stat-value">${card.value}</div>
                <div class="stat-hint">${card.hint}</div>
            </article>
        `).join("");
    }

    function renderDecisionChart(data) {
        const container = document.getElementById("decision-chart");
        const total = Number(data.allowedLogs || 0)
            + Number(data.blockedLogs || 0)
            + Number(data.errorLogs || 0);

        if (!total) {
            container.innerHTML = emptyState(
                "Chưa có log kết nối",
                "Các quyết định ALLOW / BLOCK sẽ xuất hiện sau khi proxy xử lý request.",
                "≡"
            );
            return;
        }

        const rows = [
            { label: "ALLOW", value: data.allowedLogs, className: "allow" },
            { label: "BLOCK", value: data.blockedLogs, className: "block" },
            { label: "ERROR", value: data.errorLogs, className: "error" }
        ];

        container.innerHTML = `
            ${rows.map((row) => {
                const value = Number(row.value || 0);
                const percent = Math.round((value / total) * 100);
                return `
                    <div class="decision-row">
                        <span class="decision-label">${row.label}</span>
                        <div class="decision-track">
                            <div class="decision-fill decision-fill--${row.className}" style="width:${percent}%"></div>
                        </div>
                        <span class="decision-value">${formatNumber(value)}</span>
                    </div>
                `;
            }).join("")}
            <div class="decision-total">
                <span>Tổng cộng</span>
                <strong>${formatNumber(total)}</strong>
            </div>
        `;
    }

    function renderProxy(data) {
        const proxy = data.proxy || {};
        const container = document.getElementById("proxy-status");
        const badge = document.getElementById("proxy-status-badge");

        if (proxy.online) {
            badge.className = "badge badge--success";
            badge.innerHTML = '<span class="badge-dot"></span>Online';
            container.innerHTML = `
                <div class="proxy-status-head">
                    <span class="proxy-dot proxy-dot--online"></span>
                    <div>
                        <div class="proxy-status-title">proxy-server đang hoạt động</div>
                        <div class="proxy-status-message">${escapeHtml(proxy.version || "Control API đã kết nối")}</div>
                    </div>
                </div>
                <div class="proxy-metrics">
                    <div class="proxy-metric">
                        <div class="proxy-metric-value">${formatNumber(proxy.activeClients)}</div>
                        <div class="proxy-metric-label">Active clients</div>
                    </div>
                    <div class="proxy-metric">
                        <div class="proxy-metric-value">${formatNumber(proxy.loadedRules)}</div>
                        <div class="proxy-metric-label">Loaded rules</div>
                    </div>
                    <div class="proxy-metric">
                        <div class="proxy-metric-value">${formatUptime(proxy.uptimeSeconds)}</div>
                        <div class="proxy-metric-label">Uptime</div>
                    </div>
                </div>
                <div class="proxy-note">Rule mới sẽ được gửi reload tới proxy ngay sau khi lưu.</div>
            `;
            return;
        }

        badge.className = "badge badge--danger";
        badge.innerHTML = '<span class="badge-dot"></span>Offline';
        container.innerHTML = `
            <div class="proxy-status-head">
                <span class="proxy-dot proxy-dot--offline"></span>
                <div>
                    <div class="proxy-status-title">Chưa kết nối proxy control</div>
                    <div class="proxy-status-message">${escapeHtml(proxy.message || "Kiểm tra proxy-server và cấu hình proxy.control.*")}</div>
                </div>
            </div>
            <div class="proxy-metrics">
                <div class="proxy-metric">
                    <div class="proxy-metric-value">—</div>
                    <div class="proxy-metric-label">Active clients</div>
                </div>
                <div class="proxy-metric">
                    <div class="proxy-metric-value">—</div>
                    <div class="proxy-metric-label">Loaded rules</div>
                </div>
                <div class="proxy-metric">
                    <div class="proxy-metric-value">—</div>
                    <div class="proxy-metric-label">Uptime</div>
                </div>
            </div>
            <div class="proxy-note">
                Bật <span class="mono">proxy.control.enabled=true</span> trong proxy-server
                và cấu hình cùng token với Admin API để dùng tính năng reload.
            </div>
        `;
    }

    function renderTopTargets(data) {
        const container = document.getElementById("top-targets");
        const targets = Array.isArray(data.topTargets) ? data.topTargets : [];
        if (!targets.length) {
            container.innerHTML = emptyState(
                "Chưa có dữ liệu target",
                "Log kết nối sẽ được tổng hợp theo target tại đây.",
                "◎"
            );
            return;
        }

        const max = Math.max(...targets.map((item) => Number(item.count || 0)), 1);
        container.innerHTML = targets.map((item) => {
            const count = Number(item.count || 0);
            return `
                <div class="top-target-item">
                    <span class="top-target-name" title="${escapeHtml(item.target || "")}">${escapeHtml(item.target || "(không xác định)")}</span>
                    <span class="top-target-count">${formatNumber(count)}</span>
                    <div class="top-target-track">
                        <div class="top-target-fill" style="width:${Math.max(3, Math.round((count / max) * 100))}%"></div>
                    </div>
                </div>
            `;
        }).join("");
    }

    function activityIcon(action) {
        const value = String(action || "").toUpperCase();
        if (value === "CREATE") return "+";
        if (value === "UPDATE") return "✎";
        if (value === "DELETE") return "×";
        if (value === "LOGIN") return "→";
        if (value === "LOGOUT") return "←";
        if (value === "ENABLE") return "✓";
        if (value === "DISABLE") return "–";
        return "•";
    }

    function renderRecentAudit(data) {
        const container = document.getElementById("recent-audit");
        const logs = Array.isArray(data.recentAudit) ? data.recentAudit : [];
        if (!logs.length) {
            container.innerHTML = emptyState(
                "Chưa có hoạt động quản trị",
                "Các thao tác thêm/sửa/xóa rule sẽ được ghi lại tại đây.",
                "⏱"
            );
            return;
        }

        container.innerHTML = logs.map((log) => `
            <div class="activity-item">
                <div class="activity-icon">${activityIcon(log.action)}</div>
                <div class="activity-content">
                    <div class="activity-title">
                        ${actionBadge(log.action)}
                        ${escapeHtml(log.entityType || "")}
                        ${log.entityId ? `#${escapeHtml(log.entityId)}` : ""}
                    </div>
                    <div class="activity-meta">
                        ${escapeHtml(log.username || "system")} · ${formatDateTime(log.createdAt)}
                    </div>
                </div>
            </div>
        `).join("");
    }

    function renderRecentLogs(data) {
        const container = document.getElementById("recent-logs");
        const logs = Array.isArray(data.recentLogs) ? data.recentLogs : [];
        if (!logs.length) {
            container.innerHTML = emptyState(
                "Chưa có kết nối",
                "Proxy sẽ ghi log khi client gửi request qua HTTP/HTTPS/SSH.",
                "⇄"
            );
            return;
        }

        container.innerHTML = `
            <table class="recent-logs-table">
                <thead>
                    <tr>
                        <th>Thời gian</th>
                        <th>Client</th>
                        <th>Target</th>
                        <th>Protocol</th>
                        <th>Quyết định</th>
                        <th>Trạng thái</th>
                    </tr>
                </thead>
                <tbody>
                    ${logs.map((log) => `
                        <tr>
                            <td>${formatDateTime(log.occurredAt)}</td>
                            <td class="mono">${escapeHtml(log.clientIp || "—")}</td>
                            <td class="cell-target mono" title="${escapeHtml(log.target || "")}">
                                ${escapeHtml(log.target || "—")}${log.targetPort ? `:${log.targetPort}` : ""}
                            </td>
                            <td>${escapeHtml(log.protocol || "—")}</td>
                            <td>${decisionBadge(log.decision)}</td>
                            <td>${statusBadge(log.executionStatus)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    }

    function render(data) {
        renderStats(data);
        renderDecisionChart(data);
        renderProxy(data);
        renderTopTargets(data);
        renderRecentAudit(data);
        renderRecentLogs(data);
        setProxyBadge(data.proxy);
    }

    async function load() {
        const button = document.getElementById("refresh-dashboard");
        if (button) {
            button.disabled = true;
            button.textContent = "Đang tải...";
        }
        try {
            const data = await API.get("/api/dashboard/summary");
            render(data);
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            if (button) {
                button.disabled = false;
                button.textContent = "Làm mới";
            }
        }
    }

    await load();
})();
