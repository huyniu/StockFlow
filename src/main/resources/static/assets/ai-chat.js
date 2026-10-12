export function initializeAiChat() {
    if (document.getElementById('ai-chat-root')) return;
    const toggleBtn = document.getElementById('ai-chat-toggle');
    const storefront = document.getElementById('storefront-view');
    if (!toggleBtn || !storefront) return;

    const root = document.createElement('div');
    root.id = 'ai-chat-root';
    root.innerHTML = `
        <div id="ai-chat-modal" class="ai-chat-modal" role="dialog" aria-labelledby="ai-chat-title" hidden>
            <div class="ai-header">
                <div class="ai-header-info">
                    <div class="ai-avatar" aria-hidden="true">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="3" y="11" width="18" height="10" rx="2" />
                            <circle cx="12" cy="5" r="2" /><path d="M12 7v4M8 16h.01M16 16h.01" />
                        </svg>
                    </div>
                    <div>
                        <div id="ai-chat-title" class="ai-title">Trợ lý AI StockFlow</div>
                        <div class="ai-subtitle">Tư vấn sản phẩm và mua hàng</div>
                    </div>
                </div>
                <button id="ai-close-btn" class="ai-close-btn" type="button" aria-label="Đóng trò chuyện">×</button>
            </div>
            <div id="ai-messages" class="ai-messages" role="log" aria-live="polite" aria-label="Nội dung trò chuyện">
                <div class="ai-msg bot">Xin chào! Mình là trợ lý AI của StockFlow Tech. Bạn muốn tìm sản phẩm hoặc cần hỗ trợ mua hàng?</div>
            </div>
            <div class="ai-chips" aria-label="Câu hỏi gợi ý">
                <button class="ai-chip" type="button" data-q="Tư vấn iPhone tầm giá tốt">Tư vấn iPhone</button>
                <button class="ai-chip" type="button" data-q="Có laptop nào cho sinh viên tầm 15–20 triệu không?">Laptop sinh viên</button>
                <button class="ai-chip" type="button" data-q="Chính sách bảo hành và đổi trả của shop thế nào?">Bảo hành và đổi trả</button>
                <button class="ai-chip" type="button" data-q="Thời gian giao hàng qua GHN mất bao lâu?">Thời gian giao hàng</button>
            </div>
            <div class="ai-input-box">
                <input id="ai-input" class="ai-input" type="text" aria-label="Câu hỏi cho trợ lý AI" placeholder="Nhập câu hỏi của bạn..." />
                <button id="ai-send-btn" class="ai-send-btn" type="button" aria-label="Gửi câu hỏi">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                        <path d="m22 2-7 20-4-9-9-4 20-7ZM22 2 11 13" />
                    </svg>
                </button>
            </div>
        </div>
    `;
    storefront.appendChild(root);

    const modal = root.querySelector('#ai-chat-modal');
    const input = root.querySelector('#ai-input');
    const messages = root.querySelector('#ai-messages');

    function setOpen(open) {
        modal.hidden = !open;
        modal.classList.toggle('active', open);
        toggleBtn.setAttribute('aria-expanded', String(open));
        if (open) input.focus({ preventScroll: true });
        else document.getElementById('contact-toggle')?.focus({ preventScroll: true });
    }
    toggleBtn.addEventListener('click', () => setOpen(modal.hidden));
    root.querySelector('#ai-close-btn').addEventListener('click', () => setOpen(false));
    modal.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            setOpen(false);
        }
    });
    root.querySelectorAll('.ai-chip').forEach(chip => {
        chip.addEventListener('click', () => sendMessage(chip.dataset.q));
    });

    function appendMessage(text, sender) {
        const msg = document.createElement('div');
        msg.className = 'ai-msg ' + sender;
        msg.textContent = text;
        messages.appendChild(msg);
        messages.scrollTop = messages.scrollHeight;
    }

    async function sendMessage(text) {
        const msgText = text || input.value.trim();
        if (!msgText) return;
        appendMessage(msgText, 'user');
        input.value = '';
        const typing = document.createElement('div');
        typing.className = 'ai-msg bot ai-typing';
        typing.setAttribute('aria-label', 'Trợ lý đang trả lời');
        typing.innerHTML = '<span></span><span></span><span></span>';
        messages.appendChild(typing);
        messages.scrollTop = messages.scrollHeight;

        try {
            const res = await fetch('/api/v1/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: msgText }),
            });
            typing.remove();
            if (res.ok) {
                const data = await res.json();
                appendMessage(data.reply || 'Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi lại nhé.', 'bot');
            } else {
                appendMessage('Trợ lý AI đang bận. Bạn thử lại sau giây lát nhé!', 'bot');
            }
        } catch {
            typing.remove();
            appendMessage('Chưa kết nối được với trợ lý AI. Bạn kiểm tra mạng và thử lại nhé.', 'bot');
        }
    }
    root.querySelector('#ai-send-btn').addEventListener('click', () => sendMessage());
    input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && !event.isComposing) {
            event.preventDefault();
            sendMessage();
        }
    });
}
