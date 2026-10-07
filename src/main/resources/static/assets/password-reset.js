/* Email recovery is isolated from registration OTP and never stores codes or passwords. */
(() => {
    'use strict';
    window.StockFlowPasswordReset = {
        initialize({ api, notify, setAuthMode }) {
            const dialog = document.querySelector('#password-reset-dialog');
            const form = document.querySelector('#password-reset-form');
            const email = form.elements.email;
            const details = document.querySelector('#password-reset-details');
            const error = document.querySelector('#password-reset-error');
            const info = document.querySelector('#password-reset-info');
            const send = document.querySelector('#password-reset-send');
            const submit = document.querySelector('#password-reset-submit');
            let timer, nextSend = 0, busy = false, version = 0;
            function tick() {
                const seconds = Math.max(0, Math.ceil((nextSend - Date.now()) / 1000));
                send.disabled = busy || seconds > 0;
                send.textContent = seconds ? `Gửi lại mã (${seconds}s)` : details.hidden ? 'Gửi mã qua email' : 'Gửi lại mã';
                if (!seconds) { clearInterval(timer); timer = null; }
            }
            function clean() {
                version++; clearInterval(timer); timer = null; nextSend = 0;
                form.reset(); details.hidden = true; email.readOnly = false;
                for (const input of details.querySelectorAll('input')) input.required = false;
                submit.hidden = true; error.hidden = true; info.hidden = true; tick();
            }
            dialog.addEventListener('close', clean);
            document.querySelector('#password-reset-close').addEventListener('click', () => dialog.close());
            document.querySelector('#auth-forgot-password').addEventListener('click', () => {
                clean(); email.value = document.querySelector('#auth-email').value.trim();
                dialog.showModal(); email.focus();
            });
            send.addEventListener('click', async () => {
                if (busy || Date.now() < nextSend || !email.reportValidity()) return;
                const current = version;
                busy = true; error.hidden = true; tick();
                try {
                    const result = await api('/auth/forgot-password', { method: 'POST', anonymous: true, body: { email: email.value.trim() } });
                    if (current !== version) return;
                    info.textContent = result.message; info.hidden = false;
                    details.hidden = false; email.readOnly = true; submit.hidden = false;
                    for (const input of details.querySelectorAll('input')) input.required = true;
                    nextSend = Date.now() + 60000;
                    timer = setInterval(tick, 1000);
                    form.elements.otp.focus();
                } catch (e) { if (current === version) { error.textContent = e.message || 'Không gửi được mã. Vui lòng thử lại.'; error.hidden = false; } }
                finally { busy = false; tick(); }
            });
            form.addEventListener('submit', async event => {
                event.preventDefault(); event.stopPropagation();
                if (busy || details.hidden || !form.reportValidity()) return;
                error.hidden = true;
                if (form.elements.new_password.value !== form.elements.confirm_password.value) {
                    error.textContent = 'Hai mật khẩu chưa giống nhau.'; error.hidden = false; return;
                }
                if (new TextEncoder().encode(form.elements.new_password.value).length > 72) {
                    error.textContent = 'Mật khẩu quá dài; vui lòng dùng tối đa 72 byte.'; error.hidden = false; return;
                }
                const current = version, address = email.value.trim();
                busy = true; submit.disabled = true; tick();
                try {
                    const result = await api('/auth/reset-password', { method: 'POST', anonymous: true,
                        body: { email: address, otp: form.elements.otp.value.trim(), new_password: form.elements.new_password.value } });
                    if (current !== version) return;
                    dialog.close(); setAuthMode('login');
                    document.querySelector('#auth-email').value = address;
                    document.querySelector('#auth-password').value = '';
                    document.querySelector('#auth-password').focus();
                    notify('success', result.message, '', '', { banner: false });
                } catch (e) { if (current === version) { error.textContent = e.message || 'Chưa đổi được mật khẩu.'; error.hidden = false; } }
                finally { busy = false; submit.disabled = false; tick(); }
            });
        },
    };
})();
