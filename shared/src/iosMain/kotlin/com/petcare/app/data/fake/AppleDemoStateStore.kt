package com.petcare.app.data.fake

import com.petcare.app.domain.repository.DemoStateStore
import platform.Foundation.NSUserDefaults

/** Local demo-backend metadata only. Passwords, active tokens and sessions are not persisted here. */
class AppleDemoStateStore : DemoStateStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(): String? = defaults.stringForKey("petcare.demo.backend")
    override fun write(value: String) = defaults.setObject(value, forKey = "petcare.demo.backend")
}
