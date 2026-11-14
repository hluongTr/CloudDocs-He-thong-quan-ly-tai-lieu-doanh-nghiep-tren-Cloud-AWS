package com.clouddocs.auth;
import jakarta.persistence.*;
@Entity @Table(name="user_avatars")
public class UserAvatar {
 @Id @Column(name="user_id") private Long userId;
 @Lob @Column(nullable=false,columnDefinition="MEDIUMBLOB") private byte[] data;
 public Long getUserId(){return userId;}public void setUserId(Long id){userId=id;}public byte[] getData(){return data;}public void setData(byte[] value){data=value;}
}
