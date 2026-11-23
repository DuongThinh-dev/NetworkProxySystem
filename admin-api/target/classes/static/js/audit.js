(async function () {
    const user = await requireAuth();
    if (!user) {
        return;
    }

    initLayout("audit", "Audit Logs", "Nhật ký thao tác quản trị");

    const state = {
        page: 0,
        size: 25
    };

    const table = document.getElementById("audit-table");
    const pagination = document.getElementById("audit-pagination");

    function renderTable(data) {
        if (!data.content.length) {
            table.innerHTML = emptyState(
                "Chưa có audit log",
                "Các thao tác đăng nhập và thay đổi rule sẽ được ghi lại tại đây.",
                "⏱"
            );
            return;
        }

        table.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>Thời gian</th>
                        <th>Người dùng</th>
                        <th>Hành động</th>
                        <th>Đối tượng</th>
                        <th>ID</th>
                        <th>Chi tiết</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.content.map((log) => `
                        <tr>
                            <td>${formatDateTime(log.createdAt)}</td>
                            <td>${escapeHtml(log.username || "system")}</td>
                            <td>${actionBadge(log.action)}</td>
                            <td>${escapeHtml(log.entityType || "—")}</td>
                            <td>${log.entityId ? `<span class="mono">#${escapeHtml(log.entityId)}</span>` : "—"}</td>
                            <td class="cell-target" title="${escapeHtml(log.details || "")}">${escapeHtml(log.details || "—")}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    }

    async function load() {
        table.innerHTML = '<div class="table-empty">Đang tải audit log...</div>';
        try {
            const data = await API.get(`/api/audit-logs${queryString({
                page: state.page,
                size: state.size
            })}`);
            renderTable(data);
            renderPagination(pagination, data, (page) => {
                state.page = page;
                load();
            });
        } catch (error) {
            table.innerHTML = emptyState("Không tải được audit log", error.message, "!");
            pagination.innerHTML = "";
            showToast(error.message, "error");
        }
    }

    await load();
})();
