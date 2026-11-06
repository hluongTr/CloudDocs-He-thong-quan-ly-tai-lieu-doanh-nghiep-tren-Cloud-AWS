# Cognito authentication

See [README.md](README.md) for setup, callback URLs, migrations, bootstrap ADMIN, profile
names and storage accounting. Local development uses Cognito too; there is no local JWT,
password login/register endpoint, or hybrid mode. Logout records backend audit and redirects
to Cognito; it does not instantly invalidate every copy of an access token.
