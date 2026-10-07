import { app } from './context.js';

const storageKey = 'stockflow.recently-viewed';
let ids = [];
let revision = 0;
const products = new Map();

function persist() {
    try { localStorage.setItem(storageKey, JSON.stringify(ids)); } catch { /* Browsing still works without storage. */ }
}

function render() {
    const section = document.querySelector('#recently-viewed');
    const visible = ids.map(id => products.get(id)).filter(product => product?.status === 'ACTIVE');
    section.hidden = !visible.length;
    document.querySelector('#recently-viewed-products').innerHTML = visible.map(product => `
        <a class="recent-product" href="${app.productPagePath(product.id)}" data-product-link data-product-id="${product.id}">
            ${app.productArt(product)}
            <span class="recent-product-name">${app.escapeHtml(product.name)}</span>
            <strong>${app.amount(product.min_price ?? product.unit_price)}</strong>
        </a>`).join('');
}

export function rememberProduct(product) {
    if (product.status !== 'ACTIVE') return;
    revision++;
    products.set(product.id, product);
    ids = [product.id, ...ids.filter(id => id !== product.id)].slice(0, 30);
    persist();
    render();
}

export function initializeRecentlyViewed() {
    try {
        const saved = JSON.parse(localStorage.getItem(storageKey) || '[]');
        ids = Array.isArray(saved) ? [...new Set(saved.filter(id => Number.isSafeInteger(id) && id > 0))].slice(0, 30) : [];
    } catch { ids = []; }
    document.querySelector('#clear-recently-viewed').addEventListener('click', () => {
        revision++;
        ids = [];
        products.clear();
        persist();
        render();
    });
    const initialRevision = revision;
    Promise.allSettled(ids.map(id => app.api('/products/' + id, { anonymous: true }))).then(results => {
        if (initialRevision !== revision) return;
        results.forEach((result, index) => {
            if (result.status === 'fulfilled' && !result.value.parent_product_id) products.set(ids[index], result.value);
        });
        render();
    });
}
