import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = path.resolve(fileURLToPath(new URL('../dist/', import.meta.url)));
const port = Number(process.env.PORT || 5173);
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.webmanifest':'application/manifest+json'};
// Exercise the same security headers locally as on Netlify.
const netlify = await readFile(new URL('../../netlify.toml', import.meta.url), 'utf8');
const securityHeaders = Object.fromEntries([...netlify.matchAll(/^\s+(X-Content-Type-Options|Referrer-Policy|X-Frame-Options|Permissions-Policy|Content-Security-Policy) = "([^"]+)"$/gm)].map(m => [m[1],m[2]]));
createServer(async (req,res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url,'http://localhost').pathname);
    const file = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(root + path.sep) || !(await stat(file)).isFile()) throw new Error('Not found');
    res.writeHead(200, {'Content-Type':types[path.extname(file)] || 'application/octet-stream','Cache-Control':'no-cache',...securityHeaders});
    res.end(await readFile(file));
  } catch { res.writeHead(404); res.end('Not found'); }
}).listen(port, '0.0.0.0', () => console.log(`HerMorse server listening on port ${port}`));
