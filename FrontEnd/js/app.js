const API_BASE = "http://localhost:8080/api";
let docs = [];
let selectedDocumentIds = new Set();
let selectedTrashIds = new Set();
let trashDocuments = [];
let bulkShareIds = [];
let bulkBusy = false;
let currentUser = null;
let favoriteIds = new Set();
let currentCollection = [];
let activePage = "dashboard";
let notifications = [];
let notificationTimer = null;
let storageQuota = null;
const DEFAULT_SETTINGS = {
  theme: "light",
  language: "vi",
  notifications: true,
  badge: true,
  autoRefresh: true,
};
let currentSettings = { ...DEFAULT_SETTINGS };

const pageNames = {
  tasks: ["Công việc", "CÔNG VIỆC", "Giao việc, nộp báo cáo và theo dõi hạn nộp."],
  trash: ["Thùng rác", "THÙNG RÁC", "Khôi phục hoặc xóa vĩnh viễn tài liệu đã xóa."],
  profile: ["Hồ sơ cá nhân", "TÀI KHOẢN", "Cập nhật thông tin cá nhân."],
  dashboard: ["Tổng quan", "TỔNG QUAN", "Tình hình tài liệu và hoạt động của doanh nghiệp."],
  documents: ["Tài liệu", "KHO TÀI LIỆU", "Quản lý toàn bộ tài liệu doanh nghiệp."],
  shared: ["Được chia sẻ", "CHIA SẺ", "Các tài liệu đang được chia sẻ với bạn."],
  recent: ["Gần đây", "GẦN ĐÂY", "Các tài liệu được cập nhật gần đây."],
  favorites: ["Yêu thích", "YÊU THÍCH", "Các tài liệu bạn đánh dấu yêu thích."],
  users: ["Người dùng", "QUẢN TRỊ NGƯỜI DÙNG", "Quản lý tài khoản và quyền truy cập."],
  activity: ["Nhật ký hoạt động", "AUDIT LOG", "Theo dõi hoạt động trong hệ thống."],
  settings: ["Cài đặt", "CÀI ĐẶT", "Cấu hình hệ thống quản lý tài liệu."],
};

const languageText = {
  vi: {
    nav: {
      tasks: "Công việc",
      trash: "Thùng rác",
      dashboard: "Tổng quan",
      documents: "Tài liệu",
      shared: "Được chia sẻ",
      recent: "Gần đây",
      favorites: "Yêu thích",
      users: "Người dùng",
      activity: "Nhật ký hoạt động",
      settings: "Cài đặt",
      profile: "Hồ sơ cá nhân",
    },
    pages: {
      dashboard: "Tổng quan",
      documents: "Tài liệu",
      shared: "Được chia sẻ",
      recent: "Gần đây",
      favorites: "Yêu thích",
      users: "Người dùng",
      activity: "Nhật ký hoạt động",
      settings: "Cài đặt",
      profile: "Hồ sơ cá nhân",
    },
    search: "Tìm kiếm tài liệu, người dùng...",
    admin: "Quản trị viên",
    user: "Nhân viên",
    meta: {
      profile: ["Hồ sơ cá nhân", "TÀI KHOẢN", "Cập nhật thông tin cá nhân."],
      dashboard: ["Tổng quan", "TỔNG QUAN", "Tình hình tài liệu và hoạt động của doanh nghiệp."],
      documents: ["Tài liệu", "KHO TÀI LIỆU", "Quản lý toàn bộ tài liệu doanh nghiệp."],
      shared: ["Được chia sẻ", "CHIA SẺ", "Các tài liệu đang được chia sẻ với bạn."],
      recent: ["Gần đây", "GẦN ĐÂY", "Các tài liệu bạn vừa tương tác."],
      favorites: ["Yêu thích", "YÊU THÍCH", "Các tài liệu bạn đánh dấu yêu thích."],
      users: ["Người dùng", "QUẢN TRỊ NGƯỜI DÙNG", "Quản lý tài khoản và quyền truy cập."],
      activity: ["Nhật ký hoạt động", "AUDIT LOG", "Theo dõi hoạt động trong hệ thống."],
      settings: ["Cài đặt", "CÀI ĐẶT", "Cá nhân hóa trải nghiệm của bạn."],
    },
    settings: [
      "TÙY CHỈNH",
      "Cài đặt",
      "Cá nhân hóa giao diện và trải nghiệm của bạn.",
      "Giao diện",
      "Chọn giao diện sáng hoặc tối.",
      "Sáng",
      "Tối",
      "Thông báo cá nhân",
      "Kiểm soát cách thông báo xuất hiện trên tài khoản này.",
      "Nhận thông báo",
      "Hiển thị thông báo chung từ ADMIN.",
      "Dấu thông báo mới",
      "Hiển thị chấm đỏ khi có thông báo chưa xem.",
      "Tự động cập nhật",
      "Kiểm tra thông báo mới sau mỗi 30 giây.",
      "Ngôn ngữ",
      "Chọn ngôn ngữ hiển thị chính.",
      "Ngôn ngữ giao diện",
      "✓ Đã lưu cài đặt",
    ],
  },
  en: {
    nav: {
      tasks: "Tasks",
      trash: "Trash",
      dashboard: "Dashboard",
      documents: "Documents",
      shared: "Shared with me",
      recent: "Recent",
      favorites: "Favorites",
      users: "Users",
      activity: "Activity log",
      settings: "Settings",
      profile: "My profile",
    },
    pages: {
      dashboard: "Dashboard",
      documents: "Documents",
      shared: "Shared with me",
      recent: "Recent",
      favorites: "Favorites",
      users: "Users",
      activity: "Activity log",
      settings: "Settings",
      profile: "My profile",
    },
    search: "Search documents, users...",
    admin: "Administrator",
    user: "Employee",
    meta: {
      profile: ["My profile", "ACCOUNT", "Update your personal details."],
      dashboard: ["Dashboard", "OVERVIEW", "Your organization's documents and activity."],
      documents: ["Documents", "DOCUMENT LIBRARY", "Manage and access organization documents."],
      shared: ["Shared with me", "SHARING", "Documents other people shared with you."],
      recent: ["Recent", "RECENT", "Documents you recently interacted with."],
      favorites: ["Favorites", "FAVORITES", "Documents you marked as favorites."],
      users: ["Users", "USER ADMINISTRATION", "Manage accounts and access permissions."],
      activity: ["Activity log", "AUDIT LOG", "Review activity recorded by the system."],
      settings: ["Settings", "SETTINGS", "Personalize your experience."],
    },
    settings: [
      "PREFERENCES",
      "Settings",
      "Personalize your interface and experience.",
      "Appearance",
      "Choose light or dark mode.",
      "Light",
      "Dark",
      "Personal notifications",
      "Control how notifications appear for this account.",
      "Receive notifications",
      "Show announcements from administrators.",
      "New notification badge",
      "Show a red dot for unseen notifications.",
      "Automatic refresh",
      "Check for new notifications every 30 seconds.",
      "Language",
      "Choose the primary display language.",
      "Interface language",
      "✓ Settings saved",
    ],
  },
};

const VI_EN = {
  "TỔNG QUAN": "OVERVIEW",
  "QUẢN TRỊ": "ADMINISTRATION",
  "Tổng quan": "Dashboard",
  "Tài liệu": "Documents",
  "Được chia sẻ": "Shared with me",
  "Gần đây": "Recent",
  "Yêu thích": "Favorites",
  "Người dùng": "Users",
  "Nhật ký hoạt động": "Activity log",
  "Cài đặt": "Settings",
  "Hồ sơ cá nhân": "My profile",
  "Tổng tài liệu": "Total documents",
  "Đã tải lên tháng này": "Uploaded this month",
  "Lượt tải xuống": "Downloads",
  "Dung lượng sử dụng": "Storage used",
  "Tài liệu bạn có quyền truy cập": "Documents you can access",
  "Được tạo từ đầu tháng": "Created since the start of the month",
  "Chưa có nhật ký tải xuống": "No download activity yet",
  "Lượt tải được ghi nhận": "Recorded downloads",
  "Dung lượng file đang quản lý": "Managed file storage",
  "Hoạt động gần đây": "Recent activity",
  "Những thay đổi mới nhất trong hệ thống": "Latest changes in the system",
  "Xem tất cả →": "View all →",
  "Phân bổ lưu trữ": "Storage distribution",
  "Dữ liệu từ bộ nhớ Local hiện tại": "Current local storage data",
  "đã dùng": "used",
  "Tài liệu mới nhất": "Latest documents",
  "5 tài liệu được cập nhật gần đây": "5 recently updated documents",
  "TÊN TÀI LIỆU": "DOCUMENT NAME",
  "NGƯỜI SỞ HỮU": "OWNER",
  "PHÒNG BAN": "DEPARTMENT",
  "CẬP NHẬT": "UPDATED",
  "DUNG LƯỢNG": "SIZE",
  "TRẠNG THÁI": "STATUS",
  "NGƯỜI DÙNG": "USER",
  "QUYỀN": "ROLE",
  "NGÀY TẠO": "CREATED AT",
  "EMAIL": "EMAIL",
  "THỜI GIAN": "TIME",
  "NGƯỜI THỰC HIỆN": "ACTOR",
  "HÀNH ĐỘNG": "ACTION",
  "CHI TIẾT": "DETAILS",
  "KHO TÀI LIỆU": "DOCUMENT LIBRARY",
  "Tất cả tài liệu": "All documents",
  "Quản lý và truy cập toàn bộ tài liệu doanh nghiệp.":
    "Manage and access all organization documents.",
  "Tải tài liệu lên": "Upload document",
  "Tất cả loại": "All types",
  "Tất cả phòng ban": "All departments",
  "Bộ lọc": "Filters",
  "Không tìm thấy tài liệu phù hợp.": "No matching documents found.",
  "Chưa có tài liệu.": "No documents yet.",
  "Hoạt động": "Active",
  "Lưu trữ": "Archived",
  "Đã chia sẻ": "Shared",
  "Bỏ yêu thích": "Remove from favorites",
  "Thêm vào yêu thích": "Add to favorites",
  "Xem trước tài liệu": "Preview document",
  "Tải xuống": "Download",
  "Chỉnh sửa": "Edit",
  "Chia sẻ": "Share",
  "Xóa": "Delete",
  "Tải": "Download",
  "Sửa": "Edit",
  "Đang tải dữ liệu...": "Loading data...",
  "Đang tải...": "Loading...",
  "Đang tải nhật ký...": "Loading activity log...",
  "Đang lấy dữ liệu từ hệ thống.": "Fetching data from the system.",
  "Không thể tải nhật ký": "Unable to load activity log",
  "Thử lại": "Try again",
  "Chưa có hoạt động": "No activity yet",
  "Chưa có nhật ký hoạt động nào được ghi nhận.": "No activity has been recorded yet.",
  "TÀI KHOẢN CỦA BẠN": "YOUR ACCOUNT",
  "Thông tin cá nhân": "Personal information",
  "Email liên hệ của hồ sơ CloudDocs.":
    "Contact email for your CloudDocs profile.",
  "Tên đăng nhập": "Username",
  "Tên đăng nhập được giữ cố định.": "The username cannot be changed.",
  "Họ và tên": "Full name",
  "Phòng ban": "Department",
  "Quyền:": "Role:",
  "Trạng thái:": "Status:",
  "Hoàn tác": "Reset",
  "Lưu thay đổi": "Save changes",
  "QUẢN TRỊ NGƯỜI DÙNG": "USER ADMINISTRATION",
  "Danh sách tài khoản": "Account list",
  "Chỉ ADMIN mới có quyền thay đổi role và trạng thái.":
    "Only ADMIN can change roles and account status.",
  "Làm mới": "Refresh",
  "Thông báo": "Notifications",
  "Thông báo chung từ quản trị viên": "Announcements from administrators",
  "Gửi thông báo": "Send notification",
  "Chưa có thông báo.": "No notifications yet.",
  "Gửi thông báo chung": "Send announcement",
  "Thông báo sẽ hiển thị cho tất cả tài khoản.": "The announcement will be shown to every account.",
  "Tiêu đề": "Title",
  "Nội dung": "Message",
  "Hủy": "Cancel",
  "Gửi tới tất cả user": "Send to all users",
  "THÔNG BÁO": "NOTIFICATION",
  "Chia sẻ tài liệu": "Share document",
  "Chọn người dùng": "Select user",
  "Quyền truy cập": "Access level",
  "VIEW — Chỉ xem": "VIEW — View only",
  "DOWNLOAD — Xem và tải xuống": "DOWNLOAD — View and download",
  "EDIT — Xem, tải xuống và chỉnh sửa": "EDIT — View, download and edit",
  "Cấp quyền": "Grant access",
  "Người đang được chia sẻ": "People with access",
  "Chưa chia sẻ cho ai.": "Not shared with anyone.",
  "Chỉnh sửa tài liệu": "Edit document",
  "Tên tài liệu": "Document name",
  "Mô tả": "Description",
  "ACTIVE — Hoạt động": "ACTIVE — Active",
  "ARCHIVED — Lưu trữ": "ARCHIVED — Archived",
  "Thêm tài liệu": "Add document",
  "Kéo thả file vào đây": "Drag and drop files here",
  "chọn file từ máy tính": "choose files from your computer",
  "PDF, DOCX, XLSX, PPTX, ZIP · tối đa 100 MB/file":
    "PDF, DOCX, XLSX, PPTX, ZIP · up to 100 MB/file",
  "Lưu vào MySQL": "Save to MySQL",
  "Đang tải phòng ban...": "Loading departments...",
  "Dung lượng": "Storage",
  "Quản lý dung lượng": "Manage storage",
  "Đăng xuất": "Sign out",
  "Tìm kiếm tài liệu, người dùng...": "Search documents, users...",
  "Tìm kiếm tài liệu...": "Search documents...",
  "Tìm theo tên, username hoặc email...": "Search by name, username or email...",
  "Mô tả ngắn...": "Short description...",
  "Mô tả ngắn (không bắt buộc)": "Short description (optional)",
  "Nhập nội dung thông báo...": "Enter notification message...",
  "Ví dụ: Bảo trì hệ thống": "Example: System maintenance",
  "Đang hoạt động": "Active",
  "Đã khóa": "Locked",
  "Quản trị viên": "Administrator",
  "Nhân viên": "Employee",
  "Không tên": "Untitled",
  "Không xác định": "Unknown",
  "Đây là tình hình tài liệu và hoạt động của doanh nghiệp hôm nay.":
    "Here is your organization's document and activity overview.",
  "⚙ Bộ lọc": "⚙ Filters",
  "↻ Làm mới": "↻ Refresh",
  "＋ Gửi thông báo": "＋ Send notification",
  "Chào mừng trở lại": "Welcome back",
  "Đăng nhập để quản lý tài liệu doanh nghiệp.": "Sign in to manage organization documents.",
  "ĐĂNG NHẬP": "SIGN IN",
  "Đăng nhập": "Sign in",
  "Đăng ký": "Register",
  "Tạo tài khoản": "Create an account",
  "Chưa có tài khoản?": "Don't have an account?",
  "Đã có tài khoản?": "Already have an account?",
  "Tên đăng nhập hoặc email": "Username or email",
  "Mật khẩu": "Password",
  "Tài khoản mới mặc định có quyền USER.": "New accounts receive the USER role by default.",
  "Khu vực sẵn sàng": "Area ready",
  "Tính năng này sẽ được kết nối với REST API ở các bước tiếp theo.":
    "This feature will be connected to the REST API in a later step.",
};
const EN_VI = Object.fromEntries(Object.entries(VI_EN).map(([vi, en]) => [en, vi]));
function translateInterface(root = document.body) {
  if (!root) return;
  const dictionary = currentSettings.language === "en" ? VI_EN : EN_VI;
  const translateNode = (node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      if (node.parentElement?.closest("[data-no-translate]")) return;
      const raw = node.nodeValue,
        value = raw.trim();
      if (value && dictionary[value]) node.nodeValue = raw.replace(value, dictionary[value]);
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    if (node.hasAttribute("data-no-translate")) return;
    for (const attribute of ["placeholder", "title", "aria-label"]) {
      const value = node.getAttribute(attribute);
      if (value && dictionary[value]) node.setAttribute(attribute, dictionary[value]);
    }
    [...node.childNodes].forEach(translateNode);
  };
  translateNode(root);
}

function settingsKey() {
  return `clouddocs_settings_${currentUser?.id || "guest"}`;
}
function loadUserSettings() {
  try {
    currentSettings = {
      ...DEFAULT_SETTINGS,
      ...JSON.parse(localStorage.getItem(settingsKey()) || "{}"),
    };
  } catch {
    currentSettings = { ...DEFAULT_SETTINGS };
  }
}
function applyLanguage() {
  const text = languageText[currentSettings.language] || languageText.vi;
  document.documentElement.lang = currentSettings.language;
  document.querySelectorAll(".nav-item[data-page]").forEach((item) => {
    const label = text.nav[item.dataset.page];
    const node = [...item.childNodes].find((child) => child.nodeType === Node.TEXT_NODE);
    if (label && node) node.textContent = ` ${label}`;
  });
  Object.entries(text.meta).forEach(([page, meta]) => {
    pageNames[page] = [...meta];
  });
  document.getElementById("globalSearch").placeholder = text.search;
  const ids = [
    "settingsEyebrow",
    "settingsTitle",
    "settingsDescription",
    "appearanceTitle",
    "appearanceDescription",
    "lightThemeLabel",
    "darkThemeLabel",
    "personalNotificationTitle",
    "personalNotificationDescription",
    "receiveNotificationsLabel",
    "receiveNotificationsHelp",
    "badgeNotificationsLabel",
    "badgeNotificationsHelp",
    "refreshNotificationsLabel",
    "refreshNotificationsHelp",
    "languageTitle",
    "languageDescription",
    "languageLabel",
    "settingsSaved",
  ];
  ids.forEach((id, index) => {
    const element = document.getElementById(id);
    if (element) element.textContent = text.settings[index];
  });
  if (currentUser)
    document.getElementById("sidebarUserRole").textContent =
      currentUser.role === "ADMIN" ? text.admin : text.user;
  document.getElementById("pageTitle").textContent =
    text.pages[activePage] || pageNames[activePage]?.[0] || "CloudDocs";
  if (["shared", "recent", "favorites"].includes(activePage)) {
    document.getElementById("collectionEyebrow").textContent = pageNames[activePage][1];
    document.getElementById("collectionTitle").textContent = pageNames[activePage][0];
    document.getElementById("collectionDesc").textContent = pageNames[activePage][2];
  }
  translateInterface(document.body);
}
function configureNotificationUpdates() {
  clearInterval(notificationTimer);
  notificationTimer = null;
  if (!currentSettings.notifications) {
    notifications = [];
    document.getElementById("notificationDot").classList.add("hidden");
    document.getElementById("notificationList").innerHTML =
      `<div class="notification-empty">${currentSettings.language === "en" ? "Notifications are disabled in Settings." : "Thông báo đang được tắt trong Cài đặt."}</div>`;
    return;
  }
  loadNotifications();
  if (currentSettings.autoRefresh) notificationTimer = setInterval(loadNotifications, 30000);
}
function applySettings() {
  document.documentElement.dataset.theme = currentSettings.theme;
  applyLanguage();
  if (currentUser) updateUserUI();
  renderTables();
  if (["shared", "recent", "favorites"].includes(activePage)) renderCollection(currentCollection);
  translateInterface(document.body);
  configureNotificationUpdates();
}
function saveSettings() {
  localStorage.setItem(settingsKey(), JSON.stringify(currentSettings));
  applySettings();
  renderSettings();
  const saved = document.getElementById("settingsSaved");
  saved.classList.remove("hidden");
  clearTimeout(window.settingsSavedTimer);
  window.settingsSavedTimer = setTimeout(() => saved.classList.add("hidden"), 1800);
}
function renderSettings() {
  document
    .querySelectorAll('input[name="theme"]')
    .forEach((input) => (input.checked = input.value === currentSettings.theme));
  document.getElementById("settingNotifications").checked = currentSettings.notifications;
  document.getElementById("settingNotificationBadge").checked = currentSettings.badge;
  document.getElementById("settingNotificationRefresh").checked = currentSettings.autoRefresh;
  document.getElementById("settingNotificationBadge").disabled = !currentSettings.notifications;
  document.getElementById("settingNotificationRefresh").disabled = !currentSettings.notifications;
  document.getElementById("settingLanguage").value = currentSettings.language;
}

function fileIcon(type) {
  const label = String(type || "FILE");
  return `<span class="file-icon ${escapeHtml(label.toLowerCase())}">${escapeHtml(label)}</span>`;
}
function initials(name) {
  return (
    (name || "AD")
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((x) => x[0])
      .join("")
      .toUpperCase() || "AD"
  );
}
function ownerCell(d) {
  return `<div class="owner"><span class="avatar">${initials(d.owner)}</span><span data-no-translate>${escapeHtml(d.owner || "—")}</span></div>`;
}
function formatSize(bytes) { return formatStorageBytes(bytes); }
function formatUpdated(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString(currentSettings.language === "en" ? "en-US" : "vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  });
}
function normalize(d) {
  return {
    id: d.id,
    name: d.title || d.fileName || (currentSettings.language === "en" ? "Untitled" : "Không tên"),
    type: (d.fileType || "FILE").toUpperCase(),
    owner: d.owner || "—",
    dept: d.department || "—",
    updated: d.updatedAt,
    size: formatSize(d.fileSize),
    status: (d.status || "ACTIVE").toLowerCase(),
    description: d.description || "",
    fileName: d.fileName || d.title || "",
    access: (d.access || "NONE").toUpperCase(),
  };
}
function escapeHtml(value) {
  return String(value ?? "").replace(
    /[&<>'"]/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[c],
  );
}
function row(d, full = false, selectable = full) {
  const en = currentSettings.language === "en";
  const status =
    d.status === "shared"
      ? en
        ? "Shared"
        : "Đã chia sẻ"
      : d.status === "archived"
        ? en
          ? "Archived"
          : "Lưu trữ"
        : en
          ? "Active"
          : "Hoạt động";
  const access = (d.access || "NONE").toUpperCase();
  const canShare = currentUser && (access === "ADMIN" || access === "OWNER");
  const canDelete = canShare;
  const canEdit = ["ADMIN", "OWNER", "EDIT"].includes(access);
  const canDownload = ["ADMIN", "OWNER", "EDIT", "DOWNLOAD"].includes(access);
  const canView = ["ADMIN", "OWNER", "EDIT", "DOWNLOAD", "VIEW"].includes(access);
  const actions = [];
  if (canView)
    actions.push(
      `<button class="favorite-btn ${favoriteIds.has(Number(d.id)) ? "active" : ""}" onclick="toggleFavorite(${d.id})" title="${favoriteIds.has(Number(d.id)) ? (en ? "Remove from favorites" : "Bỏ yêu thích") : en ? "Add to favorites" : "Thêm vào yêu thích"}" aria-label="${favoriteIds.has(Number(d.id)) ? (en ? "Remove from favorites" : "Bỏ yêu thích") : en ? "Add to favorites" : "Thêm vào yêu thích"}">${favoriteIds.has(Number(d.id)) ? "★" : "☆"}</button>`,
    );
  if (canView) {
    actions.push(
      `<button class="table-action"onclick="previewDocument(${d.id})"title="Xem trước tài liệu">👁 Preview</button>`,
    );
  }
  if (canDownload)
    actions.push(
      `<button class="table-action" onclick="downloadDocument(${d.id})" title="${en ? "Download" : "Tải xuống"}">⬇ ${en ? "Download" : "Tải"}</button>`,
    );
  if (canEdit)
    actions.push(
      `<button class="table-action" onclick="openEditModal(${d.id})" title="${en ? "Edit" : "Chỉnh sửa"}">✎ ${en ? "Edit" : "Sửa"}</button>`,
    );
  if (canShare)
    actions.push(
      `<button class="share-btn" onclick="openShareModal(${d.id})" title="${en ? "Share document" : "Chia sẻ tài liệu"}">${en ? "Share" : "Chia sẻ"}</button>`,
    );
  if (canDelete)
    actions.push(
      `<button class="delete-btn" onclick="deleteDocument(${d.id})" title="${en ? "Delete document" : "Xóa tài liệu"}">🗑 ${en ? "Delete" : "Xóa"}</button>`,
    );
  if (!actions.length) actions.push(`<span class="row-dash">—</span>`);
  return `<tr data-id="${d.id}">
    ${selectable ? `<td><input type="checkbox" class="document-select" aria-label="Chọn tài liệu ${escapeHtml(d.name)}" data-document-id="${d.id}" ${selectedDocumentIds.has(Number(d.id)) ? "checked" : ""} onchange="selectDocument(${d.id},this.checked)"></td>` : ""}
    <td><div class="file-cell">${fileIcon(d.type)}<span data-no-translate>${escapeHtml(d.name)}</span></div></td>
    <td>${ownerCell(d)}</td><td data-no-translate>${escapeHtml(d.dept)}</td><td>${escapeHtml(formatUpdated(d.updated))}</td><td>${escapeHtml(d.size)}</td>
    ${full ? `<td><span class="status ${escapeHtml(d.status)}">${status}</span></td>` : ""}
    <td><div class="table-actions">${actions.join("")}</div></td>
  </tr>`;
}

function getToken() { return CognitoAuth.token(); }
function requireSession(token) {
  if (!token || token !== getToken()) throw new Error("Phiên đăng nhập đã thay đổi. Vui lòng thực hiện lại thao tác.");
}
function setToken(token) { if (!token) CognitoAuth.clear(); }

async function api(path, options = {}) {
  const token = getToken();
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (token && token !== getToken()) throw new Error("Phiên đăng nhập đã thay đổi.");
  if (!response.ok) {
    if (response.status === 401) {
      setToken(null);
      showAuth("login");
    }
    throw new Error(data?.message || data?.error || `HTTP ${response.status}`);
  }
  return data;
}

function showAuth() {
  window.resetWorkTasks?.();
  window.closePreviewModal?.();
  for (const id of ["uploadModal", "shareModal", "editModal", "notificationModal", "notificationDetailModal"]) document.getElementById(id).classList.add("hidden");
  selectedDocumentIds.clear(); selectedTrashIds.clear(); trashDocuments = []; bulkShareIds = [];
  shareableUsers = []; shareDocumentId = null; editDocumentId = null;
  document.getElementById("fileInput").value = "";
  document.getElementById("selectedFiles").replaceChildren();
  document.getElementById("uploadDescription").value = "";
  currentUser = null; docs = []; currentCollection = []; favoriteIds.clear(); notifications = [];
  activePage = "dashboard";
  for (const id of ["dashboardActivity", "recentTable", "collectionTable", "trashTable", "permissionList"]) document.getElementById(id).replaceChildren();
  for (const id of ["statTotalDocuments", "statUploadedMonth", "statDownloads", "storageScope", "storageAccountingNote"]) document.getElementById(id).textContent = "—";
  document.querySelectorAll(".page").forEach(p => p.classList.toggle("hidden", p.id !== "dashboardPage"));
  document.getElementById("sidebarUserName").textContent = "—";
  document.getElementById("sidebarUserRole").textContent = "—";
  document.getElementById("notificationList").replaceChildren();
  document.getElementById("userTable").replaceChildren();
  document.getElementById("documentTable").replaceChildren();
  storageQuota = null;
  document.getElementById("storageProgress").style.width = "0%";
  document.getElementById("storageUsage").textContent = "—";
  document.getElementById("storageDonut").style.background = "#dce1e9";
  document.getElementById("statStorage").textContent = "—";
  document.getElementById("donutStorage").textContent = "—";
  document.getElementById("storageBreakdown").replaceChildren();
  document.getElementById("adminStorageRows").replaceChildren();
  document.getElementById("adminStoragePanel").classList.add("hidden");
  clearInterval(notificationTimer);
  notificationTimer = null;
  currentSettings = { ...DEFAULT_SETTINGS };
  document.documentElement.dataset.theme = "light";
  document.documentElement.lang = "vi";
  applyLanguage();
  document.getElementById("profileForm").reset();
  document.getElementById("displayNameForm").reset();
  document.getElementById("displayNameMessage").textContent = "";
  document.getElementById("authScreen").classList.remove("hidden");
  document.getElementById("appShell").classList.add("hidden");
}
function showApp() {
  document.getElementById("authScreen").classList.add("hidden");
  document.getElementById("appShell").classList.remove("hidden");
  loadUserSettings();
  updateUserUI();
  applySettings();
}
function profileName(user) {
  const name = user?.fullName?.trim();
  return !name || ["Người dùng CloudDocs", "Chưa cập nhật"].includes(name) ? "Chưa cập nhật" : name;
}
function contactEmail(user) {
  return user?.email && !user.email.toLowerCase().endsWith("@identity.invalid") ? user.email : "";
}
function updateUserUI() {
  if (!currentUser) return;
  document.getElementById("displayNameForm").classList.toggle("hidden", profileName(currentUser) !== "Chưa cập nhật");
  if (currentUser.role !== "ADMIN") {
    document.getElementById("adminStoragePanel").classList.add("hidden");
    document.getElementById("adminStorageRows").replaceChildren();
  }
  document.getElementById("sidebarUserName").textContent = profileName(currentUser);
  const locale = languageText[currentSettings.language] || languageText.vi;
  document.getElementById("sidebarUserRole").textContent =
    currentUser.role === "ADMIN" ? locale.admin : locale.user;
  document.getElementById("topAvatar").textContent = (profileName(currentUser) === "Chưa cập nhật" ? "CD" : initials(profileName(currentUser)));
  document.querySelector(".user-mini > .avatar").textContent = (profileName(currentUser) === "Chưa cập nhật" ? "CD" : initials(profileName(currentUser)));
  document.getElementById("profileNav").classList.remove("hidden");
  document.getElementById("topAvatar").disabled = false;
  document.getElementById("topAvatar").setAttribute("aria-label", "Mở hồ sơ cá nhân");
  document
    .getElementById("openNotificationComposer")
    .classList.toggle("hidden", currentUser.role !== "ADMIN");
  const usersNav = document.querySelector('.nav-item[data-page="users"]');
  if (usersNav) usersNav.style.display = currentUser.role === "ADMIN" ? "" : "none";
  const heading = document.querySelector("#dashboardPage h1");
  if (heading) {
    const hour = new Date().getHours();
    const greeting =
      currentSettings.language === "en"
        ? hour < 12
          ? "Good morning"
          : hour < 18
            ? "Good afternoon"
            : "Good evening"
        : hour < 12
          ? "Chào buổi sáng"
          : hour < 18
            ? "Chào buổi chiều"
            : "Chào buổi tối";
    heading.innerHTML = `${greeting}, ${escapeHtml(profileName(currentUser) === "Chưa cập nhật" ? "bạn" : profileName(currentUser))} 👋`;
  }
  const eyebrow = document.querySelector("#dashboardPage .eyebrow");
  if (eyebrow)
    eyebrow.textContent = new Date()
      .toLocaleDateString(currentSettings.language === "en" ? "en-US" : "vi-VN", {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
      })
      .toUpperCase();
}

function authError(error) {
  const element = document.getElementById("loginError");
  element.textContent = error.message || "Không thể kết nối. Vui lòng thử lại.";
  element.classList.remove("hidden");
}
async function bootstrapAuth() {
  try {
    await CognitoAuth.callback();
    if (!getToken()) { showAuth(); return; }
    currentUser = await api("/auth/me");
    showApp();
    await loadDocuments();
    await showPage("dashboard");
  } catch (error) {
    setToken(null); currentUser = null; showAuth(); authError(error);
  }
}
document.getElementById("cognitoLogin").onclick = async () => {
  const button = document.getElementById("cognitoLogin");
  if (button.disabled) return;
  button.disabled = true; button.textContent = "Đang chuyển đến trang đăng nhập…";
  document.getElementById("loginError").classList.add("hidden");
  try { await CognitoAuth.login(); }
  catch (error) { authError(error); button.disabled = false; button.textContent = "Đăng nhập"; }
};
// Back navigation can restore the disabled button from the browser's page cache.
window.addEventListener("pageshow", () => {
  const button = document.getElementById("cognitoLogin");
  button.disabled = false;
  button.textContent = "Đăng nhập";
});
document.getElementById("logoutButton").onclick = async () => {
  try { await api("/auth/logout", {method: "POST"}); } catch {}
  setToken(null); currentUser = null; docs = []; favoriteIds.clear(); currentCollection = [];
  notifications = []; showAuth();
  try { CognitoAuth.logout(); } catch (error) { authError(error); }
};

// Refresh database-backed permissions when returning to the application.
window.addEventListener("focus", async () => {
  if (!currentUser || !getToken()) return;
  try { currentUser = await api("/auth/me"); updateUserUI();
    if (activePage === "users" && currentUser.role !== "ADMIN") await showPage("dashboard");
  } catch (error) { currentUser = null; showAuth(); authError(error); }
});

async function loadDashboard() {
  const sessionToken = getToken();
  try {
    const data = await api("/dashboard/summary");
    if (sessionToken !== getToken()) return;
    const total = Number(data.totalDocuments || 0);
    const month = Number(data.uploadedThisMonth || 0);


    const numberLocale = currentSettings.language === "en" ? "en-US" : "vi-VN";
    document.getElementById("statTotalDocuments").textContent = total.toLocaleString(numberLocale);
    document.getElementById("statUploadedMonth").textContent = month.toLocaleString(numberLocale);
    document.getElementById("statDownloads").textContent =
      data.downloadsAvailable && data.downloads != null
        ? Number(data.downloads).toLocaleString(numberLocale)
        : "—";
    document.getElementById("statDownloadsNote").textContent = data.downloadsAvailable
      ? "Lượt tải được ghi nhận"
      : "Chưa có nhật ký tải xuống";
    renderStorageQuota(data.storageQuota);
    renderAdminStorage(data);
    renderStorageDistribution(data.storageDistribution);


    renderDashboardActivity(Array.isArray(data.recentDocuments) ? data.recentDocuments : []);

  } catch (error) {
    console.error(error);
    if (sessionToken !== getToken()) return;
    renderStorageUnavailable("Không tải được dung lượng");
    renderStorageDistribution(null);
    document.getElementById("adminStorageRows").replaceChildren();
    document.getElementById("adminStoragePanel").classList.add("hidden");
    showToast(`Không tải được tổng quan: ${error.message}`);
  }
}

function formatStorageBytes(bytes) {
  if (bytes == null || !Number.isFinite(Number(bytes)) || Number(bytes) < 0) return "—";
  let value = Number(bytes);
  const units = ["B", "KB", "MB", "GB", "TB"];
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit++;
  }
  return `${Number(value.toFixed(2))} ${units[unit]}`;
}

function renderStorageUnavailable(message = "Chưa tải được dung lượng") {
  storageQuota = null;
  document.getElementById("storageProgress").style.width = "0%";
  document.getElementById("statStorage").textContent = "-";
  document.getElementById("storageUsage").textContent = message;
}
function renderStorageQuota(quota) {
  if (!quota || ![quota.usedBytes, quota.limitBytes].every(v => typeof v === "number" && Number.isFinite(v) && v >= 0) || quota.limitBytes <= 0) {
    renderStorageUnavailable(); return;
  }
  storageQuota = quota;
  const percent = Math.min(100, quota.usedBytes / quota.limitBytes * 100);
  const progress = document.getElementById("storageProgress");
  progress.style.width = percent + "%";
  progress.style.background = percent >= 100 ? "#d14343" : percent >= 90 ? "#db8b20" : "#315bea";
  const en = currentSettings.language === "en";
  const missing = Number(quota.missingSizeCount || 0);
  const used = (missing > 0 ? "≥ " : "") + formatStorageBytes(quota.usedBytes);
  const usage = document.getElementById("storageUsage");
  usage.textContent = used + " / " + formatStorageBytes(quota.limitBytes);
  usage.title = missing ? (en ? "Some files lack size metadata" : "Một số tài liệu chưa có thông tin kích thước") : "";
  document.getElementById("statStorage").textContent = used;
  document.getElementById("storageQuotaNote").textContent = (en ? "Personal quota: " : "Hạn mức cá nhân: ") + formatStorageBytes(quota.limitBytes);
}
function renderStorageDistribution(data) {
  const en = currentSettings.language === "en";
  const note = document.getElementById("storageAccountingNote");
  if (!data || !Number.isFinite(data.usedBytes) || data.usedBytes < 0 || !data.storageByType) {
    document.getElementById("donutStorage").textContent = "-";
    document.getElementById("storageDonut").style.background = "#dce1e9";
    document.getElementById("storageBreakdown").textContent = en ? "Storage data unavailable" : "Chưa tải được phân bố lưu trữ";
    document.getElementById("storageScope").textContent = "";
    note.textContent = ""; note.classList.add("hidden"); return;
  }
  document.getElementById("donutStorage").textContent = (data.missingSizeCount > 0 ? "≥ " : "") + formatStorageBytes(data.usedBytes);
  document.getElementById("storageScope").textContent = data.scope === "SYSTEM"
    ? (en ? "All active documents across all accounts" : "Tất cả tài liệu đang hoạt động trong hệ thống")
    : (en ? "Your active documents; shared files excluded" : "Tài liệu đang hoạt động do bạn sở hữu; không tính file được chia sẻ");
  renderStorageBreakdown(data.storageByType);
  const notes = [];
  if (data.missingSizeCount > 0) notes.push(en ? data.missingSizeCount + " files lack size metadata" : data.missingSizeCount + " tài liệu chưa có thông tin kích thước");
  if (data.zeroSizeCount > 0) notes.push(en ? data.zeroSizeCount + " files have a recorded size of 0 B" : data.zeroSizeCount + " tài liệu có kích thước ghi nhận 0 B");
  note.textContent = notes.join(" · ");
  note.classList.toggle("hidden", notes.length === 0);
}

async function loadStorageUsage() {
  const sessionToken = getToken();
  try {
    const quota = await api("/storage/usage");
    if (sessionToken === getToken()) renderStorageQuota(quota);
    return sessionToken === getToken() ? quota : null;
  } catch (error) {
    if (sessionToken === getToken()) renderStorageUnavailable("Không tải được dung lượng");
    console.error("Không tải được dung lượng", error);
    return null;
  }
}

function renderAdminStorage(data) {
  const panel = document.getElementById("adminStoragePanel");
  const admin = currentUser?.role === "ADMIN" && data.isAdmin;
  panel.classList.toggle("hidden", !admin);
  const rows = document.getElementById("adminStorageRows");
  rows.replaceChildren();
  if (!admin) return;
  const en = currentSettings.language === "en";
  document.getElementById("adminStorageTitle").textContent = en
    ? "Storage by account"
    : "Dung lượng theo tài khoản";
  document.getElementById("systemStorageUsage").textContent =
    `${en ? "System allocation" : "Tổng phân bổ toàn hệ thống"}: ${data.systemMissingSizeCount > 0 ? "≥ " : ""}${formatStorageBytes(data.systemStorageBytes)}${data.systemMissingSizeCount > 0 ? " · " + data.systemMissingSizeCount + " tài liệu thiếu kích thước" : ""}${data.unlinkedOwnerCount > 0 ? " · " + data.unlinkedOwnerCount + " tài liệu thiếu liên kết chủ sở hữu" : ""}`;
  document.getElementById("adminStorageHead").innerHTML = `<tr>${(en
    ? ["ACCOUNT", "ROLE", "ALLOCATED", "QUOTA", "REMAINING"]
    : ["TÀI KHOẢN", "QUYỀN", "ĐÃ PHÂN BỔ", "HẠN MỨC", "CÒN LẠI"]
  )
    .map((label) => `<th>${label}</th>`)
    .join("")}</tr>`;
  rows.innerHTML = (data.storageUsers || [])
    .map(
      (account) => `<tr>
    <td data-no-translate>${escapeHtml(account.displayName || (account.username?.startsWith("c_") ? "Chưa cập nhật" : account.username))}${account.userId != null ? `<small> · #${escapeHtml(account.userId)}</small>` : ""}</td>
    <td data-no-translate>${escapeHtml(account.role)}</td>
    <td data-no-translate>${formatStorageBytes(account.usedBytes)}</td>
    <td data-no-translate>${formatStorageBytes(account.limitBytes)}</td>
    <td data-no-translate>${formatStorageBytes(account.remainingBytes)}</td>
  </tr>`,
    )
    .join("");
}

document.getElementById("manageStorage").addEventListener("click", async () => {
  await showPage("dashboard");
  document.querySelector(".storage-panel").scrollIntoView({ behavior: "smooth", block: "center" });
});

function renderDashboardActivity(items) {
  const box = document.getElementById("dashboardActivity");
  if (!box) return;
  if (!items.length) {
    box.innerHTML = '<div class="dashboard-loading">Chưa có tài liệu.</div>';
    return;
  }
  box.innerHTML = items
    .map((d) => {
      const owner = escapeHtml(d.owner || "—");
      const title = escapeHtml(d.title || "Không tên");
      const dept = escapeHtml(d.department || "—");
      const type = escapeHtml((d.fileType || "FILE").toUpperCase());
      return `<div class="activity-row">
      <div class="activity-avatar blue-bg">${initials(d.owner || "CloudDocs")}</div>
      <div><strong data-no-translate>${owner}</strong> ${currentSettings.language === "en" ? "updated" : "cập nhật"} <b data-no-translate>${title}</b><small><span data-no-translate>${dept}</span> · ${escapeHtml(formatUpdated(d.updatedAt))}</small></div>
      <span class="file-type ${type.toLowerCase()}">${type}</span>
    </div>`;
    })
    .join("");
}

function renderStorageBreakdown(byType) {
  const box = document.getElementById("storageBreakdown");
  if (!box) return;
  const en = currentSettings.language === "en";
  const entries = Object.entries(byType).filter(([,v]) => typeof v === "number" && Number.isFinite(v) && v >= 0)
    .sort((a,b) => b[1] - a[1]);
  const donut = document.getElementById("storageDonut");
  if (!entries.length) {
    box.textContent = en ? "No documents with a recorded size." : "Chưa có tài liệu có thông tin kích thước.";
    donut.style.background = "#dce1e9"; return;
  }
  const colors = ["#315bea", "#8665d8", "#29a888", "#e7a044", "#db6d8d", "#94a3b8", "#0891b2", "#be7c37", "#c026d3", "#64748b"];
  const total = entries.reduce((sum,[,v]) => sum + v,0);
  let end = 0;
  const segments = entries.filter(([,v]) => v > 0).map(([type,v]) => {
    const i = entries.findIndex(([key]) => key === type);
    const start = end; end += v / total * 100;
    return colors[i % colors.length] + " " + start + "% " + end + "%";
  });
  donut.style.background = total > 0 ? "conic-gradient(" + segments.join(",") + ")" : "#dce1e9";
  box.innerHTML = entries.map(([type,bytes],i) =>
    '<div><span><i class="dot" style="background:' + colors[i % colors.length] + '"></i> ' + escapeHtml(type) + '</span><b>' + formatStorageBytes(bytes) + '</b></div>').join("");
}

async function loadDepartments() {
  const filter = document.getElementById("departmentFilter");
  if (!filter || !getToken()) return;
  const selected = filter.value;
  try {
    const data = await api("/departments");
    const departments = Array.isArray(data)
      ? data.filter((value) => typeof value === "string" && value.trim())
      : [];
    filter.innerHTML =
      `<option value="">Tất cả phòng ban</option>` +
      departments
        .map((value) => {
          const safe = escapeHtml(value.trim());
          return `<option value="${safe}" data-no-translate>${safe}</option>`;
        })
        .join("");
    if (departments.some((value) => value === selected)) filter.value = selected;

    for (const id of ["uploadDepartment", "editDepartment"]) {
      const select = document.getElementById(id);
      if (!select) continue;
      const previous = select.value;
      select.innerHTML = departments
        .map((value) => {
          const safe = escapeHtml(value.trim());
          return `<option value="${safe}" data-no-translate>${safe}</option>`;
        })
        .join("");
      const preferred = departments.includes(previous) ? previous : currentUser?.department;
      if (preferred && departments.includes(preferred)) select.value = preferred;
    }
  } catch (error) {
    console.error(error);
    filter.innerHTML = `<option value="">Tất cả phòng ban</option>`;
  }
}

async function loadDocuments() {
  const sessionToken = getToken();
  selectedDocumentIds.clear();
  try {
    const [data] = await Promise.all([
      api("/documents"),
      loadDepartments(),
      loadFavoriteIds(),
      loadStorageUsage(),
    ]);
    if (sessionToken !== getToken()) return;
    docs = Array.isArray(data) ? data.map(normalize) : [];
    selectedDocumentIds = new Set([...selectedDocumentIds].filter(id => docs.some(d => Number(d.id) === id)));
    renderTables();
    filterDocs();
    updateBulkToolbar();
  } catch (error) {
    console.error(error);
    showToast(`Không tải được tài liệu: ${error.message}`);
  }
}

async function loadFavoriteIds() {
  try {
    const data = await api("/favorites");
    favoriteIds = new Set((Array.isArray(data) ? data : []).map(Number));
  } catch (error) {
    console.error(error);
    favoriteIds = new Set();
  }
}

window.toggleFavorite = async function (documentId) {
  const id = Number(documentId);
  const wasFavorite = favoriteIds.has(id);
  try {
    await api(`/favorites/${id}`, { method: wasFavorite ? "DELETE" : "PUT" });
    if (wasFavorite) favoriteIds.delete(id);
    else favoriteIds.add(id);
    renderTables();
    if (activePage === "favorites") {
      currentCollection = favoriteDocuments();
      renderCollection(currentCollection, "Bạn chưa yêu thích tài liệu nào.");
    } else if (["shared", "recent"].includes(activePage)) {
      renderCollection(currentCollection);
    }
    showToast(wasFavorite ? "Đã bỏ khỏi danh sách yêu thích." : "Đã thêm vào danh sách yêu thích.");
  } catch (error) {
    showToast(`Không thể cập nhật yêu thích: ${error.message}`);
  }
};

function renderCollection(items, emptyMessage = "Chưa có tài liệu phù hợp.") {
  const table = document.getElementById("collectionTable");
  if (!table) return;
  table.innerHTML = items.length
    ? items.map((document) => row(document, true, false)).join("")
    : `<tr><td colspan="7" style="text-align:center;padding:40px;color:#9099a8">${escapeHtml(emptyMessage)}</td></tr>`;
}

function favoriteDocuments() {
  return [...favoriteIds]
    .map((id) => docs.find((document) => Number(document.id) === id))
    .filter(Boolean);
}

async function loadCollection(page) {
  const data = pageNames[page];
  document.getElementById("collectionEyebrow").textContent = data[1];
  document.getElementById("collectionTitle").textContent = data[0];
  document.getElementById("collectionDesc").textContent = data[2];
  document.getElementById("collectionPage").classList.remove("hidden");
  const table = document.getElementById("collectionTable");
  table.innerHTML = `<tr><td colspan="7" class="dashboard-loading">Đang tải dữ liệu...</td></tr>`;
  try {
    await loadFavoriteIds();
    if (page === "favorites") {
      await loadDocuments();
      currentCollection = favoriteDocuments();
      renderCollection(currentCollection, "Bạn chưa yêu thích tài liệu nào.");
      return;
    }
    const endpoint = page === "shared" ? "/documents/shared" : "/documents/recent";
    const response = await api(endpoint);
    currentCollection = Array.isArray(response) ? response.map(normalize) : [];
    currentCollection.forEach((document) => {
      const index = docs.findIndex((item) => Number(item.id) === Number(document.id));
      if (index >= 0) docs[index] = document;
      else docs.push(document);
    });
    const empty =
      page === "shared"
        ? "Chưa có tài liệu nào được chia sẻ với bạn."
        : "Bạn chưa tương tác với tài liệu nào gần đây.";
    renderCollection(currentCollection, empty);
  } catch (error) {
    currentCollection = [];
    renderCollection([], `Không tải được dữ liệu: ${error.message}`);
  }
}
// =========================
// ACTIVITY LOG
// =========================

const activityLabels = {
  TASK_DELETED:"Xóa công việc", TASK_ASSIGNED:"Giao việc", TASK_STARTED: "Bắt đầu công việc", TASK_SUBMITTED: "Nộp báo cáo", TASK_APPROVED: "Duyệt hoàn thành", TASK_CHANGES_REQUESTED: "Yêu cầu sửa báo cáo",
  RESTORE: "Khôi phục tài liệu", PURGE: "Xóa vĩnh viễn", DELETE_NOTIFICATION: "Xóa thông báo",
  LOGIN: "Đăng nhập",
  LOGOUT: "Đăng xuất",
  CREATE_DOCUMENT: "Tạo tài liệu",
  UPLOAD: "Tải tệp lên",
  VIEW: "Xem tài liệu",
  DOWNLOAD: "Tải xuống",
  EDIT: "Chỉnh sửa",
  DELETE: "Xóa tài liệu",
  SHARE: "Chia sẻ",
  REVOKE_PERMISSION: "Thu hồi quyền",
  SEND_NOTIFICATION: "Gửi thông báo",
};
const activityLabelsEn = {
  TASK_DELETED:"Delete task", TASK_ASSIGNED:"Assign task", TASK_STARTED: "Start task", TASK_SUBMITTED: "Submit report", TASK_APPROVED: "Approve task", TASK_CHANGES_REQUESTED: "Request changes",
  RESTORE: "Restore document", PURGE: "Delete permanently", DELETE_NOTIFICATION: "Delete announcement",
  LOGIN: "Sign in",
  LOGOUT: "Sign out",
  CREATE_DOCUMENT: "Create document",
  UPLOAD: "Upload",
  VIEW: "View document",
  DOWNLOAD: "Download",
  EDIT: "Edit",
  DELETE: "Delete",
  SHARE: "Share",
  REVOKE_PERMISSION: "Revoke access",
  SEND_NOTIFICATION: "Send notification",
};

const activityIcons = {
  LOGIN: "🔐",
  LOGOUT: "🚪",
  CREATE_DOCUMENT: "📄",
  UPLOAD: "⬆",
  VIEW: "👁",
  DOWNLOAD: "⬇",
  EDIT: "✎",
  DELETE: "🗑",
  SHARE: "🔗",
  REVOKE_PERMISSION: "🔒",
  SEND_NOTIFICATION: "🔔",
};

function formatActivityTime(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return escapeHtml(value);
  }

  return date.toLocaleString(currentSettings.language === "en" ? "en-US" : "vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function activityAction(action) {
  const labels = currentSettings.language === "en" ? activityLabelsEn : activityLabels;
  return (
    labels[action] || action || (currentSettings.language === "en" ? "Unknown" : "Không xác định")
  );
}

function activityIcon(action) {
  return activityIcons[action] || "•";
}

function activityDetails(log) {
  if (String(log.action || "").startsWith("TASK_") && log.details) return escapeHtml(log.details);
  if (currentSettings.language === "en") {
    const actor = escapeHtml(log.fullName || log.username || "System"),
      documentName = escapeHtml(log.documentName || "");
    const target = escapeHtml(log.targetFullName || log.targetUsername || "");
    const messages = {
      LOGIN: `${actor} signed in`,
      LOGOUT: `${actor} signed out`,
      CREATE_DOCUMENT: `${actor} created ${documentName}`,
      UPLOAD: `${actor} uploaded ${documentName}`,
      VIEW: `${actor} viewed ${documentName}`,
      DOWNLOAD: `${actor} downloaded ${documentName}`,
      EDIT: `${actor} edited ${documentName}`,
      DELETE: `${actor} moved ${documentName} to trash`,
      RESTORE: `${actor} restored ${documentName}`,
      PURGE: `${actor} permanently deleted ${documentName}`,
      DELETE_NOTIFICATION: `${actor} deleted an announcement`,
      SHARE: `${actor} shared ${documentName}${target ? ` with ${target}` : ""}`,
      REVOKE_PERMISSION: `${actor} revoked access${target ? ` for ${target}` : ""}`,
      SEND_NOTIFICATION: `${actor} sent an announcement`,
    };
    return messages[log.action] || activityAction(log.action);
  }

  // Backend đã có details tiếng Việt.
  if (log.details && String(log.details).trim()) {
    return escapeHtml(log.details);
  }

  const action = log.action || "";

  if (log.documentName) {
    return `${activityAction(action)}: ${escapeHtml(log.documentName)}`;
  }

  return activityAction(action);
}

function activityDocument(log) {
  if (log.documentName) {
    return `
      <div style="font-weight:700;color:#313b4b;">
        ${escapeHtml(log.documentName)}
      </div>
      <small style="color:#9aa3b1;">
        Document #${escapeHtml(log.documentId ?? "—")}
      </small>
    `;
  }

  if (log.documentId) {
    return `
      <span style="color:#697386;">
        Document #${escapeHtml(log.documentId)}
      </span>
    `;
  }

  return `<span style="color:#a0a8b5;">—</span>`;
}

function renderActivityLogs(logs) {
  const box = document.getElementById("activityContent");

  if (!box) return;

  if (!Array.isArray(logs) || !logs.length) {
    box.innerHTML = `
      <section class="panel empty-state">
        <div class="empty-icon">◉</div>
        <h2>Chưa có hoạt động</h2>
        <p>Chưa có nhật ký hoạt động nào được ghi nhận.</p>
      </section>
    `;

    return;
  }

  box.innerHTML = `
    <section class="panel table-panel">

      <div class="panel-head">
        <div>
          <h2>Nhật ký hoạt động</h2>
          <p>${logs.length.toLocaleString(currentSettings.language === "en" ? "en-US" : "vi-VN")} ${currentSettings.language === "en" ? "recorded activities" : "hoạt động được ghi nhận"}</p>
        </div>

        <button
          class="link-btn"
          id="refreshActivity"
          type="button">
          ↻ Làm mới
        </button>
      </div>

      <div class="table-wrap">

        <table style="min-width:1000px;">

          <thead>
            <tr>
              <th>THỜI GIAN</th>
              <th>NGƯỜI THỰC HIỆN</th>
              <th>HÀNH ĐỘNG</th>
              <th>TÀI LIỆU</th>
              <th>CHI TIẾT</th>
            </tr>
          </thead>

          <tbody>

            ${logs
              .map((log) => {
                const action = String(log.action || "").toUpperCase();

                return `
                <tr>

                  <td>
                    <span style="white-space:nowrap;">
                      ${formatActivityTime(log.createdAt)}
                    </span>
                  </td>

                  <td>
                    <div class="owner">

                      <span class="avatar">
                        ${initials(log.fullName || log.username || "SYSTEM")}
                      </span>

                      <div>
                        <strong>
                          ${escapeHtml(log.fullName || log.username || "SYSTEM")}
                        </strong>

                        ${
                          log.targetUsername
                            ? `
                              <small style="display:block;color:#9099a8;margin-top:3px;">
                                → ${escapeHtml(log.targetFullName || log.targetUsername)}
                              </small>
                            `
                            : ""
                        }

                      </div>
                    </div>
                  </td>

                  <td>
                    <span
                      class="status active"
                      style="white-space:nowrap;">
                      ${activityIcon(action)}
                      ${escapeHtml(activityAction(action))}
                    </span>
                  </td>

                  <td>
                    ${activityDocument(log)}
                  </td>

                  <td style="max-width:350px;">
                    <span style="color:#697386;">
                      ${activityDetails(log)}
                    </span>
                  </td>

                </tr>
              `;
              })
              .join("")}

          </tbody>

        </table>

      </div>

    </section>
  `;

  const refreshButton = document.getElementById("refreshActivity");

  if (refreshButton) {
    refreshButton.onclick = loadActivityLogs;
  }
}

async function loadActivityLogs() {
  const box = document.getElementById("activityContent");

  if (!box) return;

  box.innerHTML = `
    <section class="panel empty-state">
      <div class="empty-icon">◌</div>
      <h2>Đang tải nhật ký...</h2>
      <p>Đang lấy dữ liệu hoạt động từ hệ thống.</p>
    </section>
  `;

  try {
    const data = await api("/audit-logs");

    renderActivityLogs(Array.isArray(data) ? data : []);
  } catch (error) {
    console.error("Activity Log error:", error);

    box.innerHTML = `
      <section class="panel empty-state">

        <div class="empty-icon">!</div>

        <h2>Không thể tải nhật ký</h2>

        <p>
          ${escapeHtml(error.message)}
        </p>

        <button
          class="primary-btn"
          type="button"
          onclick="loadActivityLogs()">
          ↻ Thử lại
        </button>

      </section>
    `;
  }
}
function renderTables() {
  const recent = document.getElementById("recentTable"),
    table = document.getElementById("documentTable");
  if (recent)
    recent.innerHTML =
      docs
        .slice(0, 5)
        .map((d) => row(d))
        .join("") ||
      `<tr><td colspan="6" style="text-align:center;padding:40px;color:#9099a8">Chưa có tài liệu.</td></tr>`;
  filterDocs();
}

const pages = document.querySelectorAll(".page");
async function showPage(page) {

  if (page === "users" && currentUser?.role !== "ADMIN") {
    showToast("Bạn không có quyền quản trị người dùng.");
    return;
  }

  pages.forEach((p) => p.classList.add("hidden"));
  activePage = page;
  selectedDocumentIds.clear(); selectedTrashIds.clear();
  document.querySelectorAll(".document-select").forEach(input => input.checked = false);
  updateBulkToolbar();
  filterDocs();

  const data = pageNames[page] || pageNames.dashboard;

  document.getElementById("pageTitle").textContent = data[0];

  // =========================
  // DASHBOARD
  // =========================

  if (page === "dashboard") {
    document.getElementById("dashboardPage").classList.remove("hidden");

    await loadDashboard();
  }

  // =========================
  // DOCUMENTS
  // =========================
  else if (page === "tasks") {
    document.getElementById("workTasksPage").classList.remove("hidden");
    await window.loadWorkTasks();
  }
  else if (page === "trash") {
    document.getElementById("trashPage").classList.remove("hidden");
    await loadTrash();
  }
  else if (page === "documents") {
    document.getElementById("documentsPage").classList.remove("hidden");
    await loadDocuments();
  } else if (["shared", "recent", "favorites"].includes(page)) {
    await loadCollection(page);
  }

  // =========================
  // USERS
  // =========================
  else if (page === "profile") {
    renderProfile();
    document.getElementById("profilePage").classList.remove("hidden");
  } else if (page === "users") {
    document.getElementById("usersPage").classList.remove("hidden");

    await loadUsers();
  }

  // =========================
  // ACTIVITY LOG
  // =========================
  else if (page === "activity") {
    document.getElementById("activityPage").classList.remove("hidden");
    await loadActivityLogs();
  } else if (page === "settings") {
    document.getElementById("settingsPage").classList.remove("hidden");
    renderSettings();
  }

  // =========================
  // OTHER PAGES
  // =========================
  else {
    document.getElementById("genericPage").classList.remove("hidden");

    document.getElementById("genericEyebrow").textContent = data[1];

    document.getElementById("genericTitle").textContent = data[0];

    document.getElementById("genericDesc").textContent = data[2];

    document.getElementById("emptyTitle").textContent = data[0];
  }

  // Active menu
  document.querySelectorAll(".nav-item").forEach((a) => {
    a.classList.toggle("active", a.dataset.page === page);
  });

  document.querySelector(".main").scrollTo({
    top: 0,
    behavior: "smooth",
  });

  document.querySelector(".sidebar").classList.remove("open");
}
document.querySelectorAll("[data-page]").forEach((el) =>
  el.addEventListener("click", (e) => {
    e.preventDefault();
    showPage(el.dataset.page);
  }),
);

function setProfileMessage(id, message, success = false) {
  const element = document.getElementById(id);
  element.textContent = message;
  element.classList.toggle("hidden", !message);
  element.classList.toggle("success", success);
}

function renderProfile() {
  if (!currentUser) return;
  for (const [field, key] of Object.entries({
    Username: "username",
    FullName: "fullName",
    Email: "email",
    Department: "department",
  })) {
    const value = key === "fullName" ? (profileName(currentUser) === "Chưa cập nhật" ? "" : profileName(currentUser)) : key === "email" ? contactEmail(currentUser) : currentUser[key];
    document.getElementById(`profile${field}`).value = value || "";
    document.getElementById(`profile${field}`).placeholder = "Chưa cập nhật";
  }
  document.getElementById("profileRole").textContent = currentUser.role;
  document.getElementById("profileStatus").textContent =
    currentUser.status === "ACTIVE" ? "Đang hoạt động" : "Đã khóa";
  setProfileMessage("profileMessage", "");
}

document.getElementById("topAvatar").addEventListener("click", () => {
  if (currentUser) showPage("profile");
});
document.getElementById("resetProfile").addEventListener("click", renderProfile);

document.getElementById("displayNameForm").onsubmit = async (event) => {
  event.preventDefault();
  if (!currentUser) return;
  const button = document.getElementById("saveDisplayName");
  if (button.disabled) return;
  const fullName = document.getElementById("displayNameInput").value.trim();
  const message = document.getElementById("displayNameMessage");
  if (!fullName || ["Chưa cập nhật", "Người dùng CloudDocs"].includes(fullName)) {
    message.textContent = "Vui lòng nhập họ và tên của bạn.";
    return;
  }
  const sessionToken = getToken();
  button.disabled = true; button.textContent = "Đang lưu…"; message.textContent = "";
  try {
    const updated = await api("/auth/me/display-name", {method:"PUT", body:JSON.stringify({fullName})});
    if (sessionToken !== getToken()) return;
    currentUser = updated;
    updateUserUI();
    renderProfile();
    document.getElementById("displayNameInput").value = "";
  } catch (error) {
    if (sessionToken === getToken()) message.textContent = error.message;
  } finally {
    button.disabled = false; button.textContent = "Lưu tên";
  }
};
document.getElementById("profileForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!currentUser) return;
  const button = document.getElementById("saveProfile");
  if (button.disabled) return;
  const payload = {
    fullName: document.getElementById("profileFullName").value.trim(),
    email: document.getElementById("profileEmail").value.trim(),
    department: document.getElementById("profileDepartment").value.trim(),
  };
  if (Object.values(payload).some((value) => !value)) {
    setProfileMessage("profileMessage", "Vui lòng nhập đầy đủ họ tên, email và phòng ban.");
    return;
  }
  const sessionToken = getToken();
  button.disabled = true;
  document.getElementById("resetProfile").disabled = true;
  button.textContent = "Đang lưu...";
  setProfileMessage("profileMessage", "");
  try {
    const updated = await api("/auth/me", { method: "PUT", body: JSON.stringify(payload) });
    if (getToken() !== sessionToken) return;
    currentUser = updated;
    updateUserUI();
    await loadDepartments();
    renderProfile();
    setProfileMessage("profileMessage", "Đã cập nhật hồ sơ thành công.", true);
  } catch (error) {
    if (getToken() === sessionToken) setProfileMessage("profileMessage", error.message);
  } finally {
    button.disabled = false;
    document.getElementById("resetProfile").disabled = false;
    button.textContent = "Lưu thay đổi";
  }
});

async function loadUsers() {
  const sessionToken = getToken();
  const tbody = document.getElementById("userTable");
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:30px">Đang tải...</td></tr>`;
  try {
    const users = await api("/users");
    if (sessionToken !== getToken()) return;
    tbody.innerHTML =
      users
        .map(
          (u) => `<tr>
      <td>${ownerCell({ owner: profileName(u) })}<small style="display:block;color:#8b95a7;margin-left:40px">ID: ${escapeHtml(u.id)}</small></td>
      <td>${escapeHtml(contactEmail(u) || "Chưa cập nhật")}</td><td>${escapeHtml(u.department)}</td>
      <td><span class="role-badge ${u.role.toLowerCase()}">${u.role}</span></td>
      <td><span class="status-badge ${u.status.toLowerCase()}">${u.status}</span></td>
      <td>${formatUpdated(u.createdAt)}</td>
      <td><div class="user-actions">
        <select onchange="changeUserRole(${u.id},this.value)"><option value="USER" ${u.role === "USER" ? "selected" : ""}>USER</option><option value="ADMIN" ${u.role === "ADMIN" ? "selected" : ""}>ADMIN</option></select>
        <select onchange="changeUserStatus(${u.id},this.value)">${u.status === "PENDING_APPROVAL" ? '<option value="PENDING_APPROVAL" selected disabled>PENDING_APPROVAL</option>' : ""}<option value="ACTIVE" ${u.status === "ACTIVE" ? "selected" : ""}>ACTIVE</option><option value="LOCKED" ${u.status === "LOCKED" ? "selected" : ""}>LOCKED</option></select>
        <button class="danger-btn" onclick="deleteUser(${u.id})" title="Xóa">Xóa</button>
      </div></td></tr>`,
        )
        .join("") ||
      `<tr><td colspan="7" style="text-align:center;padding:30px">Chưa có người dùng.</td></tr>`;
  } catch (error) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:30px;color:#c53030">${escapeHtml(error.message)}</td></tr>`;
  }
}
window.changeUserRole = async (id, role) => {
  try {
    await api(`/users/${id}/role`, { method: "PATCH", body: JSON.stringify({ role }) });
    currentUser = await api("/auth/me");
    updateUserUI();
    showToast("Đã cập nhật quyền.");
    await loadUsers();
  } catch (e) {
    showToast(`Không thể đổi quyền: ${e.message}`);
    await loadUsers();
  }
};
window.changeUserStatus = async (id, status) => {
  try {
    await api(`/users/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
    currentUser = await api("/auth/me");
    updateUserUI();
    showToast("Đã cập nhật trạng thái.");
    await loadUsers();
  } catch (e) {
    showToast(`Không thể đổi trạng thái: ${e.message}`);
    await loadUsers();
  }
};
window.deleteUser = async (id) => {
  if (!confirm("Xóa tài khoản này? Hành động không thể hoàn tác.")) return;
  try {
    await api(`/users/${id}`, { method: "DELETE" });
    showToast("Đã xóa tài khoản.");
    await loadUsers();
  } catch (e) {
    showToast(`Không thể xóa: ${e.message}`);
  }
};
document.getElementById("refreshUsers")?.addEventListener("click", loadUsers);

let shareDocumentId = null;
let shareableUsers = [];

async function loadShareModal(documentId, batchIds = []) {
  shareableUsers = [];
  bulkShareIds = batchIds;
  shareDocumentId = documentId;
  const doc = docs.find((x) => Number(x.id) === Number(documentId));
  document.getElementById("shareDocumentName").textContent = doc
    ? `${doc.name} · Document #${doc.id}`
    : `Document #${documentId}`;
  if (batchIds.length) document.getElementById("shareDocumentName").textContent = `Chia sẻ ${batchIds.length} tài liệu đã chọn`;
  document.getElementById("shareUserSearch").value = "";
  document.getElementById("sharePermission").value = "VIEW";
  document.getElementById("shareUserSelect").innerHTML = `<option disabled>Đang tải...</option>`;
  document.getElementById("permissionList").innerHTML =
    `<div class="share-empty">Đang tải...</div>`;
  document.getElementById("shareCount").textContent = "0";
  document.getElementById("shareModal").classList.remove("hidden");
  try {
    const [users, permissions] = await Promise.all([
      batchIds.length
        ? Promise.all(batchIds.map(id => api(`/documents/${id}/permissions/users`))).then(lists => lists[0].filter(user => lists.every(list => list.some(item => Number(item.id) === Number(user.id)))))
        : api(`/documents/${documentId}/permissions/users`),
      api(`/documents/${documentId}/permissions`),
    ]);
    shareableUsers = Array.isArray(users) ? users : [];
    renderShareUsers();
    if (batchIds.length) {
      document.getElementById("permissionList").textContent = `Quyền đã chọn sẽ áp dụng cho ${batchIds.length} tài liệu. Danh sách người nhận chỉ gồm tài khoản hợp lệ cho tất cả tài liệu.`;
      document.getElementById("shareCount").textContent = String(batchIds.length);
    } else renderPermissions(Array.isArray(permissions) ? permissions : []);
  } catch (error) {
    document.getElementById("shareUserSelect").innerHTML =
      `<option disabled>${escapeHtml(error.message)}</option>`;
    document.getElementById("permissionList").innerHTML =
      `<div class="share-error">${escapeHtml(error.message)}</div>`;
  }
}
window.openShareModal = loadShareModal;

function renderShareUsers() {
  const q = (document.getElementById("shareUserSearch").value || "").trim().toLowerCase();
  const selected = document.getElementById("shareUserSelect");
  const filtered = shareableUsers.filter((u) =>
    `${u.fullName} ${u.username} ${u.email} ${u.department}`.toLowerCase().includes(q),
  );
  selected.innerHTML = filtered.length
    ? filtered
        .map(
          (u) =>
            `<option value="${u.id}">${escapeHtml(profileName(u))} (#${escapeHtml(u.id)}) · ${escapeHtml(u.department || "")}</option>`,
        )
        .join("")
    : `<option disabled>Không tìm thấy người dùng</option>`;
}

function renderPermissions(permissions) {
  const box = document.getElementById("permissionList");
  document.getElementById("shareCount").textContent = String(permissions.length);
  if (!permissions.length) {
    box.innerHTML = `<div class="share-empty">Chưa chia sẻ cho ai.</div>`;
    return;
  }
  box.innerHTML = permissions
    .map(
      (p) => `<div class="permission-row">
    <div class="permission-user"><span class="avatar">${profileName(p) === "Chưa cập nhật" ? "CD" : initials(profileName(p))}</span><div><strong>${escapeHtml(profileName(p))}</strong><small>${escapeHtml(contactEmail(p) || "Chưa cập nhật")}</small></div></div>
    <div class="permission-actions"><span class="permission-badge ${escapeHtml(String(p.permission).toLowerCase())}">${escapeHtml(p.permission)}</span><button class="danger-btn" onclick="revokePermission(${p.userId})">Thu hồi</button></div>
  </div>`,
    )
    .join("");
}

window.revokePermission = async function (userId) {
  if (!shareDocumentId || !confirm("Thu hồi quyền của người dùng này?")) return;
  try {
    await api(`/documents/${shareDocumentId}/permissions/${userId}`, { method: "DELETE" });
    showToast("Đã thu hồi quyền.");
    const permissions = await api(`/documents/${shareDocumentId}/permissions`);
    renderPermissions(Array.isArray(permissions) ? permissions : []);
  } catch (error) {
    showToast(`Thu hồi thất bại: ${error.message}`);
  }
};

document.getElementById("shareUserSearch").addEventListener("input", renderShareUsers);
document.getElementById("grantPermission").addEventListener("click", async () => {
  if (!shareDocumentId) return;
  const userId = document.getElementById("shareUserSelect").value;
  const permission = document.getElementById("sharePermission").value;
  if (!userId) {
    showToast("Hãy chọn người dùng.");
    return;
  }
  const button = document.getElementById("grantPermission");
  button.disabled = true;
  button.textContent = "Đang cấp quyền...";
  try {
    if (bulkShareIds.length) {
      const ids = [...bulkShareIds];
      const result = await runDocumentBatch(ids, id => api(`/documents/${id}/permissions/${userId}`, {
        method: "PUT", body: JSON.stringify({ permission }),
      }), "Chia sẻ");
      bulkShareIds = result.failed;
      if (!result.failed.length) document.getElementById("shareModal").classList.add("hidden");
      else document.getElementById("shareDocumentName").textContent = `Còn ${result.failed.length} tài liệu chưa chia sẻ; có thể thử lại`;
    } else {
      await api(`/documents/${shareDocumentId}/permissions/${userId}`, {
        method: "PUT", body: JSON.stringify({ permission }),
      });
      showToast("Đã cấp/cập nhật quyền.");
      await loadShareModal(shareDocumentId);
    }
  } catch (error) {
    showToast(`Không thể cấp quyền: ${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = "Cấp quyền";
  }
});
document.getElementById("closeShare").onclick = () =>
  document.getElementById("shareModal").classList.add("hidden");
document.getElementById("shareModal").addEventListener("click", (e) => {
  if (e.target.id === "shareModal") e.currentTarget.classList.add("hidden");
});

let editDocumentId = null;

window.downloadDocument = async function (id) {
  let objectUrl = null;

  try {
    const token = getToken();

    if (!token) {
      throw new Error("Vui lòng đăng nhập lại.");
    }

    const data = await api(`/documents/${id}/download-url`);
    requireSession(token);
    const downloadUrl = data.downloadUrl || data.url;

    if (!downloadUrl) {
      throw new Error("Không tìm thấy URL tải xuống.");
    }

    // 2. Tải dữ liệu file từ S3
    const s3Response = await fetch(downloadUrl);

    if (!s3Response.ok) {
      throw new Error(`S3 trả về HTTP ${s3Response.status}`);
    }

    const blob = await s3Response.blob();

    requireSession(token);

    // 3. Xác định tên file
    const doc = docs.find((x) => Number(x.id) === Number(id));
    const fileName = doc?.fileName || doc?.name || `document-${id}`;

    // 4. Tạo liên kết tải file về máy
    objectUrl = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = fileName;
    a.style.display = "none";

    document.body.appendChild(a);
    a.click();
    a.remove();

    console.log("Tải file thành công:", {
      id,
      fileName,
      size: blob.size,
      type: blob.type,
    });

    showToast(`Đã tải xuống: ${fileName}`);
    return true;
  } catch (error) {
    console.error("Lỗi tải tài liệu:", error);
    showToast(`Không thể tải tài liệu: ${error.message}`);
    return false;
  } finally {
    if (objectUrl) {
      setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
    }
  }
};
window.deleteDocument = async function (id) {
  const doc = docs.find((x) => Number(x.id) === Number(id));
  if (!doc) return;
  const ok = confirm(`Chuyển tài liệu "${doc.name}" vào thùng rác? Bạn có thể khôi phục lại sau.`);
  if (!ok) return;
  try {
    await api(`/documents/${id}`, { method: "DELETE" });
    showToast("Đã xóa tài liệu.");
    await loadDocuments();
    await loadDashboard();
    if (["shared", "recent", "favorites"].includes(activePage)) await loadCollection(activePage);
  } catch (error) {
    showToast(`Không thể xóa tài liệu: ${error.message}`);
  }
};

window.openEditModal = function (id) {
  const doc = docs.find((x) => Number(x.id) === Number(id));
  if (!doc) return;
  editDocumentId = id;
  document.getElementById("editDocumentName").textContent = `${doc.name} · Document #${doc.id}`;
  document.getElementById("editTitle").value = doc.name || "";
  document.getElementById("editDescription").value = doc.description || "";
  const dept = document.getElementById("editDepartment");
  if ([...dept.options].some((o) => o.value === doc.dept)) dept.value = doc.dept;
  else {
    const option = new Option(doc.dept, doc.dept);
    dept.add(option);
    dept.value = doc.dept;
  }
  document.getElementById("editStatus").value = (doc.status || "active").toUpperCase();
  document.getElementById("editModal").classList.remove("hidden");
};

async function saveEditedDocument() {
  if (!editDocumentId) return;
  const title = document.getElementById("editTitle").value.trim();
  const department = document.getElementById("editDepartment").value;
  const description = document.getElementById("editDescription").value.trim();
  const status = document.getElementById("editStatus").value;
  if (!title) {
    showToast("Tên tài liệu không được để trống.");
    return;
  }
  const button = document.getElementById("saveEdit");
  button.disabled = true;
  button.textContent = "Đang lưu...";
  try {
    await api(`/documents/${editDocumentId}`, {
      method: "PUT",
      body: JSON.stringify({ title, description, department, status }),
    });
    showToast("Đã cập nhật tài liệu.");
    document.getElementById("editModal").classList.add("hidden");
    editDocumentId = null;
    await loadDocuments();
    await loadDashboard();
    if (["shared", "recent", "favorites"].includes(activePage)) await loadCollection(activePage);
  } catch (error) {
    showToast(`Không thể chỉnh sửa: ${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = "Lưu thay đổi";
  }
}

document.getElementById("closeEdit").onclick = () => {
  editDocumentId = null;
  document.getElementById("editModal").classList.add("hidden");
};
document.getElementById("cancelEdit").onclick = () => {
  editDocumentId = null;
  document.getElementById("editModal").classList.add("hidden");
};
document.getElementById("saveEdit").onclick = saveEditedDocument;
document.getElementById("editModal").addEventListener("click", (e) => {
  if (e.target.id === "editModal") e.currentTarget.classList.add("hidden");
});

const modal = document.getElementById("uploadModal");
const openUpload = () => modal.classList.remove("hidden");
document.getElementById("openUpload").onclick = openUpload;
document.getElementById("openUpload2").onclick = openUpload;
document.getElementById("closeUpload").onclick = () => modal.classList.add("hidden");
document.getElementById("cancelUpload").onclick = () => modal.classList.add("hidden");
const fileInput = document.getElementById("fileInput"),
  selected = document.getElementById("selectedFiles");
fileInput.addEventListener("change", () => {
  selected.innerHTML = [...fileInput.files]
    .map(
      (f) =>
        `<div class="selected-file"><span>📄 ${escapeHtml(f.name)}</span><span>${formatSize(f.size)}</span></div>`,
    )
    .join("");
});
document.getElementById("confirmUpload").onclick = async () => {
  if (!fileInput.files.length) {
    showToast("Hãy chọn ít nhất một file.");
    return;
  }
  const department = document.getElementById("uploadDepartment").value,
    description = document.getElementById("uploadDescription").value.trim(),
    button = document.getElementById("confirmUpload");
  if (button.disabled) return;
  const uploadToken = getToken();
  const files = [...fileInput.files];
  button.disabled = true;
  button.textContent = "Đang lưu...";
  try {
    const quota = await loadStorageUsage();
    requireSession(uploadToken);
    if (quota) {
      if (files.some((file) => file.size > quota.maxFileBytes)) {
        throw new Error(`File vượt giới hạn ${formatSize(quota.maxFileBytes)}/file.`);
      }
      if (files.reduce((sum, file) => sum + file.size, 0) > quota.remainingBytes) {
        throw new Error(
          "Không đủ dung lượng cho các file đã chọn. Hãy xóa tài liệu không cần thiết.",
        );
      }
    }
    for (const file of files) {
      requireSession(uploadToken);
      const dot = file.name.lastIndexOf("."),
        ext = dot > -1 ? file.name.slice(dot + 1).toUpperCase() : "FILE";
      // 1. Tạo document trong MySQL
      const created = await api("/documents", {
        method: "POST",
        body: JSON.stringify({
          title: file.name,
          description,
          fileName: file.name,
          fileType: ext,
          fileSize: file.size,
          department,
        }),
      });

      // 2. Xin Presigned Upload URL từ backend
      requireSession(uploadToken);
      const uploadInfo = await api(`/documents/${created.id}/upload-url`, {
        method: "POST",
      });



      // 3. Upload file trực tiếp lên S3
      requireSession(uploadToken);
      const s3Response = await fetch(uploadInfo.uploadUrl, {
        method: "PUT",
        body: file,
      });

      if (!s3Response.ok) {
        throw new Error(`Upload lên S3 thất bại: HTTP ${s3Response.status}`);
      }

      // 4. Báo backend xác nhận upload hoàn tất
      requireSession(uploadToken);
      await api(`/documents/${created.id}/upload-complete`, {
        method: "POST",
      });

      console.log("Upload S3 thành công:", created.id);
    }
    showToast(`${files.length} tài liệu đã được lưu thành công.`);
    modal.classList.add("hidden");
    fileInput.value = "";
    selected.innerHTML = "";
    document.getElementById("uploadDescription").value = "";
    await loadDocuments();
    await loadDashboard();
    showPage("documents");
  } catch (error) {
    console.error(error);
    showToast(`Lưu thất bại: ${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = "Lưu tài liệu";
    await loadStorageUsage();
  }
};
function showToast(msg) {
  const t = document.getElementById("toast");
  t.querySelector("span").textContent = msg;
  t.classList.remove("hidden");
  clearTimeout(window.toastTimer);
  window.toastTimer = setTimeout(() => t.classList.add("hidden"), 3500);
}
const search = document.getElementById("documentSearch"),
  typeFilter = document.getElementById("typeFilter"),
  departmentFilter = document.getElementById("departmentFilter");
function filterDocs() {
  if (!document.getElementById("documentTable")) return;
  const q = (search.value || "").toLowerCase(),
    type = typeFilter.value,
    department = departmentFilter?.value || "";
  const filtered = docs.filter(
    (d) =>
      (!q || `${d.name} ${d.owner} ${d.dept}`.toLowerCase().includes(q)) &&
      (!type || d.type === type) &&
      (!department || d.dept.localeCompare(department, "vi", { sensitivity: "accent" }) === 0),
  );
  const visibleIds = new Set(filtered.map(d => Number(d.id)));
  selectedDocumentIds = new Set([...selectedDocumentIds].filter(id => visibleIds.has(id)));
  document.getElementById("documentTable").innerHTML = filtered.length
    ? filtered.map((d) => row(d, true)).join("")
    : `<tr><td colspan="8" style="text-align:center;padding:40px;color:#9099a8">Không tìm thấy tài liệu phù hợp.</td></tr>`;
  updateBulkToolbar();
}
search.addEventListener("input", () => { selectedDocumentIds.clear(); filterDocs(); updateBulkToolbar(); });
typeFilter.addEventListener("change", () => { selectedDocumentIds.clear(); filterDocs(); updateBulkToolbar(); });
if (departmentFilter) departmentFilter.addEventListener("change", () => { selectedDocumentIds.clear(); filterDocs(); updateBulkToolbar(); });
document.getElementById("globalSearch").addEventListener("keydown", (e) => {
  if (e.key === "Enter") {
    showPage("documents");
    search.value = e.target.value;
    filterDocs();
  }
});
document.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    document.getElementById("globalSearch").focus();
  }
});
document.getElementById("mobileMenu").onclick = () =>
  document.querySelector(".sidebar").classList.toggle("open");
document.querySelector(".dropzone").addEventListener("dragover", (e) => {
  e.preventDefault();
  e.currentTarget.style.borderColor = "#315bea";
});
document
  .querySelector(".dropzone")
  .addEventListener("dragleave", (e) => (e.currentTarget.style.borderColor = "#b9c5df"));
document.querySelector(".dropzone").addEventListener("drop", (e) => {
  e.preventDefault();
  e.currentTarget.style.borderColor = "#b9c5df";
  try {
    fileInput.files = e.dataTransfer.files;
    fileInput.dispatchEvent(new Event("change"));
  } catch {
    showToast("Trình duyệt không cho phép kéo thả trực tiếp. Hãy chọn file.");
  }
});

function notificationSeenKey() {
  return `clouddocs_notification_seen_${currentUser?.id || "guest"}`;
}
function renderNotifications() {
  const list = document.getElementById("notificationList");
  list.innerHTML = notifications.length
    ? notifications
        .map(
          (
            item,
          ) => `<div class="notification-entry"><button type="button" class="notification-item" data-no-translate onclick="openNotificationDetail(${Number(item.id)})">
    <strong>${escapeHtml(item.title)}</strong>
    <p>${escapeHtml(item.message)}</p>
    <small>${escapeHtml(item.createdBy || "ADMIN")} · ${escapeHtml(formatUpdated(item.createdAt))}</small>
  </button>${(item.canDelete ?? currentUser?.role === "ADMIN") ? `<button type="button" class="notification-delete" aria-label="Xóa thông báo" title="Xóa thông báo" onclick="deleteNotification(${Number(item.id)},event)">×</button>` : ""}</div>`,
        )
        .join("")
    : `<div class="notification-empty">Chưa có thông báo.</div>`;
  const seen = Number(localStorage.getItem(notificationSeenKey()) || 0);
  const latest = Number(notifications[0]?.id || 0);
  document
    .getElementById("notificationDot")
    .classList.toggle("hidden", !currentSettings.badge || latest <= seen);
}
async function loadNotifications() {
  if (!currentUser || !getToken() || !currentSettings.notifications) return;
  try {
    const data = await api("/notifications");
    notifications = Array.isArray(data) ? data : [];
    renderNotifications();
  } catch (error) {
    console.error(error);
  }
}
document.getElementById("notificationButton").addEventListener("click", async (event) => {
  event.stopPropagation();
  await loadNotifications();
  const popover = document.getElementById("notificationPopover");
  popover.classList.toggle("hidden");
  if (!popover.classList.contains("hidden")) {
    localStorage.setItem(notificationSeenKey(), String(notifications[0]?.id || 0));
    document.getElementById("notificationDot").classList.add("hidden");
  }
});
document
  .getElementById("notificationPopover")
  .addEventListener("click", (event) => event.stopPropagation());
document.addEventListener("click", () =>
  document.getElementById("notificationPopover").classList.add("hidden"),
);
const notificationDetailModal = document.getElementById("notificationDetailModal");
window.openNotificationDetail = function (id) {
  const item = notifications.find((value) => Number(value.id) === Number(id));
  if (!item) return;
  document.getElementById("notificationPopover").classList.add("hidden");
  document.getElementById("notificationDetailTitle").textContent = item.title;
  document.getElementById("notificationDetailMessage").textContent = item.message;
  document.getElementById("notificationDetailMeta").textContent =
    `${item.createdBy || "ADMIN"} · ${formatUpdated(item.createdAt)}`;
  const taskLink = document.getElementById("notificationTaskLink");
  taskLink.classList.toggle("hidden", !item.workTaskId);
  taskLink.onclick = item.workTaskId ? async () => { const token=getToken(); try { closeNotificationDetail(); await showPage("tasks"); requireSession(token); await window.openWorkTask(item.workTaskId); } catch(error) { if(token===getToken())showToast(error.message); } } : null;
  notificationDetailModal.classList.remove("hidden");
};
function closeNotificationDetail() {
  notificationDetailModal.classList.add("hidden");
}
document.getElementById("closeNotificationDetail").onclick = closeNotificationDetail;
document.getElementById("okayNotificationDetail").onclick = closeNotificationDetail;
notificationDetailModal.addEventListener("click", (event) => {
  if (event.target === notificationDetailModal) closeNotificationDetail();
});
const notificationModal = document.getElementById("notificationModal");
function closeNotificationComposer() {
  notificationModal.classList.add("hidden");
}
document.getElementById("openNotificationComposer").addEventListener("click", () => {
  document.getElementById("notificationPopover").classList.add("hidden");
  document.getElementById("notificationForm").reset();
  document.getElementById("notificationFormError").classList.add("hidden");
  notificationModal.classList.remove("hidden");
});
document.getElementById("closeNotificationModal").onclick = closeNotificationComposer;
document.getElementById("cancelNotification").onclick = closeNotificationComposer;
notificationModal.addEventListener("click", (event) => {
  if (event.target === notificationModal) closeNotificationComposer();
});
document.getElementById("notificationForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (currentUser?.role !== "ADMIN") return;
  const button = document.getElementById("sendNotification");
  const error = document.getElementById("notificationFormError");
  button.disabled = true;
  button.textContent = "Đang gửi...";
  error.classList.add("hidden");
  try {
    await api("/notifications", {
      method: "POST",
      body: JSON.stringify({
        title: document.getElementById("notificationTitle").value.trim(),
        message: document.getElementById("notificationMessage").value.trim(),
      }),
    });
    closeNotificationComposer();
    await loadNotifications();
    showToast("Đã gửi thông báo tới tất cả user.");
  } catch (err) {
    error.textContent = err.message;
    error.classList.remove("hidden");
  } finally {
    button.disabled = false;
    button.textContent = "Gửi tới tất cả user";
  }
});

document.querySelectorAll('input[name="theme"]').forEach((input) =>
  input.addEventListener("change", (event) => {
    currentSettings.theme = event.target.value;
    saveSettings();
  }),
);
document.getElementById("settingNotifications").addEventListener("change", (event) => {
  currentSettings.notifications = event.target.checked;
  saveSettings();
});
document.getElementById("settingNotificationBadge").addEventListener("change", (event) => {
  currentSettings.badge = event.target.checked;
  saveSettings();
});
document.getElementById("settingNotificationRefresh").addEventListener("change", (event) => {
  currentSettings.autoRefresh = event.target.checked;
  saveSettings();
});
document.getElementById("settingLanguage").addEventListener("change", (event) => {
  currentSettings.language = event.target.value;
  saveSettings();
  if (activePage === "dashboard") loadDashboard();
  if (activePage === "activity") loadActivityLogs();
});

new MutationObserver((mutations) => {
  for (const mutation of mutations) {
    mutation.addedNodes.forEach((node) => translateInterface(node));
  }
}).observe(document.body, { childList: true, subtree: true });

bootstrapAuth();

window.previewDocument = async function (id) {
  let objectUrl = null;

  try {
    const token = getToken();

    if (!token) {
      throw new Error("Vui lòng đăng nhập lại.");
    }

    const data = await api(`/documents/${id}/preview-url`);
    requireSession(token);
    const previewUrl = data.url || data.downloadUrl;

    if (!previewUrl) {
      throw new Error("Backend không trả về URL xem trước.");
    }

    // Bước 2: Đọc file trực tiếp từ S3
    const s3Response = await fetch(previewUrl);

    if (!s3Response.ok) {
      throw new Error(`S3 trả về HTTP ${s3Response.status}`);
    }

    const blob = await s3Response.blob();

    requireSession(token);

    // Bước 3: Xác định tên và loại file
    const doc = docs.find((x) => Number(x.id) === Number(id));
    const fileName = doc?.fileName || doc?.name || `document-${id}`;

    const extension = fileName.split(".").pop().toLowerCase();

    const extensionTypes = {
      pdf: "application/pdf",
      png: "image/png",
      jpg: "image/jpeg",
      jpeg: "image/jpeg",
      gif: "image/gif",
      webp: "image/webp",
      svg: "image/svg+xml",
      txt: "text/plain",
      csv: "text/csv",
      json: "application/json",
      xml: "text/xml",
      md: "text/markdown",
      log: "text/plain",
    };

    let contentType = s3Response.headers.get("Content-Type") || blob.type || "";

    // S3 có thể trả application/octet-stream,
    // nên dùng phần mở rộng để nhận diện định dạng.
    if (!contentType || contentType === "application/octet-stream") {
      contentType = extensionTypes[extension] || contentType;
    }

    let content = "";

    // Bước 4: Hiển thị nội dung xem trước
    if (contentType.includes("pdf")) {
      content = `
                <iframe title="Xem trước tài liệu PDF" referrerpolicy="no-referrer"
                    src="${(objectUrl = URL.createObjectURL(new Blob([blob], {type: contentType})))}"
                    style="
                        width:100%;
                        height:75vh;
                        border:0;
                        border-radius:8px;
                    ">
                </iframe>
            `;
    } else if (contentType.startsWith("image/")) {
      objectUrl = URL.createObjectURL(new Blob([blob], {type: contentType}));

      content = `
                <div style="
                    display:flex;
                    justify-content:center;
                    align-items:center;
                    height:75vh;
                    overflow:auto;
                ">
                    <img
                        src="${objectUrl}"
                        style="
                            max-width:100%;
                            max-height:100%;
                            object-fit:contain;
                        ">
                </div>
            `;
    } else if (contentType.startsWith("text/") || contentType.includes("application/json")) {
      const textContent = await blob.text();

      content = `
                <pre style="
                    white-space:pre-wrap;
                    text-align:left;
                    padding:20px;
                    max-height:75vh;
                    overflow:auto;
                ">${escapeHtml(textContent)}</pre>
            `;
    } else {
      content = `
                <div style="padding:40px;text-align:center;">
                    <h3>Không hỗ trợ Preview</h3>
                    <p>
                        Định dạng file này chưa thể xem trực tiếp
                        trên trình duyệt.
                    </p>
${["OWNER", "ADMIN", "EDIT", "DOWNLOAD"].includes(doc?.access) ? `<button class="table-action" onclick="downloadDocument(${id})">Tải xuống</button>` : ""}
                </div>
            `;
    }

    requireSession(token);
    openPreviewModal(content, objectUrl);
    objectUrl = null;

    console.log("Preview từ S3 thành công:", {
      id,
      fileName,
      contentType,
      size: blob.size,
    });
  } catch (error) {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    console.error("Lỗi Preview từ S3:", error);

    showToast(`Không thể xem trước tài liệu: ${error.message}`);
  }
};

let previewObjectUrl = null;
function openPreviewModal(content, objectUrl = null) {
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
  previewObjectUrl = objectUrl;
  const oldModal = document.getElementById("previewModal");

  if (oldModal) {
    oldModal.remove();
  }

  const modal = document.createElement("div");

  modal.id = "previewModal";

  modal.innerHTML = `
        <div class="preview-overlay" onclick="closePreviewModal(event)">
            <div class="preview-container" onclick="event.stopPropagation()">

                <div class="preview-header">
                    <div>
                        <strong>Preview tài liệu</strong>
                    </div>

                    <button
                        class="preview-close"
                        onclick="closePreviewModal()">
                        ✕
                    </button>
                </div>

                <div class="preview-body">
                    ${content}
                </div>

            </div>
        </div>
    `;

  document.body.appendChild(modal);
}

window.closePreviewModal = function (event) {
  if (event && event.target !== event.currentTarget) {
    return;
  }

  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
  previewObjectUrl = null;
  const modal = document.getElementById("previewModal");

  if (modal) {
    modal.remove();
  }
};


// Selection is scoped to the visible table; each server request checks access independently.
window.selectDocument = function(id, checked) {
  checked ? selectedDocumentIds.add(Number(id)) : selectedDocumentIds.delete(Number(id));
  updateBulkToolbar();
};
function updateBulkToolbar() {
  const count = document.getElementById("documentSelectionCount");
  if (count) count.textContent = "Đã chọn " + selectedDocumentIds.size;
  const selected = docs.filter(d => selectedDocumentIds.has(Number(d.id)));
  const manage = selected.length > 0 && selected.every(d => ["OWNER", "ADMIN"].includes(d.access));
  for (const id of ["bulkDelete", "bulkShare"]) document.getElementById(id).disabled = bulkBusy || !manage;
  document.getElementById("bulkDownload").disabled = bulkBusy || !selected.length || !selected.every(d => ["OWNER", "ADMIN", "EDIT", "DOWNLOAD"].includes(d.access));
  const visible = [...document.querySelectorAll("#documentTable .document-select")];
  const all = document.getElementById("selectAllDocuments");
  all.checked = visible.length > 0 && visible.every(input => selectedDocumentIds.has(Number(input.dataset.documentId)));
  all.indeterminate = !all.checked && visible.some(input => selectedDocumentIds.has(Number(input.dataset.documentId)));
}
window.selectAllDocuments = function(checked) {
  document.querySelectorAll("#documentTable .document-select").forEach(input => {
    input.checked = checked; const id = Number(input.dataset.documentId);
    checked ? selectedDocumentIds.add(id) : selectedDocumentIds.delete(id);
  });
  updateBulkToolbar();
};
async function runDocumentBatch(ids, action, label) {
  const failed = []; let firstError = "";
  const token = getToken();
  for (const id of ids) {
    try {
      if (token !== getToken()) throw new Error("Phiên đăng nhập đã thay đổi; đã dừng thao tác.");
      if (await action(id) === false) throw new Error("Tải xuống thất bại"); }
    catch (error) { failed.push(id); firstError ||= error.message; }
  }
  showToast(label + ": " + (ids.length - failed.length) + "/" + ids.length + " thành công" + (failed.length ? ". " + failed.length + " thất bại: " + firstError : ""));
  return { failed };
}
window.bulkDocuments = async function(action) {
  if (bulkBusy || !selectedDocumentIds.size) return;
  const ids = [...selectedDocumentIds];
  const chosen = docs.filter(d => ids.includes(Number(d.id)));
  const allowed = action === "download" ? ["OWNER", "ADMIN", "EDIT", "DOWNLOAD"] : ["OWNER", "ADMIN"];
  if (!chosen.length || !chosen.every(d => allowed.includes(d.access))) return showToast("Có tài liệu bạn không có quyền thực hiện thao tác này.");
  if (action === "share") return loadShareModal(ids[0], ids);
  if (action === "delete" && !confirm("Chuyển " + ids.length + " tài liệu vào thùng rác?")) return;
  bulkBusy = true; updateBulkToolbar();
  try {
    const result = await runDocumentBatch(ids, id => action === "download" ? downloadDocument(id) : api(`/documents/${id}`, {method: "DELETE"}), action === "download" ? "Tải xuống" : "Chuyển vào thùng rác");
    if (action === "delete") await loadDocuments();
    selectedDocumentIds = new Set(result.failed);
    filterDocs();
  } finally { bulkBusy = false; updateBulkToolbar(); }
};
async function loadTrash() {
  const token = getToken();
  document.getElementById("trashTable").innerHTML = '<tr><td colspan="5">Đang tải...</td></tr>';
  try {
    const data = await api("/documents/trash");
    if (token !== getToken()) return;
    trashDocuments = data.map(normalize);
    selectedTrashIds = new Set([...selectedTrashIds].filter(id => trashDocuments.some(d => Number(d.id) === id)));
    renderTrash();
  } catch (error) {
    trashDocuments = []; selectedTrashIds.clear(); renderTrash();
    document.getElementById("trashTable").innerHTML = '<tr><td colspan="5">' + escapeHtml(error.message) + '</td></tr>';
  }
}
function renderTrash() {
  document.getElementById("trashTable").innerHTML = trashDocuments.map(d => `<tr>
    <td><input type="checkbox" aria-label="Chọn ${escapeHtml(d.name)}" ${selectedTrashIds.has(Number(d.id)) ? "checked" : ""} onchange="selectTrash(${d.id},this.checked)"></td>
    <td data-no-translate>${escapeHtml(d.name)}</td><td>${escapeHtml(d.size)}</td><td>${escapeHtml(formatUpdated(d.updated))}</td>
    <td class="table-actions"><button class="secondary-btn" onclick="trashAction('restore',[${d.id}])">Khôi phục</button><button class="danger-btn" onclick="trashAction('purge',[${d.id}])">Xóa vĩnh viễn</button></td></tr>`).join("") || '<tr><td colspan="5" class="trash-empty">Thùng rác trống.</td></tr>';
  document.getElementById("trashSelectionCount").textContent = "Đã chọn " + selectedTrashIds.size;
  for (const id of ["restoreSelected", "purgeSelected"]) document.getElementById(id).disabled = bulkBusy || !selectedTrashIds.size;
  const all = document.getElementById("selectAllTrash");
  all.checked = trashDocuments.length > 0 && selectedTrashIds.size === trashDocuments.length;
  all.indeterminate = selectedTrashIds.size > 0 && !all.checked;
}
window.selectTrash = function(id, checked) {
  checked ? selectedTrashIds.add(Number(id)) : selectedTrashIds.delete(Number(id)); renderTrash();
};
window.selectAllTrash = function(checked) {
  selectedTrashIds = new Set(checked ? trashDocuments.map(d => Number(d.id)) : []); renderTrash();
};
window.trashAction = async function(action, ids = [...selectedTrashIds]) {
  if (bulkBusy || !ids.length) return;
  if (action === "purge" && !confirm("Xóa vĩnh viễn " + ids.length + " tài liệu? Không thể khôi phục file sau thao tác này.")) return;
  bulkBusy = true; renderTrash();
  try {
    const result = await runDocumentBatch(ids, id => api(action === "restore" ? `/documents/${id}/restore` : `/documents/${id}/permanent`, {method: action === "restore" ? "POST" : "DELETE"}), action === "restore" ? "Khôi phục" : "Xóa vĩnh viễn");
    selectedTrashIds = new Set(result.failed);
    await loadDocuments(); await loadTrash();
  } finally { bulkBusy = false; renderTrash(); }
};
window.deleteNotification = async function(id, event) {
  event?.stopPropagation();
  const item = notifications.find(value => Number(value.id) === Number(id));
  if (!(item?.canDelete ?? currentUser?.role === "ADMIN")) return;
  if (!confirm(item?.workTaskId ? "Xóa thông báo này khỏi danh sách của bạn?" : "Xóa thông báo này khỏi hệ thống cho tất cả người dùng?")) return;
  try { await api(`/notifications/${id}`, {method: "DELETE"}); await loadNotifications(); showToast("Đã xóa thông báo."); }
  catch (error) { showToast("Không thể xóa thông báo: " + error.message); }
};
