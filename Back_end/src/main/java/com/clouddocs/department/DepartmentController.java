package com.clouddocs.department;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
@RestController @RequestMapping("/api/departments")
public class DepartmentController {
 private final DepartmentService service;public DepartmentController(DepartmentService service){this.service=service;}
 @GetMapping public List<String> findAll(){return service.findAll();}
 @GetMapping("/catalog") public List<Department> catalog(){return service.catalog();}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Department create(@Valid @RequestBody Request r,Authentication a){return service.create(r.name(),r.allowSelfSelection(),a.getName());}
 @PutMapping("/{id}") public Department update(@PathVariable Long id,@Valid @RequestBody Request r,Authentication a){return service.update(id,r.name(),r.active(),r.allowSelfSelection(),a.getName());}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Authentication a){service.delete(id,a.getName());}
 @PatchMapping("/assignments/{userId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void assign(@PathVariable Long userId,@Valid @RequestBody Assignment r,Authentication a){service.assign(userId,r.name(),a.getName());}
 public record Request(@NotBlank @Size(max=100) String name,boolean active,boolean allowSelfSelection){}
 public record Assignment(@Size(max=100) String name){}
}
