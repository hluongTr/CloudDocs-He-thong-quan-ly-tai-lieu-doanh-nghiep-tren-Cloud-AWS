# Account profiles

See [README.md](README.md). Both ACTIVE USER and ADMIN can edit their own contact profile.
PUT /api/auth/me/display-name saves only the authenticated user's name without requiring
email/department; role/status/identity fields cannot be changed through this endpoint.
Names missing in Cognito can be entered once in the dashboard. Password changes are handled
by Cognito. Existing document permissions, notifications and preferences remain unchanged.
