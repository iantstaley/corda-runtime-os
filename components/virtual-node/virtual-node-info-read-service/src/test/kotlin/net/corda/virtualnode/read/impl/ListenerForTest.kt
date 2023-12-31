package net.corda.virtualnode.read.impl

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener

class ListenerForTest : VirtualNodeInfoListener {
    var update = false
    var lastSnapshot = mapOf<HoldingIdentity, VirtualNodeInfo>()
    var changedKeys = emptySet<HoldingIdentity>()

    override fun onUpdate(
        changedKeys: Set<HoldingIdentity>,
        currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>
    ) {
        update = true
        lastSnapshot = currentSnapshot
        this.changedKeys = changedKeys
    }
}
