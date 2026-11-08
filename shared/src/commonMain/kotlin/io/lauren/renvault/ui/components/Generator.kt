package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.RvGenerated
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.RvSecureScreen
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.readPreference
import io.lauren.renvault.lib.strengthLabel
import io.lauren.renvault.lib.rememberStrength
import io.lauren.renvault.lib.writePreference
import io.lauren.renvault.ui.theme.RenVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt

private enum class GeneratorKind(val key: String) { Apple("apple"), Random("random"), Words("words") }

private const val KindKey = "generator_kind"
private const val LengthKey = "generator_length"
private const val WordsKey = "generator_words"
private const val DigitsKey = "generator_digits"
private const val SymbolsKey = "generator_symbols"
private const val DefaultLength = 20
private const val DefaultWords = 4
private const val MinWords = 3
private const val MaxWords = 10

private class GeneratorPrefs {
    var kind by mutableStateOf(GeneratorKind.entries.firstOrNull { it.key == readPreference(KindKey) } ?: GeneratorKind.Apple)
    var length by mutableIntStateOf(readPreference(LengthKey)?.toIntOrNull() ?: DefaultLength)
    var words by mutableIntStateOf(readPreference(WordsKey)?.toIntOrNull() ?: DefaultWords)
    var digits by mutableStateOf(readPreference(DigitsKey) != "false")
    var symbols by mutableStateOf(readPreference(SymbolsKey) != "false")

    fun save() {
        writePreference(KindKey, kind.key)
        writePreference(LengthKey, length.toString())
        writePreference(WordsKey, words.toString())
        writePreference(DigitsKey, digits.toString())
        writePreference(SymbolsKey, symbols.toString())
    }

    suspend fun generate(domain: String): RvGenerated? = runCatching {
        Vault.generatePassword(domain, kind.key, length, digits, symbols, words)
    }.getOrNull()
}

@Composable
fun RvPasswordSuggestion(
    domain: String,
    strings: RvStrings,
    onUse: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var suggested by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(domain) {
        suggested = GeneratorPrefs().generate(domain)?.password.orEmpty()
    }
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(RvShieldIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(strings.generatorSuggested, style = MaterialTheme.typography.labelLarge)
                }
                ScrambleText(
                    target = suggested,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onMore, shapes = ButtonDefaults.shapes()) {
                        Text(strings.generatorMore, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Button(
                        onClick = {
                            scope.launch { runCatching { Vault.recordGenerated(suggested, domain) } }
                            onUse(suggested)
                        },
                        enabled = suggested.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(strings.generatorUseShort)
                    }
                }
            }
        }
    }
}

@Composable
fun RvGeneratorSheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    domain: String,
    strings: RvStrings,
    onUse: (String) -> Unit,
) {
    if (!open) return
    RvSecureScreen()

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val copy = rememberClipboardCopy()
    val prefs = remember { GeneratorPrefs() }
    var result by remember { mutableStateOf<RvGenerated?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var historyOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val record: (String) -> Unit = { value ->
        scope.launch { runCatching { Vault.recordGenerated(value, domain) } }
    }

    LaunchedEffect(prefs.kind, prefs.length, prefs.words, prefs.digits, prefs.symbols, round) {
        prefs.save()
        prefs.generate(domain)?.let { result = it }
    }

    val close: () -> Unit = {
        scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
    }
    val password = result?.password.orEmpty()
    val motion = MaterialTheme.motionScheme
    val blocked = result?.blocked.orEmpty()
    val kind = prefs.kind.takeIf { it.key !in blocked } ?: GeneratorKind.Random

    RvGeneratedSheet(
        open = historyOpen,
        onOpenChange = { historyOpen = it },
        strings = strings,
        onUse = {
            onUse(it)
            close()
        },
    )

    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.generatorTitle,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { historyOpen = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvHistoryIcon, contentDescription = strings.generatedTitle)
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ScrambleText(
                            target = password,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(
                            onClick = { round++ },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(RvSyncIcon, contentDescription = strings.generatorAgain)
                        }
                    }
                }

                val strength = rememberStrength(password)
                RvStrengthMeter(strength = strength, label = strings.strengthLabel(strength))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val kinds = GeneratorKind.entries
                    kinds.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = kind == option,
                            onCheckedChange = { prefs.kind = option },
                            enabled = option.key !in blocked,
                            modifier = Modifier.weight(1f),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                kinds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(
                                text = when (option) {
                                    GeneratorKind.Apple -> strings.generatorKindApple
                                    GeneratorKind.Random -> strings.generatorKindRandom
                                    GeneratorKind.Words -> strings.generatorKindWords
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = kind,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
                    },
                    label = "generatorOptions",
                ) { shown ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (shown) {
                            GeneratorKind.Apple -> Unit
                            GeneratorKind.Random -> {
                                val low = result?.minLength ?: 8
                                val high = maxOf(result?.maxLength ?: 64, low + 1)
                                Counter(
                                    label = strings.generatorLength,
                                    value = prefs.length.coerceIn(low, high),
                                    range = low..high,
                                    onValue = { prefs.length = it },
                                )
                                val toggles = buildList {
                                    if (result?.needsDigits != true) add(true)
                                    if (result?.needsSymbols != true) add(false)
                                }
                                if (toggles.isNotEmpty()) {
                                    RvSection {
                                        toggles.forEachIndexed { index, digits ->
                                            if (digits) {
                                                Toggle(index, toggles.size, strings.generatorDigits, null, prefs.digits) { prefs.digits = it }
                                            } else {
                                                Toggle(
                                                    index = index,
                                                    count = toggles.size,
                                                    title = strings.generatorSymbols,
                                                    detail = result?.symbols?.takeIf { it.isNotEmpty() },
                                                    checked = prefs.symbols,
                                                ) { prefs.symbols = it }
                                            }
                                        }
                                    }
                                }
                            }
                            GeneratorKind.Words -> {
                                Counter(
                                    label = strings.generatorWords,
                                    value = prefs.words,
                                    range = MinWords..MaxWords,
                                    onValue = { prefs.words = it },
                                )
                                RvSection {
                                    Toggle(0, 1, strings.generatorDigits, null, prefs.digits) { prefs.digits = it }
                                }
                            }
                        }
                    }
                }

                result?.takeIf { it.ruled && domain.isNotEmpty() }?.let { rules ->
                    val parts = buildList {
                        if (rules.maxLength < 64) add(strings.generatorRuleMax(rules.maxLength.toString()))
                        if (rules.minLength > 8) add(strings.generatorRuleMin(rules.minLength.toString()))
                        if (rules.needsDigits) add(strings.generatorRuleDigits)
                        if (rules.needsSymbols) add(strings.generatorRuleSpecial)
                        if (rules.symbols.isNotEmpty()) add(strings.generatorRuleSymbols(rules.symbols))
                    }.ifEmpty { listOf(strings.generatorRuleApplied) }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(RvInfoIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(strings.generatorRules(domain, parts.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            copy(password, true)
                            record(password)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar(strings.fieldCopied)
                            }
                        },
                        enabled = password.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(RvCopyIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(strings.generatorCopy, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    Button(
                        onClick = {
                            record(password)
                            onUse(password)
                            close()
                        },
                        enabled = password.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1.4f),
                    ) {
                        Text(strings.generatorUse)
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.titleMediumEmphasized)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun Toggle(
    index: Int,
    count: Int,
    title: String,
    detail: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    RvSegment(
        index = index,
        count = count,
        onClick = { onChange(!checked) },
        supporting = detail?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

@Composable
private fun colored(password: String): AnnotatedString {
    val digit = RenVault.accents.blue.content
    val symbol = RenVault.accents.orange.content
    return buildAnnotatedString {
        password.forEach { c ->
            val tint: Color? = when {
                c.isDigit() -> digit
                c == '-' || c.isLetter() -> null
                else -> symbol
            }
            if (tint != null) withStyle(SpanStyle(color = tint)) { append(c) } else append(c)
        }
    }
}

@Composable
private fun ScrambleText(target: String, style: TextStyle, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target.isEmpty()) {
            shown = target
            return@LaunchedEffect
        }
        for (frame in 1..ScrambleFrames) {
            val settled = target.length * frame / ScrambleFrames
            shown = buildString {
                target.forEachIndexed { index, c ->
                    append(if (index < settled) c else scrambled(c))
                }
            }
            delay(ScrambleStep)
        }
        shown = target
    }
    Text(
        text = colored(shown.ifEmpty { " " }),
        style = style,
        modifier = modifier,
    )
}

@Composable
fun rememberRuleProblem(domain: String, password: String, strings: RvStrings): String? {
    var problem by remember { mutableStateOf<String?>(null) }
    var checked by remember(domain) { mutableStateOf(false) }
    LaunchedEffect(domain, password, strings) {
        if (domain.isEmpty() || password.isEmpty()) {
            problem = null
            return@LaunchedEffect
        }
        if (checked) delay(RuleCheckDelay)
        checked = true
        val check = runCatching { Vault.checkPassword(domain, password) }.getOrNull()
        problem = if (check?.problems.isNullOrEmpty()) null else strings.rulesInvalid(domain)
    }
    return problem
}

private val RuleCheckDelay = 300.milliseconds

private const val ScrambleFrames = 12
private const val ScrambleLower = "abcdefghijkmnopqrstuvwxyz"
private const val ScrambleUpper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val ScrambleDigits = "0123456789"
private const val ScrambleSymbols = "!#$%&*+=?@^_"

private fun scrambled(c: Char): Char = when {
    c.isDigit() -> ScrambleDigits.random()
    c.isUpperCase() -> ScrambleUpper.random()
    c.isLowerCase() -> ScrambleLower.random()
    c == '-' || c.isWhitespace() -> c
    else -> ScrambleSymbols.random()
}
private val ScrambleStep = 24.milliseconds
