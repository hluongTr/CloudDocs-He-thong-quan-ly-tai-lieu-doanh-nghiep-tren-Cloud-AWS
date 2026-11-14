package com.clouddocs.department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DepartmentRepository extends JpaRepository<Department,Long>{
 List<Department> findAllByOrderByNameAsc();Optional<Department> findByNameKey(String key);
}
