(async function () {
    const user = await requireAuth();
    if (!user) {
        return;
    }

    initLayout("clients", "Clients", "Tổng hợp lưu lượng theo IP");

    const state = {
        page: 0,
        size: 25,
        q: ""
    };

    const table = document.getElementById("clients-table");
    const pagination = document.getElementById("clients-pagination");
    const searchInput = document.getElementById("client-search");

    document.getElementById("client-filters")?.addEventListener("submit", (event) => {
        event.preventDefault();
        state.q = searchInput.value.trim();
        state.page = 0;
        load();
    });

    document.getElementById("reset-client-filters")?.addEventListener("click", () => {
        searchInput.value = "";
        state.q = "";
        state.page = 0;
        load();
    });

    searchInput?.addEventListener("input", debounce(() => {
        state.q = searchInput.value.trim();
        state.page = 0;
        load();
    }));

    function renderTable(data) {
        if (!data.content.length) {
            table.innerHTML = emptyState(
                "Chưa có client nào",
                "Dữ liệu sẽ xuất hiện sau khi proxy ghi nhận kết nối.",
                "◉"
            );
            return;
        }

        table.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>Client IP</th>
                        <th>Tổng request</th>
                        <th>ALLOW</th>
                        <th>BLOCK</th>
                        <th>ERROR</th>
                        <th>Tỉ lệ block</th>
                        <th>Lần cuối</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    ${data.content.map((client) => `
                        <tr>
                            <td class="mono"><strong>${escapeHtml(client.clientIp || "—")}</strong></td>
                            <td>${formatNumber(client.totalRequests)}</td>
                            <td><span class="text-success">${formatNumber(client.allowed)}</span></td>
                            <td><span class="text-danger">${formatNumber(client.blocked)}</span></td>
                            <td>${formatNumber(client.errors)}</td>
                            <td>${formatPercent(client.blocked, client.totalRequests)}</td>
                            <td>${formatDateTime(client.lastSeen)}</td>
                            <td>
                                <a class="btn btn-secondary btn-sm"
                                   href="connection-logs.html?clientIp=${encodeURIComponent(client.clientIp || "")}">
                                    Xem log
                                </a>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    }

    async function load() {
        table.innerHTML = '<div class="table-empty">Đang tải danh sách client...</div>';
        try {
            const data = await API.get(`/api/clients${queryString({
                page: state.page,
                size: state.size,
                q: state.q
            })}`);
            renderTable(data);
            renderPagination(pagination, data, (page) => {
                state.page = page;
                load();
            });
        } catch (error) {
            table.innerHTML = emptyState("Không tải được client", error.message, "!");
            pagination.innerHTML = "";
            showToast(error.message, "error");
        }
    }

    await load();
})();
