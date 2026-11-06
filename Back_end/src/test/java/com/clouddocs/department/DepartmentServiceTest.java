package com.clouddocs.department;

import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {
    @Test
    void combinesUserAndDocumentDepartmentsWithoutBlankOrDuplicates() {
        UserRepository users = mock(UserRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        when(users.findDistinctDepartments()).thenReturn(List.of(" Kỹ thuật ", "Nhân sự", ""));
        when(documents.findDistinctDepartments())
                .thenReturn(List.of("kỹ thuật", "Marketing", "  "));

        List<String> result = new DepartmentService(users, documents).findAll();

        assertEquals(List.of("Kỹ thuật", "Marketing", "Nhân sự"), result);
    }
}
