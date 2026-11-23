(async function () {
    const user = await requireAuth();
    if (!user) {
        return;
    }

    initLayout("rules", "Access Rules", "Quản lý quy tắc ALLOW / BLOCK");

    const state = {
        page: 0,
        size: 20,
        q: "",
        enabled: ""
    };

    const table = document.getElementById("rules-table");
    const pagination = document.getElementById("rules-pagination");
    const searchInput = document.getElementById("rule-search");
    const enabledSelect = document.getElementById("rule-enabled");

    document.getElementById("add-rule")?.addEventListener("click", () => openRuleModal(null));

    document.getElementById("rule-filters")?.addEventListener("submit", (event) => {
        event.preventDefault();
        applyFilters();
    });

    document.getElementById("reset-rule-filters")?.addEventListener("click", () => {
        searchInput.value = "";
        enabledSelect.value = "";
        state.q = "";
        state.enabled = "";
        state.page = 0;
        load();
    });

    searchInput?.addEventListener("input", debounce(() => {
        state.q = searchInput.value.trim();
        state.page = 0;
        load();
    }));

    enabledSelect?.addEventListener("change", () => {
        state.enabled = enabledSelect.value;
        state.page = 0;
        load();
    });

    function applyFilters() {
        state.q = searchInput.value.trim();
        state.enabled = enabledSelect.value;
        state.page = 0;
        load();
    }

    function displayAny(value, fallback) {
        return value
            ? `<span class="mono">${escapeHtml(value)}</span>`
            : `<span class="muted">${fallback}</span>`;
    }

    function renderTable(data) {
        if (!data.content.length) {
            table.innerHTML = emptyState(
                "Chưa có rule nào",
                "Bấm “Thêm rule” để tạo quy tắc ALLOW hoặc BLOCK đầu tiên.",
                "⚙"
            );
            return;
        }

        table.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Client IP</th>
                        <th>Target</th>
                        <th>Port</th>
                        <th>Protocol</th>
                        <th>Action</th>
                        <th>Priority</th>
                        <th>Bật</th>
                        <th>Cập nhật</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    ${data.content.map((rule) => `
                        <tr>
                            <td class="mono">#${escapeHtml(rule.id)}</td>
                            <td>${displayAny(rule.clientIp, "Mọi IP")}</td>
                            <td class="cell-target" title="${escapeHtml(rule.target || "")}">
                                ${displayAny(rule.target, "Mọi target")}
                            </td>
                            <td>${rule.targetPort ? escapeHtml(rule.targetPort) : '<span class="muted">Mọi port</span>'}</td>
                            <td>${rule.protocol ? `<span class="badge badge--neutral">${escapeHtml(rule.protocol)}</span>` : '<span class="muted">Mọi protocol</span>'}</td>
                            <td>${decisionBadge(rule.action)}</td>
                            <td>${formatNumber(rule.priority)}</td>
                            <td>
                                <label class="switch" title="${rule.enabled ? "Đang bật" : "Đang tắt"}">
                                    <input type="checkbox" data-toggle="${escapeHtml(rule.id)}" ${rule.enabled ? "checked" : ""}>
                                    <span class="switch-track"></span>
                                </label>
                            </td>
                            <td>${formatDateTime(rule.updatedAt)}</td>
                            <td>
                                <div class="cell-actions">
                                    <button class="btn btn-secondary btn-sm" type="button" data-edit="${escapeHtml(rule.id)}">Sửa</button>
                                    <button class="btn btn-ghost btn-sm" type="button" data-delete="${escapeHtml(rule.id)}">Xóa</button>
                                </div>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;

        table.querySelectorAll("[data-edit]").forEach((button) => {
            button.addEventListener("click", () => {
                const rule = data.content.find((item) => String(item.id) === button.dataset.edit);
                if (rule) {
                    openRuleModal(rule);
                }
            });
        });

        table.querySelectorAll("[data-delete]").forEach((button) => {
            button.addEventListener("click", () => deleteRule(Number(button.dataset.delete)));
        });

        table.querySelectorAll("[data-toggle]").forEach((input) => {
            input.addEventListener("change", () => toggleRule(Number(input.dataset.toggle), input));
        });
    }

    async function load() {
        table.innerHTML = '<div class="table-empty">Đang tải danh sách rule...</div>';
        try {
            const data = await API.get(`/api/rules${queryString({
                page: state.page,
                size: state.size,
                q: state.q,
                enabled: state.enabled
            })}`);
            renderTable(data);
            renderPagination(pagination, data, (page) => {
                state.page = page;
                load();
            });
        } catch (error) {
            table.innerHTML = emptyState("Không tải được rule", error.message, "!");
            pagination.innerHTML = "";
            showToast(error.message, "error");
        }
    }

    async function toggleRule(id, input) {
        input.disabled = true;
        try {
            const result = await API.patch(`/api/rules/${id}/toggle`, {
                enabled: input.checked
            });
            showToast(
                input.checked ? `Đã bật rule #${id}.` : `Đã tắt rule #${id}.`,
                "success"
            );
            if (result.warning) {
                showToast(result.warning, "warning");
            }
            await load();
        } catch (error) {
            input.checked = !input.checked;
            showToast(error.message, "error");
        } finally {
            input.disabled = false;
        }
    }

    async function deleteRule(id) {
        if (!window.confirm(`Xóa rule #${id}? Thao tác này không thể hoàn tác.`)) {
            return;
        }
        try {
            const result = await API.delete(`/api/rules/${id}`);
            showToast(`Đã xóa rule #${id}.`, "success");
            if (result.warning) {
                showToast(result.warning, "warning");
            }
            await load();
        } catch (error) {
            showToast(error.message, "error");
        }
    }

    function openRuleModal(rule) {
        const isEdit = Boolean(rule);
        const root = document.getElementById("modal-root");
        root.innerHTML = `
            <div class="modal-backdrop">
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="rule-modal-title">
                    <div class="modal-header">
                        <div>
                            <h2 id="rule-modal-title">${isEdit ? `Sửa rule #${escapeHtml(rule.id)}` : "Thêm rule"}</h2>
                            <p>Để trống IP, target, port hoặc protocol để áp dụng cho mọi giá trị.</p>
                        </div>
                        <button class="icon-btn" type="button" data-close aria-label="Đóng">×</button>
                    </div>
                    <form class="modal-body" id="rule-form">
                        <div class="form-grid">
                            <label class="field">
                                <span>Client IP</span>
                                <input class="input" id="rule-client-ip" maxlength="45" placeholder="Ví dụ: 127.0.0.1">
                            </label>
                            <label class="field">
                                <span>Target</span>
                                <input class="input" id="rule-target" maxlength="253" placeholder="Ví dụ: example.com">
                            </label>
                            <label class="field">
                                <span>Port</span>
                                <input class="input" id="rule-port" type="number" min="1" max="65535" placeholder="Mọi port">
                            </label>
                            <label class="field">
                                <span>Protocol</span>
                                <select class="select" id="rule-protocol">
                                    <option value="">Mọi protocol</option>
                                    <option value="HTTP">HTTP</option>
                                    <option value="HTTPS">HTTPS</option>
                                    <option value="SSH">SSH</option>
                                </select>
                            </label>
                            <label class="field">
                                <span>Action</span>
                                <select class="select" id="rule-action">
                                    <option value="ALLOW">ALLOW</option>
                                    <option value="BLOCK">BLOCK</option>
                                </select>
                            </label>
                            <label class="field">
                                <span>Priority</span>
                                <input class="input" id="rule-priority" type="number" min="0" value="0">
                            </label>
                        </div>
                        <label class="checkbox-row" style="margin-top:16px;">
                            <input type="checkbox" id="rule-enabled" checked>
                            Kích hoạt rule
                        </label>
                        <div class="form-error" id="rule-form-error"></div>
                        <div class="modal-footer">
                            <button class="btn btn-ghost" type="button" data-close>Hủy</button>
                            <button class="btn btn-primary" type="submit" id="rule-submit">${isEdit ? "Lưu thay đổi" : "Tạo rule"}</button>
                        </div>
                    </form>
                </div>
            </div>
        `;

        if (rule) {
            document.getElementById("rule-client-ip").value = rule.clientIp || "";
            document.getElementById("rule-target").value = rule.target || "";
            document.getElementById("rule-port").value = rule.targetPort ?? "";
            document.getElementById("rule-protocol").value = rule.protocol || "";
            document.getElementById("rule-action").value = rule.action || "ALLOW";
            document.getElementById("rule-priority").value = rule.priority ?? 0;
            document.getElementById("rule-enabled").checked = Boolean(rule.enabled);
        }

        document.getElementById("rule-form")?.addEventListener("submit", async (event) => {
            event.preventDefault();
            const error = document.getElementById("rule-form-error");
            const submit = document.getElementById("rule-submit");
            error.textContent = "";

            const portValue = document.getElementById("rule-port").value.trim();
            const targetPort = portValue ? Number(portValue) : null;
            if (targetPort !== null && (!Number.isInteger(targetPort) || targetPort < 1 || targetPort > 65535)) {
                error.textContent = "Port phải là số từ 1 đến 65535.";
                return;
            }

            const payload = {
                clientIp: document.getElementById("rule-client-ip").value.trim() || null,
                target: document.getElementById("rule-target").value.trim() || null,
                targetPort,
                protocol: document.getElementById("rule-protocol").value || null,
                action: document.getElementById("rule-action").value,
                priority: Number(document.getElementById("rule-priority").value || 0),
                enabled: document.getElementById("rule-enabled").checked
            };

            submit.disabled = true;
            submit.textContent = "Đang lưu...";
            try {
                const result = isEdit
                    ? await API.put(`/api/rules/${rule.id}`, payload)
                    : await API.post("/api/rules", payload);
                showToast(isEdit ? "Đã cập nhật rule." : "Đã tạo rule mới.", "success");
                if (result.warning) {
                    showToast(result.warning, "warning");
                }
                closeModal();
                await load();
            } catch (exception) {
                error.textContent = exception.message;
            } finally {
                submit.disabled = false;
                submit.textContent = isEdit ? "Lưu thay đổi" : "Tạo rule";
            }
        });
    }

    await load();
})();
