package com.clouddocs.work;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class WorkTaskDtos {
    private WorkTaskDtos(){}
    public record CreateRequest(@NotBlank @Size(max=255) String title,
            @NotBlank @Size(max=2000) String description,@NotNull @Positive Long assigneeId,
            @NotNull @Future Instant dueAt){}
    public record StartRequest(@NotNull @PositiveOrZero Long version){}
    public record SubmitRequest(@NotNull @PositiveOrZero Long version,@NotNull @Positive Long documentId,
            @Size(max=2000) String note){}
    public record ReviewRequest(@NotNull @PositiveOrZero Long version,@Size(max=2000) String feedback){}
    public record Person(Long id,String fullName,String department){}
    public record ReportDocument(Long id,String title,String fileName){}
    public record Response(Long id,String title,String description,Person assignedBy,Person assignee,
            Instant dueAt,String status,boolean overdue,boolean lateSubmission,ReportDocument reportDocument,
            boolean reportAvailable,String reportNote,String reviewFeedback,Instant submittedAt,
            Instant reviewedAt,Instant createdAt,Instant updatedAt,Long version){}
}
