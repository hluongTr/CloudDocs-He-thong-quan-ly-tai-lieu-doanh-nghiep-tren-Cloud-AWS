package com.clouddocs.department;

import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

@Service
public class DepartmentService {
    private final UserRepository users;
    private final DocumentRepository documents;

    public DepartmentService(UserRepository users, DocumentRepository documents) {
        this.users = users;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public List<String> findAll() {
        Map<String, String> unique = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Stream.concat(
                        users.findDistinctDepartments().stream(),
                        documents.findDistinctDepartments().stream())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(value -> unique.putIfAbsent(value, value));
        return List.copyOf(unique.values());
    }
}
