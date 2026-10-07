/* CSS-only assembly. JavaScript uses native ES modules directly. */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'frontend/manifest.json'), 'utf8'));
let stale = false;
for (const [output, fragments] of Object.entries(manifest)) {
    const assembled = fragments.map(file => fs.readFileSync(path.join(root, 'frontend', file), 'utf8').replace(/\r\n/g, '\n')).join('');
    const destination = path.join(root, 'src/main/resources/static', output);
    if (process.argv.includes('--check')) {
        if (fs.readFileSync(destination, 'utf8').replace(/\r\n/g, '\n') !== assembled) {
            console.error(`${output} is stale. Run node scripts/build-storefront.cjs`); stale = true;
        }
    } else fs.writeFileSync(destination, assembled, 'utf8');
}
if (stale) process.exitCode = 1;
else console.log(process.argv.includes('--check') ? 'PASS: storefront bundles match source fragments' : 'Built styles.css');
