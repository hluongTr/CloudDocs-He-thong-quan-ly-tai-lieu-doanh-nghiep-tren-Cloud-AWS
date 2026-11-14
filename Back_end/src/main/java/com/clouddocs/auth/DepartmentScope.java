package com.clouddocs.auth;
/** Department access follows current owner membership, never an arbitrary document label. */
public final class DepartmentScope {
 private DepartmentScope(){}
 public static boolean assigned(User user){String d=user.getDepartment();return d!=null&&!d.isBlank()&&!d.trim().equalsIgnoreCase("Chưa cập nhật");}
 public static boolean same(User a,User b){return assigned(a)&&assigned(b)&&a.getDepartment().trim().equalsIgnoreCase(b.getDepartment().trim());}
 public static boolean manager(User user){return "MANAGER".equalsIgnoreCase(user.getRole());}
}
