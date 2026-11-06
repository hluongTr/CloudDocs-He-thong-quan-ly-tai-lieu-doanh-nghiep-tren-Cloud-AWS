package com.clouddocs.auth;

/** Presentation only: never use placeholders for identity matching or authorization. */
public final class ProfilePresentation {
    private ProfilePresentation() {}
    public static boolean missingName(String name) {
        return name == null || name.isBlank() || "Người dùng CloudDocs".equals(name.trim()) || "Chưa cập nhật".equals(name.trim());
    }
    public static boolean missingEmail(String email) {
        return email == null || email.isBlank() || email.toLowerCase(java.util.Locale.ROOT).endsWith("@identity.invalid");
    }
    public static String name(User user) { return missingName(user.getFullName()) ? "Chưa cập nhật" : user.getFullName(); }
    public static String email(User user) { return missingEmail(user.getEmail()) ? null : user.getEmail(); }
}
