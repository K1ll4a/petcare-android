package com.petcare.app

import kotlinx.datetime.*
fun fixedClock(at: Instant, zone: TimeZone): Clock = object : Clock { override fun now() = at }
