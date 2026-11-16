package com.clouddocs.folder;

import jakarta.persistence.*;

@Entity
@Table(name = "document_folders")
public class DocumentFolder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "parent_id")
    private Long parentId;
    @Column(nullable = false, length = 120)
    private String name;
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { ownerId = value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
}
