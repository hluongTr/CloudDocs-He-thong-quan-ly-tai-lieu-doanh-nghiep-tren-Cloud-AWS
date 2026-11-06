package com.clouddocs.document;

import com.clouddocs.auth.ProfilePresentation;
import com.clouddocs.auth.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Resolves presentation names by stable owner ID without altering ownership or permissions. */
public final class DocumentOwnerNames {
    private DocumentOwnerNames() {}

    public static Map<Long, String> resolve(Collection<Document> documents, UserRepository users) {
        var ids = documents.stream().map(Document::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) users.findAllById(ids).forEach(user -> names.put(user.getId(), ProfilePresentation.name(user)));
        return names;
    }

    public static String name(Document document, Map<Long, String> names) {
        String name = names.get(document.getOwnerId());
        return name == null ? "Chưa cập nhật" : name;
    }
}
