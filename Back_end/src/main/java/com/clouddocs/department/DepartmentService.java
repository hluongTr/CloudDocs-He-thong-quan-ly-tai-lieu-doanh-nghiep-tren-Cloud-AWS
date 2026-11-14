package com.clouddocs.department;
import com.clouddocs.auth.*;
import com.clouddocs.document.DocumentRepository;
import com.clouddocs.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
@Service @Transactional
public class DepartmentService {
 public static final String UNASSIGNED="Chưa cập nhật";
 private final DepartmentRepository departments;private final UserRepository users;private final DocumentRepository documents;private final AuditLogService audit;
 public DepartmentService(DepartmentRepository departments,UserRepository users,DocumentRepository documents,AuditLogService audit){this.departments=departments;this.users=users;this.documents=documents;this.audit=audit;}
 @Transactional(readOnly=true) public List<String> findAll(){return departments.findAllByOrderByNameAsc().stream().map(Department::getName).toList();}
 @Transactional(readOnly=true) public List<Department> catalog(){return departments.findAllByOrderByNameAsc();}
 private String clean(String name){String n=name==null?"":name.trim();if(n.isEmpty()||n.length()>100||n.equalsIgnoreCase(UNASSIGNED)||n.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Tên phòng ban không hợp lệ");return n;}
 private User admin(String username){User u=users.findByUsernameIgnoreCase(username).orElseThrow(()->new AccessDeniedException("Không tìm thấy tài khoản"));if(!"ADMIN".equalsIgnoreCase(u.getRole())||!"ACTIVE".equalsIgnoreCase(u.getStatus()))throw new AccessDeniedException("Chỉ admin được quản lý phòng ban");return u;}
 private Department get(Long id){return departments.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy phòng ban"));}
 public Department create(String name,boolean self,String username){User actor=admin(username);String n=clean(name);if(departments.findByNameKey(n.toLowerCase(Locale.ROOT)).isPresent())throw new IllegalStateException("Tên phòng ban đã tồn tại");Department d=new Department();d.setName(n);d.setAllowSelfSelection(self);departments.saveAndFlush(d);log(actor,"CREATE_DEPARTMENT","đã thêm phòng ban "+n,null);return d;}
 public Department update(Long id,String name,boolean active,boolean self,String username){User actor=admin(username);Department d=get(id);String n=clean(name),old=d.getName();departments.findByNameKey(n.toLowerCase(Locale.ROOT)).ifPresent(other->{if(!Objects.equals(other.getId(),id))throw new IllegalStateException("Tên phòng ban đã tồn tại");});
 d.setName(n);d.setActive(active);d.setAllowSelfSelection(self);departments.saveAndFlush(d);
 if(!old.equals(n)){users.renameDepartment(old,n);documents.renameDepartment(old,n);}
 log(actor,"UPDATE_DEPARTMENT","đã cập nhật phòng ban "+old+" → "+n+(active?" (đang sử dụng)":" (ngừng sử dụng)"),null);return d;}
 @Transactional(readOnly=true) public String documentName(String requested,String previous){String n=requested==null?UNASSIGNED:requested.trim();if(n.isEmpty()||n.equalsIgnoreCase(UNASSIGNED))return UNASSIGNED;
 if(previous!=null&&previous.equals(n))return n;return active(n).getName();}
 private Department active(String name){Department d=departments.findByNameKey(clean(name).toLowerCase(Locale.ROOT)).orElseThrow(()->new IllegalArgumentException("Vui lòng chọn phòng ban có sẵn"));if(!d.isActive())throw new IllegalStateException("Phòng ban đã ngừng sử dụng");return d;}
 public String profileName(User user,String requested){String n=requested==null?UNASSIGNED:requested.trim();if(n.isEmpty())n=UNASSIGNED;if(Objects.equals(n,user.getDepartment()))return n;
 if(DepartmentScope.manager(user))throw new AccessDeniedException("Chỉ admin được thay đổi phòng ban của trưởng phòng");
 if(!"ADMIN".equalsIgnoreCase(user.getRole())){if(n.equals(UNASSIGNED))throw new AccessDeniedException("Chỉ admin được bỏ gán phòng ban");Department d=active(n);if(!d.isAllowSelfSelection())throw new AccessDeniedException("Phòng ban này cần admin gán cho nhân viên");return d.getName();}
 return n.equals(UNASSIGNED)?UNASSIGNED:active(n).getName();}
 public void assign(Long userId,String name,String username){User actor=admin(username);User target=users.findForStorageUpdate(userId).filter(u->!"DELETED".equalsIgnoreCase(u.getStatus())).orElseThrow(()->new IllegalArgumentException("Không tìm thấy nhân viên"));String n=name==null||name.isBlank()||name.equals(UNASSIGNED)?UNASSIGNED:active(name).getName();target.setDepartment(n);users.saveAndFlush(target);log(actor,"ASSIGN_DEPARTMENT","đã gán "+ProfilePresentation.name(target)+" vào phòng ban "+n,userId);}
 public void delete(Long id,String username){User actor=admin(username);Department d=get(id);String name=d.getName();users.renameDepartment(name,UNASSIGNED);documents.renameDepartment(name,UNASSIGNED);departments.deleteById(id);log(actor,"DELETE_DEPARTMENT","đã xóa phòng ban "+name+"; hồ sơ và tài liệu được chuyển về "+UNASSIGNED,null);}
 private void log(User u,String action,String details,Long target){audit.logInTransaction(u.getUsername(),action,null,target,ProfilePresentation.name(u)+" "+details);}
}
