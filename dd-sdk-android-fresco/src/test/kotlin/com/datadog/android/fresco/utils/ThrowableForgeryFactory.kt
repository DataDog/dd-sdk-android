package com.datadog.android.fresco.utils

import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ThrowableForgeryFactory :
    ForgeryFactory<Throwable> {
    override fun getForgery(forge: Forge): Throwable {
        return forge.aThrowable()
    }
}
