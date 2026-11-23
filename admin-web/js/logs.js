(async function () {
    const user = await requireAuth();
    if (!user) {
        return;
    }

    initLayout("logs", "Connection Logs", "Tra cứu lịch sử kết nối");

    const state = {
        page: 0,
        size: 25,
        clientIp: "",
        target: "",
        protocol: "",
        decision: "",
        executionStatus: "",
        from: "",
        to: ""
    };

    const table = document.getElementById("logs-table");
    const pagination = document.getElementById("logs-pagination");

    const initialClientIp = new URLSearchParams(window.location.search).get("clientIp");
    if (initialClientIp) {
        document.getElementById("log-client-ip").value = initialClientIp;
        state.clientIp = initialClientIp;
    }

    document.getElementById("log-filters")?.addEventListener("submit", (event) => {
        event.preventDefault();
        applyFilters();
    });

    document.getElementById("reset-log-filters")?.addEventListener("click", () => {
        ["log-client-ip", "log-target", "log-protocol", "log-decision", "log-status", "log-from", "log-to"]
            .forEach((id) => {
                const element = document.getElementById(id);
                if (element) {
                    element.value = "";
                }
            });
        Object.assign(state, {
            page: 0,
            clientIp: "",
            target: "",
            protocol: "",
            decision: "",
            executionStatus: "",
            from: "",
            to: ""
        });
        load();
    });

    function applyFilters() {
        state.clientIp = document.getElementById("log-client-ip").value.trim();
        state.target = document.getElementById("log-target").value.trim();
        state.protocol = document.getElementById("log-protocol").value;
        state.decision = document.getElementById("log-decision").value;
        state.executionStatus = document.getElementById("log-status").value;
        state.from = document.getElementById("log-from").value;
        state.to = document.getElementById("log-to").value;
        state.page = 0;
        load();
    }

    function renderTable(data) {
        if (!data.content.length) {
            table.innerHTML = emptyState(
                "Không có log phù hợp",
                "Thử xóa bộ lọc hoặc thay đổi khoảng thời gian.",
                "≡"
            );
            return;
        }

        table.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>Thời gian</th>
                        <th>Client</th>
                        <th>Target</th>
                        <th>Port</th>
                        <th>Protocol</th>
                        <th>Decision</th>
                        <th>Trạng thái</th>
                        <th>Rule</th>
                        <th>Reason</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    ${data.content.map((log) => `
                        <tr>
                            <td>${formatDateTime(log.occurredAt)}</td>
                            <td class="mono">${escapeHtml(log.clientIp || "—")}</td>
                            <td class="cell-target mono" title="${escapeHtml(log.target || "")}">${escapeHtml(log.target || "—")}</td>
                            <td>${escapeHtml(log.targetPort ?? "—")}</td>
                            <td>${escapeHtml(log.protocol || "—")}</td>
                            <td>${decisionBadge(log.decision)}</td>
                            <td>${statusBadge(log.executionStatus)}</td>
                            <td>${log.matchedRuleId ? `<span class="mono">#${escapeHtml(log.matchedRuleId)}</span>` : "—"}</td>
                            <td>${escapeHtml(log.reasonCode || "—")}</td>
                            <td>
                                <button class="btn btn-secondary btn-sm" type="button" data-detail="${escapeHtml(log.id)}">Chi tiết</button>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;

        table.querySelectorAll("[data-detail]").forEach((button) => {
            button.addEventListener("click", () => {
                const log = data.content.find((item) => String(item.id) === button.dataset.detail);
                if (log) {
                    openLogDetail(log);
                }
            });
        });
    }

    async function load() {
        table.innerHTML = '<div class="table-empty">Đang tải log kết nối...</div>';
        try {
            const data = await API.get(`/api/logs${queryString({
                page: state.page,
                size: state.size,
                clientIp: state.clientIp,
                target: state.target,
                protocol: state.protocol,
                decision: state.decision,
                executionStatus: state.executionStatus,
                from: state.from,
                to: state.to
            })}`);
            renderTable(data);
            renderPagination(pagination, data, (page) => {
                state.page = page;
                load();
            });
        } catch (error) {
            table.innerHTML = emptyState("Không tải được log", error.message, "!");
            pagination.innerHTML = "";
            showToast(error.message, "error");
        }
    }

    function openLogDetail(log) {
        const root = document.getElementById("modal-root");
        root.innerHTML = `
            <div class="modal-backdrop">
                <div class="modal modal--wide" role="dialog" aria-modal="true" aria-labelledby="log-detail-title">
                    <div class="modal-header">
                        <div>
                            <h2 id="log-detail-title">Chi tiết kết nối #${escapeHtml(log.id)}</h2>
                            <p>${formatDateTime(log.occurredAt)}</p>
                        </div>
                        <button class="icon-btn" type="button" data-close aria-label="Đóng">×</button>
                    </div>
                    <div class="modal-body">
                        <div class="modal-section">
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <div class="detail-label">Client IP</div>
                                    <div class="detail-value mono">${escapeHtml(log.clientIp || "—")}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Target</div>
                                    <div class="detail-value mono">${escapeHtml(log.target || "—")}${log.targetPort ? `:${escapeHtml(log.targetPort)}` : ""}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Protocol</div>
                                    <div class="detail-value">${escapeHtml(log.protocol || "—")}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Decision</div>
                                    <div class="detail-value">${decisionBadge(log.decision)}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Execution status</div>
                                    <div class="detail-value">${statusBadge(log.executionStatus)}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Matched rule</div>
                                    <div class="detail-value">${log.matchedRuleId ? `#${escapeHtml(log.matchedRuleId)}` : "—"}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Reason code</div>
                                    <div class="detail-value mono">${escapeHtml(log.reasonCode || "—")}</div>
                                </div>
                                <div class="detail-item">
                                    <div class="detail-label">Thời gian</div>
                                    <div class="detail-value">${formatDateTime(log.occurredAt)}</div>
                                </div>
                            </div>
                        </div>
                        <div class="modal-section">
                            <div class="detail-label">Error message</div>
                            <div class="detail-value mono">${escapeHtml(log.errorMessage || "—")}</div>
                        </div>
                        <div class="modal-footer">
                            <button class="btn btn-secondary" type="button" data-close>Đóng</button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    await load();
})();
