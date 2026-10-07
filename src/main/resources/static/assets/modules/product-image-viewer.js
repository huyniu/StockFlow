export function initializeProductImageViewer() {
    const dialog = document.createElement('dialog');
    dialog.id = 'product-image-viewer';
    dialog.setAttribute('aria-labelledby', 'product-image-viewer-title');
    dialog.innerHTML = `<div class="image-viewer-toolbar"><h2 id="product-image-viewer-title">Ảnh sản phẩm</h2><div><button type="button" data-image-zoom="out" aria-label="Thu nhỏ">−</button><button type="button" data-image-zoom="reset" aria-label="Đặt lại kích thước"><span id="image-viewer-scale">100%</span></button><button type="button" data-image-zoom="in" aria-label="Phóng to">+</button><button type="button" data-image-close aria-label="Đóng ảnh">✕</button></div></div><p class="image-viewer-hint">Lăn chuột hoặc dùng nút + / − để phóng to, thu nhỏ. Cuộn thanh kéo để xem vùng ảnh đã phóng to.</p><div class="image-viewer-stage"><img alt="" draggable="false" /></div>`;
    document.body.append(dialog);
    const stage = dialog.querySelector('.image-viewer-stage');
    const image = stage.querySelector('img');
    let zoom = 1;
    let previousOverflow = '';
    function resize(value) {
        zoom = Math.max(1, Math.min(4, value));
        image.style.width = `${zoom * 100}%`;
        image.style.height = `${zoom * 100}%`;
        dialog.querySelector('#image-viewer-scale').textContent = `${Math.round(zoom * 100)}%`;
        dialog.querySelector('[data-image-zoom="out"]').disabled = zoom === 1;
        dialog.querySelector('[data-image-zoom="in"]').disabled = zoom === 4;
        if (zoom === 1) { stage.scrollTop = 0; stage.scrollLeft = 0; }
    }
    document.addEventListener('click', event => {
        if (!event.target.closest('[data-open-product-image]')) return;
        const source = document.querySelector('#shop-product-main-image');
        if (!source || dialog.open) return;
        image.src = source.currentSrc || source.src;
        image.alt = source.alt;
        dialog.showModal();
        resize(1);
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
    });
    dialog.addEventListener('click', event => {
        if (event.target.closest('[data-image-close]')) dialog.close();
        const action = event.target.closest('[data-image-zoom]')?.dataset.imageZoom;
        if (action) resize(action === 'reset' ? 1 : zoom + (action === 'in' ? .5 : -.5));
        if (event.target === dialog) {
            const rect = dialog.getBoundingClientRect();
            if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
        }
    });
    stage.addEventListener('wheel', event => {
        if (event.ctrlKey || !event.deltaY) return;
        event.preventDefault();
        resize(zoom + (event.deltaY < 0 ? .25 : -.25));
    }, { passive: false });
    stage.addEventListener('dblclick', () => resize(zoom === 1 ? 2 : 1));
    dialog.addEventListener('close', () => {
        document.body.style.overflow = previousOverflow;
        image.removeAttribute('src');
    });
}
