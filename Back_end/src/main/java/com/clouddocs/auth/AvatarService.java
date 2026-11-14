package com.clouddocs.auth;
import com.clouddocs.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import java.time.Instant;
@Service @Transactional
public class AvatarService {
 private final UserRepository users;private final UserAvatarRepository avatars;private final AuditLogService audit;
 public AvatarService(UserRepository users,UserAvatarRepository avatars,AuditLogService audit){this.users=users;this.avatars=avatars;this.audit=audit;}
 private User actor(String name){return users.findByUsernameIgnoreCase(name).filter(u->"ACTIVE".equalsIgnoreCase(u.getStatus())).orElseThrow(()->new AccessDeniedException("Tài khoản không hoạt động"));}
 public void upload(String username,MultipartFile file){User actor=actor(username);byte[] normalized=normalize(file);User user=users.findForStorageUpdate(actor.getId()).orElseThrow();if(!"ACTIVE".equalsIgnoreCase(user.getStatus()))throw new AccessDeniedException("Tài khoản không hoạt động");UserAvatar avatar=new UserAvatar();avatar.setUserId(user.getId());avatar.setData(normalized);avatars.saveAndFlush(avatar);user.setAvatarUpdatedAt(Instant.now());users.saveAndFlush(user);audit.logInTransaction(username,"UPDATE_AVATAR",null,user.getId(),ProfilePresentation.name(user)+" đã cập nhật ảnh đại diện");}
 public void delete(String username){User actor=actor(username);User user=users.findForStorageUpdate(actor.getId()).orElseThrow();if(!"ACTIVE".equalsIgnoreCase(user.getStatus()))throw new AccessDeniedException("Tài khoản không hoạt động");avatars.findById(user.getId()).ifPresent(avatars::delete);user.setAvatarUpdatedAt(null);users.saveAndFlush(user);audit.logInTransaction(username,"DELETE_AVATAR",null,user.getId(),ProfilePresentation.name(user)+" đã xóa ảnh đại diện");}
 @Transactional(readOnly=true) public byte[] read(String username,Long id){actor(username);users.findById(id).filter(u->!"DELETED".equalsIgnoreCase(u.getStatus())).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));return avatars.findById(id).map(UserAvatar::getData).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));}
 byte[] normalize(MultipartFile file){if(file==null||file.isEmpty()||file.getSize()>5*1024*1024)throw new IllegalArgumentException("Chọn ảnh JPG hoặc PNG không quá 5 MB");
 try(var source=file.getInputStream();var input=ImageIO.createImageInputStream(source)){if(input==null)throw new IllegalArgumentException("Ảnh không hợp lệ");var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPG và PNG");var reader=readers.next();try{String format=reader.getFormatName();if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPG và PNG");reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||(long)width*height>16000000)throw new IllegalArgumentException("Ảnh quá lớn, tối đa 16 triệu điểm ảnh");BufferedImage image=reader.read(0);BufferedImage result=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);int size=Math.min(width,height),x=(width-size)/2,y=(height-size)/2;g.drawImage(image,0,0,256,256,x,y,x+size,y+size,null);}finally{g.dispose();}ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(result,"png",out);return out.toByteArray();}finally{reader.dispose();}}
 catch(IOException|IndexOutOfBoundsException ex){throw new IllegalArgumentException("Không đọc được ảnh. Hãy chọn ảnh JPG hoặc PNG khác",ex);}
 }
}
