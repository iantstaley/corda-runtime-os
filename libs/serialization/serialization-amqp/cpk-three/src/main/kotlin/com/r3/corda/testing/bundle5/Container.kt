package com.r3.corda.testing.bundle5

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class Container<T>(val obj: T)