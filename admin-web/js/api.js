/* Shared API + formatting helpers for the admin dashboard. */

const API = (() => {
    async function request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }

        let response;
        try {
            response = await fetch(path, {
                credentials: "same-origin",
                ...options,
                headers
            });
        } catch (error) {
            throw new Error("Không kết nối được Admin API. Kiểm tra server đã chạy chưa.");
        }

        if (response.status === 401) {
            if (!window.location.pathname.endsWith("/login.html")) {
                window.location.replace("/login.html");
            }
            throw new Error("UNAUTHORIZED");
        }

        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch {
                data = text;
            }
        }

        if (!response.ok) {
            const message = data && typeof data === "object"
                ? (data.message || data.error)
                : data;
            throw new Error(message || `HTTP ${response.status}`);
        }

        return data;
    }

    return {
        get: (path) => request(path),
        post: (path, body) => request(path, {
            method: "POST",
            body: body === undefined ? undefined : JSON.stringify(body)
        }),
        put: (path, body) => request(path, {
            method: "PUT",
            body: JSON.stringify(body)
        }),
        patch: (path, body) => request(path, {
            method: "PATCH",
            body: JSON.stringify(body)
        }),
        delete: (path) => request(path, { method: "DELETE" })
    };
})();

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    }[character]));
}

function formatNumber(value) {
    const number = Number(value ?? 0);
    return new Intl.NumberFormat("vi-VN").format(Number.isFinite(number) ? number : 0);
}

function formatDateTime(value) {
    if (!value) {
        return "—";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "—";
    }
    return new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "medium"
    }).format(date);
}

function formatDate(value) {
    if (!value) {
        return "—";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "—";
    }
    return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(date);
}

function formatPercent(value, total) {
    const denominator = Number(total ?? 0);
    if (!denominator) {
        return "0%";
    }
    return `${Math.round((Number(value ?? 0) / denominator) * 100)}%`;
}

function formatUptime(seconds) {
    const total = Math.max(0, Number(seconds ?? 0));
    const days = Math.floor(total / 86_400);
    const hours = Math.floor((total % 86_400) / 3_600);
    const minutes = Math.floor((total % 3_600) / 60);

    if (days > 0) {
        return `${days} ngày ${hours}h`;
    }
    if (hours > 0) {
        return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
}

function debounce(callback, delay = 320) {
    let timer;
    return (...args) => {
        window.clearTimeout(timer);
        timer = window.setTimeout(() => callback(...args), delay);
    };
}

function queryString(params) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            search.set(key, value);
        }
    });
    const result = search.toString();
    return result ? `?${result}` : "";
}

function decisionBadge(value) {
    const decision = String(value || "").toUpperCase();
    if (decision === "ALLOW") {
        return '<span class="badge badge--allow"><span class="badge-dot"></span>ALLOW</span>';
    }
    if (decision === "BLOCK") {
        return '<span class="badge badge--block"><span class="badge-dot"></span>BLOCK</span>';
    }
    return `<span class="badge badge--neutral">${escapeHtml(decision || "—")}</span>`;
}

function statusBadge(value) {
    const status = String(value || "").toUpperCase();
    if (status === "CONNECTED") {
        return '<span class="badge badge--connected">CONNECTED</span>';
    }
    if (status === "BLOCKED") {
        return '<span class="badge badge--warning">BLOCKED</span>';
    }
    if (status === "ERROR") {
        return '<span class="badge badge--error">ERROR</span>';
    }
    return `<span class="badge badge--neutral">${escapeHtml(status || "—")}</span>`;
}

function actionBadge(value) {
    const action = String(value || "").toUpperCase();
    const tone = action === "DELETE" || action === "DISABLE"
        ? "badge--danger"
        : action === "CREATE" || action === "ENABLE"
            ? "badge--success"
            : action === "UPDATE" || action === "CHANGE_PASSWORD"
                ? "badge--info"
                : "badge--violet";
    return `<span class="badge ${tone}">${escapeHtml(action || "—")}</span>`;
}

function renderPagination(container, pageData, onPage) {
    if (!container) {
        return;
    }
    const page = Number(pageData.page ?? 0);
    const totalPages = Number(pageData.totalPages ?? 0);
    const totalElements = Number(pageData.totalElements ?? 0);

    if (totalPages <= 1) {
        container.innerHTML = `<span class="page-info">${formatNumber(totalElements)} bản ghi</span>`;
        return;
    }

    container.innerHTML = `
        <button type="button" class="page-btn" data-page="${page - 1}" ${page <= 0 ? "disabled" : ""}>← Trước</button>
        <span class="page-info">Trang ${page + 1} / ${totalPages} · ${formatNumber(totalElements)} bản ghi</span>
        <button type="button" class="page-btn" data-page="${page + 1}" ${page >= totalPages - 1 ? "disabled" : ""}>Sau →</button>
    `;

    container.querySelectorAll("[data-page]").forEach((button) => {
        button.addEventListener("click", () => {
            const nextPage = Number(button.dataset.page);
            if (Number.isInteger(nextPage) && nextPage >= 0 && nextPage < totalPages) {
                onPage(nextPage);
            }
        });
    });
}

function emptyState(title, subtitle = "", icon = "◌") {
    return `
        <div class="empty-state">
            <div class="empty-state-icon">${escapeHtml(icon)}</div>
            <strong>${escapeHtml(title)}</strong>
            <span>${escapeHtml(subtitle)}</span>
        </div>
    `;
}

function showToast(message, type = "info") {
    const root = document.getElementById("toast-root");
    if (!root) {
        return;
    }
    const icon = type === "success" ? "✓" : type === "error" ? "✕" : type === "warning" ? "!" : "i";
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `
        <span class="toast-icon">${icon}</span>
        <span class="toast-message">${escapeHtml(message)}</span>
    `;
    root.appendChild(toast);

    window.setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(6px)";
        toast.style.transition = "opacity .2s ease, transform .2s ease";
        window.setTimeout(() => toast.remove(), 220);
    }, 4_200);
}

function closeModal() {
    const root = document.getElementById("modal-root");
    if (root) {
        root.innerHTML = "";
    }
}

function valueOrDash(value) {
    return value === null || value === undefined || value === ""
        ? "—"
        : value;
}
