package com.clouddocs.document;
import jakarta.validation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DocumentValidationTest {
    final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
    DocumentDtos.CreateDocumentRequest request(String title,String name,String type,Long size,String department,String description) {
        return new DocumentDtos.CreateDocumentRequest(title,description,name,type,size,department);
    }
    @Test void acceptsUnicodeNamesAndZeroByteFile() {
        assertTrue(validator.validate(request("Báo cáo","Báo cáo.txt","TXT",0L,"IT","")).isEmpty());
    }
    @Test void rejectsMissingSizeNegativeSizeAndOversizedColumns() {
        assertFalse(validator.validate(request("Report","a.txt","TXT",null,"IT","")).isEmpty());
        assertFalse(validator.validate(request("Report","a.txt","TXT",-1L,"IT","")).isEmpty());
        assertFalse(validator.validate(request("x".repeat(256),"a.txt","TXT",1L,"IT","")).isEmpty());
        assertFalse(validator.validate(request("Report","x".repeat(256),"TXT",1L,"IT","")).isEmpty());
        assertFalse(validator.validate(request("Report","a.txt","TXT",1L,"x".repeat(101),"")).isEmpty());
        assertFalse(validator.validate(request("Report","a.txt","TXT",1L,"IT","x".repeat(1001))).isEmpty());
    }
    @Test void rejectsHeaderControlCharactersAndInjectedFileType() {
        assertFalse(validator.validate(request("Report","a\r\nX-Test: evil","TXT",1L,"IT","")).isEmpty());
        assertFalse(validator.validate(request("Report","a.txt","<img src=x onerror=alert(1)>",1L,"IT","")).isEmpty());
    }
    @Test void ordinaryUpdateCannotBypassTrashEndpoints() {
        for(String status: new String[]{"TRASHED","PURGED","UNKNOWN"}) {
            assertFalse(validator.validate(new DocumentDtos.UpdateDocumentRequest("Report","","IT",status)).isEmpty());
        }
    }
}
