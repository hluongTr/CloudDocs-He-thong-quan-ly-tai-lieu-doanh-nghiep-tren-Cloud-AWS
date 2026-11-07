const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function screen() {
  const nodes = new Map();
  const document = {
    getElementById(id) {
      if (!nodes.has(id))
        nodes.set(id, {
          textContent: "",
          innerHTML: "",
          style: {},
          hidden: false,
          replaceChildren() {
            this.innerHTML = "";
          },
          classList: {
            add() { this.hidden = true; },
            toggle(name, hidden) {
              this.hidden = hidden;
            },
          },
          addEventListener() {},
        });
      return nodes.get(id);
    },
  };
  const context = vm.createContext({
    document,
    currentSettings: { language: "vi" },
    currentUser: { role: "ADMIN" },
    storageQuota: null,
    escapeHtml: (value) => String(value).replaceAll("<", "&lt;").replaceAll(">", "&gt;"),
  });
  const source = fs.readFileSync(path.join(__dirname, "../js/app.js"), "utf8");
  vm.runInContext(
    source.slice(
      source.indexOf("function formatStorageBytes("),
      source.indexOf("function renderDashboardActivity("),
    ),
    context,
  );
  vm.runInContext(source.slice(source.indexOf("function renderStorageBreakdown("), source.indexOf("async function loadDepartments(")), context);
  return { context, document };
}


test("personal quota displays decimal GB and TB consistently", () => {
  const { context } = screen();
  assert.equal(context.formatStorageBytes(100_000_000_000), "100 GB");
  assert.equal(context.formatStorageBytes(1_000_000_000_000), "1 TB");
});
test("sidebar displays total allocated bytes / quota without percent or remaining labels", () => {
  const { context, document } = screen();
  context.renderStorageQuota({ usedBytes: 1200, confirmedBytes: 200, limitBytes: 1000 });
  assert.equal(document.getElementById("storageUsage").textContent, "1.2 KB / 1 KB");
  assert.equal(document.getElementById("statStorage").textContent, "1.2 KB");
  assert.equal(document.getElementById("storageProgress").style.width, "100%");
  context.renderStorageQuota({usedBytes:250,limitBytes:1000});
  assert.equal(document.getElementById("storageProgress").style.width, "25%");
  context.renderStorageQuota(null);
  assert.equal(document.getElementById("storageProgress").style.width, "0%");
});
test("admin table shows per-account limits and escapes account names", () => {
  const { context, document } = screen();
  context.renderAdminStorage({isAdmin:true,systemStorageBytes:300,storageUsers:[{username:"<script>",role:"USER",usedBytes:300,limitBytes:100_000_000_000,remainingBytes:99_999_999_700}]});
  const html=document.getElementById("adminStorageRows").innerHTML;
  assert.ok(html.includes("100 GB"));assert.ok(html.includes("&lt;script&gt;"));assert.ok(!html.includes("<script>"));
});
test("switching to a regular user clears and hides admin storage details", () => {
  const { context, document } = screen();
  document.getElementById("adminStorageRows").innerHTML = "private admin data";
  context.currentUser = { role: "USER" };context.renderAdminStorage({ isAdmin: true });
  assert.equal(document.getElementById("adminStorageRows").innerHTML, "");
  assert.equal(document.getElementById("adminStoragePanel").classList.hidden, true);
});
test("admin distribution includes all supplied formats and is independent of personal quota", () => {
  const {context,document}=screen();
  context.renderStorageDistribution({scope:"SYSTEM",usedBytes:1250000,storageByType:{PDF:1000000,ZIP:250000}});
  context.renderStorageQuota({usedBytes:3300,limitBytes:1_000_000_000_000});
  assert.equal(document.getElementById("storageUsage").textContent,"3.3 KB / 1 TB");
  assert.equal(document.getElementById("donutStorage").textContent,"1.25 MB");
  assert.ok(document.getElementById("storageScope").textContent.includes("hệ thống"));
  assert.ok(document.getElementById("storageDonut").style.background.includes("80%"));
  assert.ok(document.getElementById("storageBreakdown").innerHTML.includes("ZIP"));
});
test("unknown sizes are shown as a lower bound and explained in chart",()=>{
  const {context,document}=screen();
  context.renderStorageQuota({usedBytes:0,limitBytes:1000,missingSizeCount:2});
  context.renderStorageDistribution({scope:"PERSONAL",usedBytes:0,missingSizeCount:2,storageByType:{}});
  assert.equal(document.getElementById("storageUsage").textContent,"≥ 0 B / 1 KB");
  assert.equal(document.getElementById("donutStorage").textContent,"≥ 0 B");
  assert.ok(document.getElementById("storageAccountingNote").textContent.includes("2 tài liệu"));
});
test("quota failure clears stale quota while preserving independently loaded distribution",async()=>{
  const {context,document}=screen();
  context.renderStorageDistribution({scope:"SYSTEM",usedBytes:200,storageByType:{PDF:200}});
  context.renderStorageQuota({usedBytes:200,limitBytes:1000});context.renderStorageQuota(null);
  assert.equal(document.getElementById("statStorage").textContent,"-");
  context.getToken=()=>"session";context.api=async()=>{throw Error("offline")};context.console={error(){}};
  await context.loadStorageUsage();
  assert.equal(context.storageQuota,null);
  assert.equal(document.getElementById("donutStorage").textContent,"200 B");
});
test("missing distribution clears stale chart and account scope",()=>{
  const {context,document}=screen();
  context.renderStorageDistribution({scope:"SYSTEM",usedBytes:200,storageByType:{PDF:200}});
  context.renderStorageDistribution(null);
  assert.equal(document.getElementById("donutStorage").textContent,"-");
  assert.equal(document.getElementById("storageScope").textContent,"");
  assert.equal(document.getElementById("storageDonut").style.background,"#dce1e9");
});
test("all formats remain in legend even with more than six formats",()=>{
  const {context,document}=screen();context.renderStorageBreakdown({A:100,B:90,C:80,D:70,E:60,F:50,G:40});
  const html=document.getElementById("storageBreakdown").innerHTML;
  assert.ok(html.includes("40 B"));assert.equal((html.match(/<div>/g)||[]).length,7);
});
test("zero byte formats remain visible without creating invalid donut segments",()=>{
  const {context,document}=screen();context.renderStorageDistribution({scope:"PERSONAL",usedBytes:0,zeroSizeCount:1,storageByType:{TXT:0}});
  assert.ok(document.getElementById("storageBreakdown").innerHTML.includes("TXT"));
  assert.ok(document.getElementById("storageBreakdown").innerHTML.includes("0 B"));
  assert.equal(document.getElementById("storageDonut").style.background,"#dce1e9");
  assert.ok(document.getElementById("storageAccountingNote").textContent.includes("0 B"));
});
test("pending uploads are included in allocated quota and metadata distribution",()=>{
  const {context,document}=screen();
  context.renderStorageQuota({usedBytes:3000,confirmedBytes:1000,unconfirmedBytes:2000,limitBytes:100000});
  context.renderStorageDistribution({scope:"PERSONAL",usedBytes:3000,storageByType:{PDF:1000,ZIP:2000}});
  assert.equal(document.getElementById("storageUsage").textContent,"3 KB / 100 KB");
  assert.equal(document.getElementById("donutStorage").textContent,"3 KB");
  assert.ok(document.getElementById("storageBreakdown").innerHTML.includes("ZIP"));
});

test('admin dashboard sidebar uses all accounts instead of the admin personal allocation',()=>{const {context,document}=screen();context.renderStorageQuota({usedBytes:12,limitBytes:10000});context.renderAdminStorage({isAdmin:true,systemStorageBytes:11012,systemMissingSizeCount:0,storageUsers:[{displayName:'Admin',role:'ADMIN',usedBytes:12,limitBytes:10000,remainingBytes:9988},{displayName:'User',role:'USER',usedBytes:11000,limitBytes:1000,remainingBytes:0}]});assert.equal(document.getElementById('storageUsage').textContent,'11.01 KB / 10 KB');assert.equal(document.getElementById('statStorage').textContent,'11.01 KB');});
