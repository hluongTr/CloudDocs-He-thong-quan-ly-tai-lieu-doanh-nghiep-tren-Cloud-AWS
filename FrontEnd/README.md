# CloudDocs frontend

Vanilla HTML/CSS/JavaScript. The login button is labeled Đăng nhập and redirects to the
configured identity provider using Authorization Code + PKCE.

From the project root:

~~~powershell
.\scripts\start-frontend.ps1
~~~

Or run node scripts/serve-frontend.cjs. No npx installation is needed. The server reads the
public redirectUri in js/cognito-config.js, uses its loopback host/port and supports its path
prefix (including /FrontEnd/index.html). Only the FrontEnd directory is exposed; the backend,
SQL scripts and other project files are not served. Open the URL printed by the server.

Keep callback/logout URLs exactly registered in the Cognito app client. API_BASE in js/app.js
selects the backend. The backend startup script derives CORS from the same callback origin.
Never store access keys, client secrets or passwords in frontend files.

Tests: node --test FrontEnd/tests/*.test.cjs from the project root.

The S3 preview implementation is the only active preview handler. The old overwritten local
preview function was removed. Local backend endpoints remain for compatibility.
