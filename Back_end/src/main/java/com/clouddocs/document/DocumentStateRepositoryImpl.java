package com.clouddocs.document;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
public class DocumentStateRepositoryImpl implements DocumentStateRepository {
    @PersistenceContext private EntityManager entityManager;
    @Override public void refreshState(Document document) { entityManager.refresh(document); }
}
