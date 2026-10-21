package io.lauren.renvault.baseline

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

private const val Package = "io.lauren.renvault"
private const val Settle = 5_000L

class VaultProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val password: String?
        get() = InstrumentationRegistry.getArguments().getString("vaultPassword")

    @Test
    fun startup() = rule.collect(
        packageName = Package,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun journey() = rule.collect(packageName = Package) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        if (!unlock()) return@collect

        openSection("Tutto")
        scrollAround()

        device.findObject(By.desc("Seleziona"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        leaveSection()
        openSection("Passkey")
        scrollAround()
        leaveSection()

        openSection("Wi-Fi")
        scrollAround()
        leaveSection()
    }

    private fun MacrobenchmarkScope.unlock(): Boolean {
        val secret = password ?: return false
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), Settle)
            ?: return false
        field.text = secret
        device.pressBack()
        device.findObject(By.text(Pattern.compile("Unlock|Sblocca")))?.click()
        return device.wait(Until.hasObject(By.text("Tutto")),20_000L)
    }

    private fun MacrobenchmarkScope.openSection(name: String) {
        device.findObject(By.text(name))?.click()
        device.wait(Until.hasObject(By.desc(Pattern.compile("(Cerca|Search).*"))), Settle)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.leaveSection() {
        device.pressBack()
        device.wait(Until.hasObject(By.text("Tutto")), Settle)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollAround() {
        val list = device.findObject(By.scrollable(true))
            ?: device.findObject(By.pkg(Package).depth(0))
            ?: return
        list.setGestureMargin(device.displayWidth / 6)
        repeat(4) {
            list.swipe(Direction.UP, 0.8f, 3_000)
            device.waitForIdle()
        }
        repeat(2) {
            list.swipe(Direction.DOWN, 0.8f, 3_000)
            device.waitForIdle()
        }
    }
}
