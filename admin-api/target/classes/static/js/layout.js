const NAV_ITEMS = [
    { id: "dashboard", label: "Tổng quan", href: "/pages/dashboard.html", icon: "▦" },
    { id: "rules", label: "Access Rules", href: "/pages/access-rules.html", icon: "⚙" },
    { id: "logs", label: "Connection Logs", href: "/pages/connection-logs.html", icon: "≡" },
    { id: "clients", label: "Clients", href: "/pages/clients.html", icon: "◉" },
    { id: "audit", label: "Audit Logs", href: "/pages/audit-logs.html", icon: "⏱" }
];

function initials(name) {
    return String(name || "A")
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part.charAt(0))
        .join("")
        .toUpperCase();
}

function setProxyBadge(stats) {
    const badge = document.getElementById("proxy-live-badge");
    if (!badge) {
        return;
    }
    if (stats && stats.online) {
        badge.className = "badge badge--success";
        badge.innerHTML = `<span class="badge-dot"></span>Proxy online · ${formatNumber(stats.activeClients)} clients`;
    } else {
        badge.className = "badge badge--danger";
        badge.innerHTML = `<span class="badge-dot"></span>Proxy offline`;
    }
}

async function loadProxyBadge() {
    try {
        const summary = await API.get("/api/dashboard/summary");
        setProxyBadge(summary.proxy);
    } catch {
        setProxyBadge(null);
    }
}

function renderSidebar(active) {
    const sidebar = document.getElementById("sidebar");
    if (!sidebar) {
        return;
    }

    sidebar.innerHTML = `
        <div class="brand">
            <div class="brand-logo">NP</div>
            <div>
                <div class="brand-name">Network Proxy</div>
                <div class="brand-sub">Admin Console</div>
            </div>
        </div>
        <nav class="nav" aria-label="Điều hướng chính">
            <div class="nav-label">Quản lý</div>
            ${NAV_ITEMS.map((item) => `
                <a class="nav-link ${item.id === active ? "active" : ""}" href="${item.href}">
                    <span class="nav-icon">${item.icon}</span>
                    <span>${item.label}</span>
                </a>
            `).join("")}
        </nav>
        <div class="sidebar-footer">
            <div class="sidebar-status">
                <span class="status-dot"></span>
                <span>Admin API online</span>
            </div>
            <div class="sidebar-version">Phase 6 · v1.0</div>
        </div>
    `;
}

function renderTopbar(title, subtitle) {
    const topbar = document.getElementById("topbar");
    if (!topbar) {
        return;
    }
    const user = getCurrentUser();
    const displayName = user?.displayName || user?.username || "Admin";

    topbar.innerHTML = `
        <div class="topbar-left">
            <button class="icon-btn mobile-menu-btn" id="mobile-menu-toggle" type="button" aria-label="Mở menu">☰</button>
            <div>
                <div class="topbar-title">${escapeHtml(title)}</div>
                <div class="topbar-subtitle">${escapeHtml(subtitle || "")}</div>
            </div>
        </div>
        <div class="topbar-actions">
            <span class="badge badge--neutral" id="proxy-live-badge">Proxy: đang kiểm tra</span>
            <div class="user-chip">
                <div class="avatar">${escapeHtml(initials(displayName))}</div>
                <div class="user-meta">
                    <div class="user-name">${escapeHtml(displayName)}</div>
                    <div class="user-role">Quản trị viên</div>
                </div>
                <button class="icon-btn" id="user-menu-toggle" type="button" aria-label="Tài khoản">▾</button>
            </div>
            <div class="user-menu" id="user-menu" hidden>
                <button class="menu-item" id="change-password-btn" type="button">Đổi mật khẩu</button>
                <button class="menu-item menu-item--danger" id="logout-btn" type="button">Đăng xuất</button>
            </div>
        </div>
    `;

    document.getElementById("logout-btn")?.addEventListener("click", logout);
    document.getElementById("change-password-btn")?.addEventListener("click", openChangePasswordModal);

    document.getElementById("mobile-menu-toggle")?.addEventListener("click", () => {
        document.getElementById("sidebar")?.classList.toggle("open");
    });

    const menu = document.getElementById("user-menu");
    document.getElementById("user-menu-toggle")?.addEventListener("click", (event) => {
        event.stopPropagation();
        if (menu) {
            menu.hidden = !menu.hidden;
        }
    });
    document.addEventListener("click", () => {
        if (menu) {
            menu.hidden = true;
        }
    });
}

function openChangePasswordModal() {
    const root = document.getElementById("modal-root");
    if (!root) {
        return;
    }

    root.innerHTML = `
        <div class="modal-backdrop">
            <div class="modal" role="dialog" aria-modal="true" aria-labelledby="change-password-title">
                <div class="modal-header">
                    <div>
                        <h2 id="change-password-title">Đổi mật khẩu</h2>
                        <p>Cập nhật mật khẩu đăng nhập Admin Console.</p>
                    </div>
                    <button class="icon-btn" type="button" data-close aria-label="Đóng">×</button>
                </div>
                <form class="modal-body" id="change-password-form">
                    <div class="form-grid">
                        <label class="field field--full">
                            <span>Mật khẩu hiện tại</span>
                            <input class="input" type="password" id="current-password" autocomplete="current-password" required>
                        </label>
                        <label class="field field--full">
                            <span>Mật khẩu mới</span>
                            <input class="input" type="password" id="new-password" autocomplete="new-password" minlength="6" required>
                        </label>
                        <label class="field field--full">
                            <span>Nhập lại mật khẩu mới</span>
                            <input class="input" type="password" id="confirm-password" autocomplete="new-password" minlength="6" required>
                        </label>
                    </div>
                    <div class="form-error" id="change-password-error"></div>
                    <div class="modal-footer">
                        <button class="btn btn-ghost" type="button" data-close>Hủy</button>
                        <button class="btn btn-primary" type="submit" id="change-password-submit">Lưu mật khẩu</button>
                    </div>
                </form>
            </div>
        </div>
    `;

    const form = document.getElementById("change-password-form");
    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const currentPassword = document.getElementById("current-password").value;
        const newPassword = document.getElementById("new-password").value;
        const confirmPassword = document.getElementById("confirm-password").value;
        const error = document.getElementById("change-password-error");
        const submit = document.getElementById("change-password-submit");

        error.textContent = "";
        if (newPassword !== confirmPassword) {
            error.textContent = "Mật khẩu nhập lại không khớp.";
            return;
        }

        submit.disabled = true;
        submit.textContent = "Đang lưu...";
        try {
            await changePassword(currentPassword, newPassword);
            showToast("Đã đổi mật khẩu thành công.", "success");
            closeModal();
        } catch (exception) {
            error.textContent = exception.message;
        } finally {
            submit.disabled = false;
            submit.textContent = "Lưu mật khẩu";
        }
    });
}

function initLayout(active, title, subtitle) {
    renderSidebar(active);
    renderTopbar(title, subtitle);

    const root = document.getElementById("modal-root");
    root?.addEventListener("click", (event) => {
        if (event.target === root
            || event.target.classList.contains("modal-backdrop")
            || event.target.closest("[data-close]")) {
            closeModal();
        }
    });

    if (active !== "dashboard") {
        loadProxyBadge();
    }
}
