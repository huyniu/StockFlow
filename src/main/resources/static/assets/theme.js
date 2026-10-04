/* Điều khiển giao diện sáng/tối xanh dương, độc lập với đăng nhập, giỏ hàng và API nghiệp vụ. */
(() => {
    'use strict';

    const THEME_KEY = 'stockflow.web.theme';
    const root = document.documentElement;
    const systemTheme =
        typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    let preference = readPreference();

    /** Chỉ đọc khóa giao diện; trình duyệt chặn lưu trữ vẫn có thể đổi màu trong phiên hiện tại. */
    function readPreference() {
        try {
            const value = window.localStorage.getItem(THEME_KEY);
            return value === 'light' || value === 'dark' ? value : null;
        } catch {
            return null;
        }
    }

    /** Khi chưa có lựa chọn riêng, dùng chế độ của máy; thiếu matchMedia thì dùng giao diện sáng. */
    function preferredTheme() {
        return preference ?? (systemTheme?.matches ? 'dark' : 'light');
    }

    /** Đồng bộ màu trang, điều khiển gốc của trình duyệt và trạng thái nút dành cho bàn phím/trình đọc màn hình. */
    function applyTheme(theme) {
        root.dataset.theme = theme;
        root.style.colorScheme = theme;
        const meta = document.querySelector('meta[name="theme-color"]');
        // Thanh trình duyệt dùng xanh nhận diện khi sáng và navy khi tối, không giữ màu xanh lá cũ.
        if (meta) meta.content = theme === 'dark' ? '#0b1220' : '#2563eb';

        const button = document.getElementById('theme-toggle');
        if (!button) return;
        const dark = theme === 'dark';
        button.setAttribute('aria-pressed', String(dark));
        button.title = dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối';
        const label = button.querySelector('.theme-toggle-label');
        if (label) label.textContent = dark ? 'Tối' : 'Sáng';
        button.hidden = false;
    }

    // Thực hiện ngay trong head; không đợi API hoặc DOMContentLoaded để chọn đúng nền ban đầu.
    applyTheme(preferredTheme());

    /** Bấm nút chỉ đổi bảng màu và lưu sở thích, không tải lại trang hay gửi request nghiệp vụ. */
    function initializeToggle() {
        const button = document.getElementById('theme-toggle');
        if (!button) return;
        applyTheme(preferredTheme());
        button.addEventListener('click', () => {
            preference = root.dataset.theme === 'dark' ? 'light' : 'dark';
            applyTheme(preference);
            try {
                window.localStorage.setItem(THEME_KEY, preference);
            } catch {
                // Giữ lựa chọn trong bộ nhớ khi trình duyệt không cho phép ghi localStorage.
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeToggle, { once: true });
    } else {
        initializeToggle();
    }

    /** Chế độ hệ thống chỉ có tác dụng khi người dùng chưa bấm chọn riêng. */
    if (systemTheme?.addEventListener) {
        systemTheme.addEventListener('change', () => {
            if (preference === null) applyTheme(preferredTheme());
        });
    }

    /** Các tab cùng cửa hàng dùng chung sở thích giao diện; không đọc hoặc sửa khóa phiên JWT. */
    window.addEventListener('storage', (event) => {
        if (event.key !== THEME_KEY && event.key !== null) return;
        preference = readPreference();
        applyTheme(preferredTheme());
    });
})();
