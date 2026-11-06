const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../FrontEnd');
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.gif':'image/gif','.svg':'image/svg+xml','.webp':'image/webp','.ico':'image/x-icon','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
function configuration() {
  const context = {window:{}};
  vm.runInNewContext(fs.readFileSync(path.join(root,'js/cognito-config.js'),'utf8'),context,{timeout:1000});
  const config = context.window.COGNITO_CONFIG;
  const url = new URL(config.redirectUri);
  if (url.protocol !== 'http:' || !['localhost','127.0.0.1'].includes(url.hostname) || url.username || url.password || url.search || url.hash)
    throw Error('Local frontend requires an http://localhost or http://127.0.0.1 callback.');
  if (new URL(config.logoutUri).origin !== url.origin) throw Error('Login and logout origins must match.');
  return url;
}
function publicFile(target, mount = '/', directory = root) {
  let pathname = decodeURIComponent(target.split('?')[0]);
  if (pathname.includes(String.fromCharCode(92)) || pathname.includes(String.fromCharCode(0)) || pathname.split('/').some(part=>part==='..' || part.startsWith('.'))) return null;
  if (mount !== '/') {
    if (!pathname.startsWith(mount + '/')) return null;
    pathname = pathname.slice(mount.length);
  }
  if (pathname.endsWith('/')) pathname += 'index.html';
  const file = path.resolve(directory, '.' + pathname);
  if (!file.startsWith(path.resolve(directory) + path.sep) || !types[path.extname(file).toLowerCase()]) return null;
  return file;
}
function createServer(url = configuration()) {
  const mount = path.posix.dirname(url.pathname);
  return http.createServer(async (request,response) => {
    response.setHeader('Cache-Control','no-store');
    response.setHeader('X-Content-Type-Options','nosniff');
    if (!['GET','HEAD'].includes(request.method)) { response.writeHead(405,{Allow:'GET, HEAD'});return response.end(); }
    if (request.url === '/' && mount !== '/') { response.writeHead(302,{Location:url.pathname});return response.end(); }
    let file;
    try { file = publicFile(request.url,mount); } catch { response.writeHead(400);return response.end('Bad path'); }
    if (!file) { response.writeHead(404);return response.end('Not found'); }
    try {
      const [realRoot,realFile] = await Promise.all([fs.promises.realpath(root),fs.promises.realpath(file)]);
      if (!realFile.startsWith(realRoot + path.sep)) throw Error('Outside public folder');
      const stat = await fs.promises.stat(realFile);
      if (!stat.isFile()) throw Error('Not a file');
      const body = request.method === 'HEAD' ? null : await fs.promises.readFile(realFile);
      response.writeHead(200,{'Content-Type':types[path.extname(file).toLowerCase()],'Content-Length':stat.size});response.end(body);
    } catch { response.writeHead(404);response.end('Not found'); }
  });
}
if (require.main === module) {
  const url = configuration();
  const server = createServer(url);
  server.on('error',error=>{console.error(error.message);process.exitCode=1;});
  server.listen(Number(url.port || 80),url.hostname,()=>console.log('Open ' + url.href));
}
module.exports = {configuration,publicFile,createServer};
