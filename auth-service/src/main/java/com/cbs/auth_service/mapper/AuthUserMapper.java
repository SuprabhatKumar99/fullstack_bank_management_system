package com.cbs.auth_service.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.cbs.auth_service.dto.response.UserProfileResponse;
import com.cbs.auth_service.entity.AuthUser;

/**
 * MapStruct mapper: {@link AuthUser} entity → {@link UserProfileResponse} DTO.
 *
 * <p>The {@code password} field is never mapped to any DTO — MapStruct
 * only maps fields that have a corresponding target, so it is excluded by default.
 * The explicit {@code @Mapping(target = "role", ...)} converts the enum to its
 * string name, matching what the JWT claim carries.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AuthUserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    UserProfileResponse toProfileResponse(AuthUser user);
}