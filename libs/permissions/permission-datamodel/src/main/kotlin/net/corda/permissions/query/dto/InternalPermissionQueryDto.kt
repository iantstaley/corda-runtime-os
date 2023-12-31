package net.corda.permissions.query.dto

import net.corda.permissions.model.PermissionType

/**
 * Internal permission query data transfer object holding the summary data for one permission.
 */
data class InternalPermissionQueryDto(
    val loginName: String,
    val id: String,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionString: String,
    val permissionType: PermissionType
)

/**
 * Internal permission query data transfer object holding the login name and if user is enabled.
 */
data class InternalUserEnabledQueryDto(
    val loginName: String,
    val enabled: Boolean
)
