const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function screen() {
 const nodes = new Map();
 const panel = { after(node) { nodes.set(node.id, node); } };
 const document = {
  getElementById: id => nodes.get(id) || null,
  createElement: () => ({ innerHTML: '', hidden: false, setAttribute() {}, replaceChildren() { this.innerHTML = ''; } }),
 };
 let token = 'owner';
 const context = vm.createContext({ document, getToken: () => token, currentSettings: { language: 'vi' } });
 context.window = context;
 const source = fs.readFileSync(process.env.PAGINATION_SOURCE || path.join(__dirname, '../js/list-pagination.js'), 'utf8');
 vm.runInContext(source.slice(source.indexOf('const renderedTablePages =')), context);
 return {
  context,
  setToken: value => token = value,
  render(id, count) {
   const body = { children: Array.from({ length: Math.max(count, 1) }, () => ({ style: {} })), closest: () => panel };
   nodes.set(id, body);
   context.paginateRenderedTable(id, count);
   return body;
  },
  pager: id => nodes.get(id + 'Pager'),
  click(id, page) {
   this.pager(id).onclick({ target: { closest: () => ({ disabled: false, dataset: { tablePage: String(page) } }) } });
  },
 };
}
const visible = body => body.children.filter(row => row.style.display !== 'none').length;

test('users and audit paginate independently, twenty rows at a time', () => {
 const s = screen();
 const users = s.render('userTable', 45), logs = s.render('activityLogTable', 61);
 assert.equal(visible(users), 20);
 assert.equal(visible(logs), 20);
 s.click('userTable', 3);
 assert.equal(visible(users), 5);
 assert.equal(visible(logs), 20);
 assert.match(s.pager('userTable').innerHTML, /41–45/);
});

test('refresh rebinds pager to new rows and deletions clamp the last page', () => {
 const s = screen();
 s.render('userTable', 45); s.click('userTable', 3);
 const refreshed = s.render('userTable', 22);
 assert.equal(visible(refreshed), 2);
 assert.match(s.pager('userTable').innerHTML, /21–22/);
 s.click('userTable', 1);
 assert.equal(visible(refreshed), 20);
});

test('small and empty tables hide pager without hiding empty-state row', () => {
 const s = screen();
 s.render('userTable', 45);
 const empty = s.render('userTable', 0);
 assert.equal(s.pager('userTable').hidden, true);
 assert.equal(visible(empty), 1);
 assert.equal(visible(s.render('userTable', 20)), 20);
 assert.equal(s.pager('userTable').hidden, true);
});

test('new login resets page and invalid page numbers are ignored', () => {
 const s = screen();
 s.render('userTable', 45); s.click('userTable', 3);
 s.setToken('another-user');
 const body = s.render('userTable', 45);
 assert.match(s.pager('userTable').innerHTML, /1–20/);
 s.click('userTable', 0); s.click('userTable', 100);
 assert.equal(visible(body), 20);
 assert.match(s.pager('userTable').innerHTML, /1–20/);
});
