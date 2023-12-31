package net.corda.libs.permissions.manager.impl

import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.PermissionGroupManager
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.PermissionRoleManager
import net.corda.libs.permissions.manager.PermissionUserManager

class PermissionManagerImpl(
    private val permissionUserManager: PermissionUserManager,
    private val permissionGroupManager: PermissionGroupManager,
    private val permissionRoleManager: PermissionRoleManager,
    private val permissionEntityManager: PermissionEntityManager
) : PermissionManager,
    PermissionUserManager by permissionUserManager,
    PermissionGroupManager by permissionGroupManager,
    PermissionRoleManager by permissionRoleManager,
    PermissionEntityManager by permissionEntityManager {

    private var running = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}
