package io.lauren.renvault.lib

import java.security.SecureRandom

private val random = SecureRandom()

actual fun secureRandomUInt(): UInt = random.nextInt().toUInt()
