package com.clouddocs.work;
import com.clouddocs.auth.User;
import com.clouddocs.document.Document;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="work_tasks")
public class WorkTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=255) String title;
    @Column(nullable=false,length=2000) String description;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assigned_by",nullable=false) User assignedBy;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assignee_id",nullable=false) User assignee;
    @Column(name="due_at",nullable=false) Instant dueAt;
    @Column(nullable=false,length=20) String status="TODO";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="report_document_id") Document reportDocument;
    @Column(name="report_note",length=2000) String reportNote;
    @Column(name="review_feedback",length=2000) String reviewFeedback;
    @Column(name="submitted_at") Instant submittedAt;
    @Column(name="reviewed_at") Instant reviewedAt;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) Long version;
    @PrePersist void createTime(){ createdAt=Instant.now(); updatedAt=createdAt; }
    @PreUpdate void updateTime(){ updatedAt=Instant.now(); }
}
