// src/main/java/io/github/ssforu/pin4u/features/groups/domain/StatusConverter.java
package io.github.ssforu.pin4u.features.groups.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusConverter implements AttributeConverter<GroupMember.Status, String> {

    @Override
    public String convertToDatabaseColumn(GroupMember.Status attribute) {
        // DB에는 소문자('pending'|'approved')
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public GroupMember.Status convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        // 엔티티 enum은 UPPER(PENDING/APPROVED)
        return GroupMember.Status.valueOf(dbData.toUpperCase());
    }
}
