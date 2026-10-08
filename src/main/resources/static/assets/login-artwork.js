/* Adapted from the user's Stitch ANIMATION_21. Three.js r125 is vendored with its MIT license.
   The existing CSS cube remains a fallback when WebGL/library loading is unavailable. */
export function initializeLoginArtwork() {
    'use strict';
    const container = document.getElementById('auth-webgl-container');
    if (!container) return;
    const panel = container.closest('.auth-page-art');
    const desktop = window.matchMedia('(min-width: 801px)');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    const finePointer = window.matchMedia('(pointer: fine)');
    let active = false, inViewport = true, focused = true, loading = false;
    let libraryPromise, rig = null, frame = null, lastTimestamp = null;
    let mouseX = 0, mouseY = 0, targetX = 0, targetY = 0;

    function loadThree() {
        if (window.THREE) return Promise.resolve(window.THREE);
        if (!libraryPromise) libraryPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            const fail = () => { window.clearTimeout(timer); script.remove(); libraryPromise = null; reject(new Error('Three.js unavailable')); };
            const timer = window.setTimeout(fail, 10000);
            script.src = '/assets/vendor/three-r125.min.js';
            script.async = true;
            script.onerror = fail;
            script.onload = () => {
                window.clearTimeout(timer);
                if (window.THREE) resolve(window.THREE);
                else fail();
            };
            document.head.append(script);
        });
        return libraryPromise;
    }
    function canDisplay() {
        return active && desktop.matches && !document.hidden && focused && inViewport && container.clientWidth > 0 && container.clientHeight > 0;
    }
    function stop() {
        if (frame !== null) window.cancelAnimationFrame(frame);
        frame = null;
        lastTimestamp = null;
    }
    function dispose() {
        stop();
        const old = rig;
        rig = null;
        panel.classList.remove('webgl-ready');
        mouseX = mouseY = targetX = targetY = 0;
        if (!old) return;
        old.geometries.forEach(value => value.dispose());
        old.materials.forEach(value => value.dispose());
        old.renderer.dispose();
        old.renderer.forceContextLoss();
        old.renderer.domElement.remove();
    }
    function build(THREE) {
        const geometries = new Set(), materials = new Set();
        const geo = value => { geometries.add(value); return value; };
        const mat = value => { materials.add(value); return value; };
        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, .1, 1000);
        camera.position.set(0, 0, 6);
        const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' });
        renderer.setClearColor(0x000000, 0);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
        renderer.setSize(container.clientWidth, container.clientHeight);
        renderer.domElement.setAttribute('aria-hidden', 'true');
        container.append(renderer.domElement);
        scene.add(new THREE.AmbientLight(0x0a192f, 2.5));
        const blue = new THREE.PointLight(0x2563eb, 5, 25);
        blue.position.set(4, 5, 5); scene.add(blue);
        const cyan = new THREE.PointLight(0x38bdf8, 4, 25);
        cyan.position.set(-4, -4, 4); scene.add(cyan);
        const master = new THREE.Group(); scene.add(master);
        const box = geo(new THREE.BoxGeometry(2.1, 2.1, 2.1));
        const cube = new THREE.LineSegments(geo(new THREE.EdgesGeometry(box)), mat(new THREE.LineBasicMaterial({ color: 0x38bdf8, transparent: true, opacity: .85 })));
        master.add(cube);
        // Nodes belong to the wireframe cube so they stay at its corners while it rotates.
        const cornerGeo = geo(new THREE.BoxGeometry(.12, .12, .12));
        const cornerMat = mat(new THREE.MeshBasicMaterial({ color: 0x60a5fa }));
        for (const x of [-1.05,1.05]) for (const y of [-1.05,1.05]) for (const z of [-1.05,1.05]) {
            const corner = new THREE.Mesh(cornerGeo, cornerMat);
            corner.position.set(x,y,z); cube.add(corner);
        }
        const ring1 = new THREE.Mesh(geo(new THREE.TorusGeometry(1.8,.035,16,80)), mat(new THREE.MeshStandardMaterial({ color: 0x1d4ed8, metalness: .85, roughness: .15, emissive: 0x1e40af, emissiveIntensity: .7 })));
        const ring2 = new THREE.Mesh(geo(new THREE.TorusGeometry(1.5,.03,16,80)), mat(new THREE.MeshStandardMaterial({ color: 0x0ea5e9, metalness: .8, roughness: .2, emissive: 0x0284c7, emissiveIntensity: .6 })));
        ring2.rotation.x = Math.PI / 3; master.add(ring1,ring2);
        const coreGeo = geo(new THREE.OctahedronGeometry(.75,0));
        const core = new THREE.Mesh(coreGeo, mat(new THREE.MeshPhongMaterial({ color: 0x60a5fa, emissive: 0x2563eb, emissiveIntensity: .85, shininess: 100 })));
        const coreWire = new THREE.Mesh(coreGeo, mat(new THREE.MeshBasicMaterial({ color: 0xbae6fd, wireframe: true, transparent: true, opacity: .9 })));
        coreWire.scale.set(1.05,1.05,1.05); master.add(core,coreWire);
        const matrixPos = new Float32Array(180 * 3);
        for (let i = 0; i < 180; i++) {
            const angle = i / 180 * Math.PI * 2;
            const radius = 2.4 + (Math.random() - .5) * .4;
            matrixPos[i * 3] = Math.cos(angle) * radius;
            matrixPos[i * 3 + 1] = (Math.random() - .5) * .3;
            matrixPos[i * 3 + 2] = Math.sin(angle) * radius;
        }
        const matrixGeo = geo(new THREE.BufferGeometry()); matrixGeo.setAttribute('position',new THREE.BufferAttribute(matrixPos,3));
        const matrix = new THREE.Points(matrixGeo, mat(new THREE.PointsMaterial({ color: 0x38bdf8, size: .055, transparent: true, opacity: .85 })));
        master.add(matrix);
        const starPos = new Float32Array(150 * 3);
        for (let i = 0; i < starPos.length; i += 3) {
            starPos[i] = (Math.random() - .5) * 10;
            starPos[i + 1] = (Math.random() - .5) * 10;
            starPos[i + 2] = (Math.random() - .5) * 8;
        }
        const starGeo = geo(new THREE.BufferGeometry()); starGeo.setAttribute('position',new THREE.BufferAttribute(starPos,3));
        const stars = new THREE.Points(starGeo, mat(new THREE.PointsMaterial({ color: 0x93c5fd, size: .04, transparent: true, opacity: .6 })));
        scene.add(stars);
        const result = { scene,camera,renderer,master,cube,ring1,ring2,core,coreWire,matrix,stars,geometries,materials,time: 1.3 };
        renderer.domElement.addEventListener('webglcontextlost', event => {
            event.preventDefault();
            if (rig !== result) return;
            stop(); panel.classList.remove('webgl-ready');
        });
        renderer.domElement.addEventListener('webglcontextrestored', () => { if (rig === result) { panel.classList.add('webgl-ready'); sync(); } });
        return result;
    }
    function render() {
        if (!rig) return;
        const {master,cube,ring1,ring2,core,coreWire,matrix,stars,time} = rig;
        targetX += (mouseX - targetX) * .08;
        targetY += (mouseY - targetY) * .08;
        master.rotation.y = time * .35 + targetX * .7;
        master.rotation.x = Math.sin(time * .2) * .2 + targetY * .4;
        cube.rotation.x = time * .25; cube.rotation.z = time * .15;
        ring1.rotation.x = time * .6; ring1.rotation.y = Math.cos(time * .4) * .5;
        ring2.rotation.y = -time * .7; ring2.rotation.z = Math.sin(time * .5) * .5;
        matrix.rotation.y = -time * .4; matrix.rotation.x = Math.sin(time * .3) * .15;
        core.rotation.x = -time * .9; core.rotation.y = time * .9;
        coreWire.rotation.copy(core.rotation);
        const pulse = 1 + Math.sin(time * 3) * .09;
        core.scale.set(pulse,pulse,pulse); coreWire.scale.set(pulse * 1.05,pulse * 1.05,pulse * 1.05);
        stars.rotation.y = -time * .05;
        rig.renderer.render(rig.scene,rig.camera);
    }
    function animate(timestamp) {
        frame = null;
        if (!rig || !canDisplay() || reduced.matches || rig.renderer.getContext().isContextLost()) { stop(); return; }
        if (lastTimestamp === null) lastTimestamp = timestamp;
        if (timestamp - lastTimestamp >= 1000 / 30) {
            rig.time += Math.min((timestamp - lastTimestamp) / 1000,.1);
            lastTimestamp = timestamp;
            render();
        }
        frame = window.requestAnimationFrame(animate);
    }
    async function sync() {
        if (!active || !desktop.matches) { dispose(); return; }
        if (!canDisplay()) { stop(); return; }
        if (!rig && !loading) {
            loading = true;
            try {
                const THREE = await loadThree();
                if (!canDisplay()) return;
                rig = build(THREE);
                panel.classList.add('webgl-ready');
            } catch {
                dispose(); // Illustration failure never blocks the form or Google login.
                return;
            } finally { loading = false; }
        }
        if (!rig || !canDisplay()) return;
        if (rig.renderer.getContext().isContextLost()) { stop(); return; }
        const w = container.clientWidth, h = container.clientHeight;
        rig.camera.aspect = w / h; rig.camera.updateProjectionMatrix();
        rig.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1,2));
        rig.renderer.setSize(w,h);
        if (reduced.matches) { stop(); mouseX = mouseY = targetX = targetY = 0; render(); }
        else if (frame === null) { render(); frame = window.requestAnimationFrame(animate); }
    }
    document.addEventListener('pointermove', event => {
        if (!rig || !canDisplay() || reduced.matches || !finePointer.matches || event.pointerType === 'touch') return;
        const rect = container.getBoundingClientRect();
        mouseX = Math.max(-1,Math.min(1,((event.clientX - rect.left) / Math.max(rect.width,1) - .5) * 2));
        mouseY = Math.max(-1,Math.min(1,((event.clientY - rect.top) / Math.max(rect.height,1) - .5) * 2));
    }, { passive: true });
    document.documentElement.addEventListener('pointerleave', () => { mouseX = mouseY = 0; });
    document.addEventListener('visibilitychange', sync);
    window.addEventListener('blur', () => { focused = false; stop(); mouseX = mouseY = 0; });
    window.addEventListener('focus', () => { focused = true; void sync(); });
    desktop.addEventListener('change', sync);
    reduced.addEventListener('change', sync);
    if ('ResizeObserver' in window) new ResizeObserver(() => { void sync(); }).observe(container);
    else window.addEventListener('resize', sync, { passive: true });
    if ('IntersectionObserver' in window) new IntersectionObserver(entries => {
        inViewport = entries.some(entry => entry.isIntersecting); void sync();
    }).observe(panel);
    window.addEventListener('pagehide', dispose);
    window.addEventListener('pageshow', () => { void sync(); });
    window.StockFlowAuthArtwork = { setActive(value) { active = Boolean(value); void sync(); } };
}
