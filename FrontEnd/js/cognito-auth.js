/* Cognito public client: Authorization Code + PKCE. No local token issuer. */
window.CognitoAuth = (() => {
  const SESSION = "clouddocs_cognito_session";
  const TRANSACTION = "clouddocs_cognito_transaction";
  const MAX_AGE = 10 * 60 * 1000;
  const base64url = bytes => btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const random = () => base64url(crypto.getRandomValues(new Uint8Array(32)));
  function config() {
    const c = window.COGNITO_CONFIG;
    if (!c?.clientId || !c?.domain) throw new Error("Chưa cấu hình đăng nhập. Hãy kiểm tra cấu hình Cognito.");
    const domain = new URL(c.domain);
    if (domain.protocol !== "https:" || domain.pathname !== "/" || domain.search || domain.hash || domain.username)
      throw new Error("Domain Cognito không hợp lệ.");
    for (const uri of [c.redirectUri, c.logoutUri]) {
      const url = new URL(uri);
      if (url.origin !== location.origin || url.search || url.hash)
        throw new Error("Callback và logout URL phải khớp địa chỉ website hiện tại.");
    }
    return c;
  }
  function clear() { localStorage.removeItem(SESSION); }
  function token() {
    try {
      const s = JSON.parse(localStorage.getItem(SESSION));
      if (s && s.expiresAt > Date.now()) return s.accessToken;
    } catch {}
    clear(); return null;
  }
  async function login() {
    const c = config();
    const verifier = random(), state = random();
    const challenge = base64url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))));
    sessionStorage.setItem(TRANSACTION, JSON.stringify({verifier, state, createdAt: Date.now(), redirectUri: c.redirectUri}));
    const url = new URL("/oauth2/authorize", c.domain);
    url.search = new URLSearchParams({response_type: "code", client_id: c.clientId,
      redirect_uri: c.redirectUri, scope: "openid", state,
      code_challenge_method: "S256", code_challenge: challenge});
    location.assign(url.href);
  }
  async function callback() {
    const url = new URL(location.href);
    if (!["code", "state", "error", "error_description"].some(k => url.searchParams.has(k))) return false;
    const params = new URLSearchParams(url.search);
    // Consume the transaction and URL even for invalid/error callbacks; never restore an old session.
    const raw = sessionStorage.getItem(TRANSACTION);
    sessionStorage.removeItem(TRANSACTION);
    clear();
    for (const key of ["code", "state", "error", "error_description", "error_uri", "session_state"]) url.searchParams.delete(key);
    history.replaceState(null, "", url.pathname + url.search + url.hash);
    let transaction;
    try { transaction = JSON.parse(raw); } catch {}
    if (!transaction || params.getAll("state").length !== 1 || params.get("state") !== transaction.state
        || !transaction.verifier || Date.now() - transaction.createdAt > MAX_AGE
        || !Number.isFinite(transaction.createdAt) || transaction.createdAt > Date.now())
      throw new Error("Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    if (params.has("error")) throw new Error("Đăng nhập chưa hoàn tất. Vui lòng thử lại.");
    if (params.getAll("code").length !== 1 || !params.get("code")) throw new Error("Callback thiếu mã đăng nhập.");
    const c = config();
    if (transaction.redirectUri !== c.redirectUri) throw new Error("Callback không khớp giao dịch đăng nhập.");
    const response = await fetch(new URL("/oauth2/token", c.domain), {method: "POST",
      headers: {"Content-Type": "application/x-www-form-urlencoded"},
      body: new URLSearchParams({grant_type: "authorization_code", client_id: c.clientId,
        code: params.get("code"), redirect_uri: c.redirectUri, code_verifier: transaction.verifier})});
    if (!response.ok) throw new Error("Không thể hoàn tất đăng nhập. Vui lòng thử lại.");
    const data = await response.json();
    if (!data.access_token || data.token_type?.toLowerCase() !== "bearer" || !Number.isFinite(data.expires_in) || data.expires_in <= 0)
      throw new Error("Phản hồi đăng nhập không hợp lệ.");
    localStorage.setItem(SESSION, JSON.stringify({accessToken: data.access_token, expiresAt: Date.now() + data.expires_in * 1000}));
    return true;
  }
  function logout() {
    clear(); sessionStorage.removeItem(TRANSACTION);
    const c = config();
    const url = new URL("/logout", c.domain);
    url.search = new URLSearchParams({client_id: c.clientId, logout_uri: c.logoutUri});
    location.assign(url.href);
  }
  // Only this confirmed historical local JWT key is removed.
  localStorage.removeItem("clouddocs_access_token");
  return {login, callback, logout, token, clear};
})();
