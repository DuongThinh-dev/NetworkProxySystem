const AUTH_USER_KEY = "networkProxyAdminUser";

function saveCurrentUser(user) {
    sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

function getCurrentUser() {
    try {
        return JSON.parse(sessionStorage.getItem(AUTH_USER_KEY));
    } catch {
        return null;
    }
}

async function requireAuth() {
    try {
        const user = await API.get("/api/auth/me");
        saveCurrentUser(user);
        return user;
    } catch (error) {
        if (error.message !== "UNAUTHORIZED") {
            console.error(error);
        }
        sessionStorage.removeItem(AUTH_USER_KEY);
        window.location.replace("/login.html");
        return null;
    }
}

async function logout() {
    try {
        await API.post("/api/auth/logout");
    } catch {
        // Session may already be expired; still clear local state.
    }
    sessionStorage.removeItem(AUTH_USER_KEY);
    window.location.replace("/login.html");
}

async function changePassword(currentPassword, newPassword) {
    return API.post("/api/auth/change-password", {
        currentPassword,
        newPassword
    });
}
