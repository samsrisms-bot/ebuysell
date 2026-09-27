// Copies the static dashboard assets into dist/ after tsc compiles src/ -> dist/,
// since tsc only touches .ts files.
const fs = require('fs');
const path = require('path');

const src = path.join(__dirname, '..', 'src', 'public');
const dest = path.join(__dirname, '..', 'dist', 'public');

fs.rmSync(dest, { recursive: true, force: true });
fs.cpSync(src, dest, { recursive: true });
console.log(`copied ${src} -> ${dest}`);
