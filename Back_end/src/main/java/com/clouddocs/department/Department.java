package com.clouddocs.department;
import jakarta.persistence.*;
@Entity @Table(name="departments")
public class Department {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=100) private String name;
 @Column(name="name_key",nullable=false,unique=true,length=100) private String nameKey;
 @Column(nullable=false) private boolean active=true;
 @Column(name="allow_self_selection",nullable=false) private boolean allowSelfSelection=false;
 public Long getId(){return id;}public String getName(){return name;}public String getNameKey(){return nameKey;}public boolean isActive(){return active;}public boolean isAllowSelfSelection(){return allowSelfSelection;}
 public void setName(String name){this.name=name;this.nameKey=name.toLowerCase(java.util.Locale.ROOT);}public void setActive(boolean v){active=v;}public void setAllowSelfSelection(boolean v){allowSelfSelection=v;}
}
