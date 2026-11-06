package com.clouddocs.work;
import com.clouddocs.work.WorkTaskDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/work-tasks")
public class WorkTaskController {
    private final WorkTaskService service;
    public WorkTaskController(WorkTaskService service){this.service=service;}
    @GetMapping public List<Response> list(Authentication a){return service.list(a.getName());}
    @GetMapping("/assignees") public List<Person> assignees(Authentication a){return service.assignees(a.getName());}
    @GetMapping("/report-documents") public List<ReportDocument> reportDocuments(Authentication a){return service.reportDocuments(a.getName());}
    @GetMapping("/{id}") public Response detail(@PathVariable Long id,Authentication a){return service.detail(id,a.getName());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,@RequestParam Long version,Authentication a){service.delete(id,version,a.getName());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Response create(@Valid @RequestBody CreateRequest r,Authentication a){return service.create(r,a.getName());}
    @PostMapping("/{id}/start") public Response start(@PathVariable Long id,@Valid @RequestBody StartRequest r,Authentication a){return service.start(id,r,a.getName());}
    @PostMapping("/{id}/submit") public Response submit(@PathVariable Long id,@Valid @RequestBody SubmitRequest r,Authentication a){return service.submit(id,r,a.getName());}
    @PostMapping("/{id}/approve") public Response approve(@PathVariable Long id,@Valid @RequestBody ReviewRequest r,Authentication a){return service.review(id,r,true,a.getName());}
    @PostMapping("/{id}/request-changes") public Response changes(@PathVariable Long id,@Valid @RequestBody ReviewRequest r,Authentication a){return service.review(id,r,false,a.getName());}
}
