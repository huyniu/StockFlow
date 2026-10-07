export async function loadFragments() {
    const placeholders = [...document.querySelectorAll('template[data-fragment]')];
    const contents = await Promise.all(placeholders.map(async placeholder => {
        const response = await fetch(placeholder.dataset.fragment, { credentials: 'omit', signal: AbortSignal.timeout(15000) });
        if (!response.ok) throw new Error('Không tải được giao diện.');
        return response.text();
    }));
    placeholders.forEach((placeholder, index) => {
        const fragment = document.createElement('template');
        fragment.innerHTML = contents[index];
        placeholder.replaceWith(fragment.content);
    });
    window.StockFlowTheme?.refresh();
    document.querySelector('#boot-status')?.remove();
}
