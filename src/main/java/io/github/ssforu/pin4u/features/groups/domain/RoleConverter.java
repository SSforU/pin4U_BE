// src/main/java/io/github/ssforu/pin4u/features/groups/domain/RoleConverter.java
package io.github.ssforu.pin4u.features.groups.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RoleConverter implements AttributeConverter<GroupMember.Role, String> {

    @Override
    public String convertToDatabaseColumn(GroupMember.Role attribute) {
        // DB에는 소문자('owner'|'member')로 저장 → DDL CHECK와 일치
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public GroupMember.Role convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        // 엔티티 enum은 UPPER(OWNER/MEMBER)로 선언 → 대문자 변환 후 매칭
        return GroupMember.Role.valueOf(dbData.toUpperCase());
    }
}
