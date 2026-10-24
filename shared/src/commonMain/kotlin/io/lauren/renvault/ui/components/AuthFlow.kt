package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.lauren.renvault.ui.theme.RvTone
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.lauren.renvault.lib.MinPassword
import io.lauren.renvault.lib.RvPasswordStrength
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.strengthOf
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.theme.RenVault
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VeilMs = 220L
private val BodyMax = 440.dp
private const val DeviceArtFill = 0.64f

private enum class AuthStep { TwoFactor, TrustedDevices, DevicePasscode, LocalChoice, LocalProfile, MasterSetup, LocalJoin }

private enum class LocalMode { New, Join }

private enum class AuthEntry { Deciding, Credentials, Master }

data class RvAuthProfile(
    val name: String = "",
    val photo: ImageBitmap? = null,
)

data class RvAuthDevice(
    val name: String,
    val model: String,
    val art: ImageBitmap? = null,
)

@Composable
fun RvAuthFlow(
    modifier: Modifier = Modifier,
    photo: ImageBitmap? = null,
    strings: RvStrings = LocalStrings.current,
    describe: (Throwable) -> String = { it.message.orEmpty() },
    unlocked: suspend () -> Boolean = { false },
    configured: suspend () -> Boolean = { false },
    startLogin: suspend (String, String) -> Boolean = { _, _ -> false },
    requestCode: suspend () -> Unit = {},
    submitCode: suspend (String) -> Unit = {},
    loadDevices: suspend () -> List<RvAuthDevice> = { emptyList() },
    loadProfile: suspend () -> RvAuthProfile = { RvAuthProfile() },
    recoverDevice: suspend (Int, String) -> Unit = { _, _ -> },
    finishSetup: suspend (String) -> Unit = {},
    finishLocal: suspend (String, String, ByteArray?) -> Unit = { _, _, _ -> },
    joinPairing: suspend (String) -> String = { "" },
    unlock: suspend (String) -> Unit = {},
    onPrivacy: () -> Unit = {},
    anisette: String = "",
    onAnisette: suspend (String) -> Boolean = { true },
    backup: RvBackupActions? = null,
    onDone: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val steps = remember { mutableStateListOf<AuthStep>() }

    var entry by remember { mutableStateOf(AuthEntry.Deciding) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var chosen by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var busyTitle by remember { mutableStateOf("") }
    var busyLabel by remember { mutableStateOf("") }
    var codeError by remember { mutableIntStateOf(0) }
    var passcode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var masterRepeat by remember { mutableStateOf("") }
    var masterShown by remember { mutableStateOf(false) }
    var masterError by remember { mutableStateOf("") }
    var unlockValue by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<RvAuthDevice>>(emptyList()) }
    var face by remember { mutableStateOf(photo) }
    var anisetteOpen by remember { mutableStateOf(false) }
    var owner by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<LocalMode?>(null) }
    var profileName by remember { mutableStateOf("") }
    var profilePhoto by remember { mutableStateOf<ByteArray?>(null) }
    var profileFace by remember { mutableStateOf<ImageBitmap?>(null) }
    var joinCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf("") }
    val restoreFlow = backup?.let {
        rememberBackupFlow(
            actions = it,
            strings = strings,
            onMessage = { text -> joinError = text },
            onImported = { onDone() },
        )
    }

    val signInShake = remember { Animatable(0f) }
    val passcodeShake = remember { Animatable(0f) }
    val masterShake = remember { Animatable(0f) }
    val unlockShake = remember { Animatable(0f) }

    val passwordFocus = remember { FocusRequester() }
    val passcodeFocus = remember { FocusRequester() }
    val masterFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }

    suspend fun refuse(track: Animatable<Float, AnimationVector1D>, focus: FocusRequester) {
        delay(VeilMs)
        runCatching { focus.requestFocus() }
        track.shake()
    }

    suspend fun openDevices() {
        val account = runCatching { loadProfile() }.getOrNull()
        face = account?.photo ?: photo
        owner = account?.name.orEmpty()
        val found = loadDevices()
        devices = found
        if (found.size == 1) {
            chosen = 0
            passcode = ""
            passcodeError = ""
            steps += AuthStep.DevicePasscode
        } else {
            steps += AuthStep.TrustedDevices
        }
    }

    val here = steps.lastOrNull()

    LaunchedEffect(entry, here) {
        withFrameNanos { }

        val target = when {
            here == AuthStep.DevicePasscode -> passcodeFocus
            here == AuthStep.MasterSetup -> masterFocus
            here == null && entry == AuthEntry.Master -> unlockFocus
            else -> null
        }

        target?.let { runCatching { it.requestFocus() } }
    }

    val canContinue = email.trim().isNotEmpty() && password.isNotEmpty()

    val pickPhoto = rememberPhotoPicker { bytes ->
        scope.launch {
            val image = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            if (image != null) {
                profilePhoto = bytes
                profileFace = image
            }
        }
    }

    val openMaster: () -> Unit = {
        master = ""
        masterRepeat = ""
        masterError = ""
        steps += AuthStep.MasterSetup
    }

    val chooseLocal: (LocalMode) -> Unit = { mode ->
        local = mode
        if (mode == LocalMode.New) steps += AuthStep.LocalProfile else openMaster()
    }

    val submitJoin: (String) -> Unit = { text ->
        if (text.isNotBlank() && !busy) {
            scope.launch {
                joinError = ""
                busyTitle = strings.busyJoiningTitle
                busyLabel = strings.busyJoining
                busy = true
                try {
                    joinPairing(text)
                    onDone()
                    return@launch
                } catch (failure: Throwable) {
                    joinError = describe(failure)
                }
                busy = false
            }
        }
    }

    val scanJoin = rememberQrScanner(onScanned = submitJoin)

    val submitCredentials: () -> Unit = {
        if (canContinue && !busy) {
            scope.launch {
                error = ""
                busyTitle = strings.busySigningInTitle
                busyLabel = strings.busyCheckingCredentials
                busy = true
                try {
                    if (startLogin(email, password)) {
                        requestCode()
                        steps += AuthStep.TwoFactor
                    } else {
                        openDevices()
                    }
                } catch (failure: Throwable) {
                    error = describe(failure)
                    password = ""
                    busy = false
                    refuse(signInShake, passwordFocus)
                }
                busy = false
            }
        }
    }

    val resendCode: () -> Unit = {
        scope.launch {
            busyTitle = strings.authTwoFactorTitle
            busyLabel = strings.busySendingCode
            busy = true
            code = ""
            try {
                requestCode()
            } catch (failure: Throwable) {
                steps.removeLastOrNull()
                error = describe(failure)
                password = ""
                busy = false
                refuse(signInShake, passwordFocus)
            }
            busy = false
        }
    }

    val sendCode: (String) -> Unit = { entered ->
        scope.launch {
            busyTitle = strings.authTwoFactorTitle
            busyLabel = strings.busyVerifyingCode
            busy = true
            try {
                submitCode(entered)
                openDevices()
            } catch (failure: Throwable) {
                codeError++
                code = ""
            }
            busy = false
        }
    }

    val pickDevice: (Int) -> Unit = { index ->
        chosen = index
        passcode = ""
        passcodeError = ""
        steps += AuthStep.DevicePasscode
    }

    val submitPasscode: () -> Unit = {
        if (passcode.isNotEmpty() && !busy) {
            scope.launch {
                passcodeError = ""
                busyTitle = owner.ifEmpty { strings.authDevicePasscodeTitle }
                busyLabel = strings.busyVerifyingPasscode
                busy = true
                val entered = passcode
                passcode = ""
                try {
                    recoverDevice(chosen, entered)
                    master = ""
                    masterRepeat = ""
                    masterError = ""
                    steps += AuthStep.MasterSetup
                } catch (failure: Throwable) {
                    passcodeError = describe(failure)
                    busy = false
                    refuse(passcodeShake, passcodeFocus)
                }
                busy = false
            }
        }
    }

    val tooShort = master.isNotEmpty() && master.length < MinPassword
    val mismatch = masterRepeat.isNotEmpty() && master != masterRepeat
    val masterHint = when {
        masterError.isNotEmpty() -> masterError
        tooShort -> strings.authMasterTooShort(MinPassword)
        mismatch -> strings.authMasterMismatch
        else -> ""
    }
    val masterReady = master.length >= MinPassword && master == masterRepeat

    val submitMaster: () -> Unit = {
        if (masterReady && !busy) {
            scope.launch {
                masterError = ""
                busyTitle = strings.busyCreatingVaultTitle
                busyLabel = strings.busyCreatingVault
                busy = true
                val entered = master
                master = ""
                masterRepeat = ""
                try {
                    when (local) {
                        null -> finishSetup(entered)
                        LocalMode.New -> finishLocal(entered, profileName, profilePhoto)
                        LocalMode.Join -> finishLocal(entered, "", null)
                    }
                    if (local == LocalMode.Join) {
                        joinCode = ""
                        joinError = ""
                        steps += AuthStep.LocalJoin
                        busy = false
                        return@launch
                    }
                    onDone()
                    return@launch
                } catch (failure: Throwable) {
                    masterError = describe(failure)
                    busy = false
                    refuse(masterShake, masterFocus)
                }
                busy = false
            }
        }
    }

    val submitUnlock: () -> Unit = {
        if (unlockValue.isNotEmpty() && !busy) {
            scope.launch {
                unlockError = ""
                busyTitle = strings.busyOpeningVaultTitle
                busyLabel = strings.busyOpeningVault
                busy = true
                val entered = unlockValue
                unlockValue = ""
                try {
                    unlock(entered)
                    onDone()
                    return@launch
                } catch (failure: Throwable) {
                    unlockError = describe(failure)
                    busy = false
                    refuse(unlockShake, unlockFocus)
                }
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val slow = launch {
            delay(200)
            busyTitle = strings.busyOpeningVaultTitle
            busyLabel = strings.busyOpeningVault
            busy = true
        }
        try {
            if (unlocked()) {
                busyTitle = strings.busyOpeningVaultTitle
                busyLabel = strings.busyOpeningVault
                busy = true
                onDone()
                return@LaunchedEffect
            }
            entry = if (configured()) AuthEntry.Master else AuthEntry.Credentials
        } catch (failure: Throwable) {
            entry = AuthEntry.Credentials
        } finally {
            slow.cancel()
        }
        busy = false
    }

    val canGoBack = !busy && when (here) {
        AuthStep.DevicePasscode -> AuthStep.TrustedDevices in steps
        AuthStep.LocalChoice, AuthStep.LocalProfile -> true
        AuthStep.MasterSetup -> local != null
        else -> false
    }
    val goBack = {
        passcode = ""
        passcodeError = ""
        master = ""
        masterRepeat = ""
        masterError = ""
        steps.removeLastOrNull()
        if (steps.lastOrNull() == AuthStep.LocalChoice) local = null
        Unit
    }

    RvBackHandler(enabled = canGoBack, onBack = goBack)

    val avatar: @Composable () -> Unit = {
        RvProfileAvatar(size = 112.dp, photo = face, shape = MaterialShapes.Cookie12Sided.toShape())
    }

    val page = here?.let { "step:${it.name}" } ?: "entry:${entry.name}"
    val depth = steps.size
    val motion = MaterialTheme.motionScheme

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
            targetState = page to depth,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                val spatial = motion.defaultSpatialSpec<IntOffset>()
                val effects = motion.defaultEffectsSpec<Float>()
                (slideInHorizontally(spatial) { if (forward) it / 4 else -it / 4 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { if (forward) -it / 4 else it / 4 } + fadeOut(effects))
            },
            label = "authStep",
        ) { (shown, _) ->
            when (shown) {
                "entry:${AuthEntry.Master.name}" -> AuthPage(
                    hero = { AuthHero(RvLockIcon, MaterialShapes.Cookie9Sided) },
                    title = strings.authUnlockTitle,
                    hint = strings.authUnlockHint,
                    body = {
                        Column(modifier = Modifier.shaken { unlockShake.value }) {
                            RvTextField(
                                value = unlockValue,
                                onValueChange = { unlockValue = it },
                                label = strings.authMasterPlaceholder,
                                enabled = !busy,
                                secret = true,
                                leading = RvKeyIcon,
                                error = unlockError.ifEmpty { null },
                                focusRequester = unlockFocus,
                                onDone = submitUnlock,
                            )
                        }
                    },
                    actions = {
                        RvButton(
                            text = strings.authUnlockAction,
                            onClick = submitUnlock,
                            enabled = unlockValue.isNotEmpty(),
                            icon = RvLockOpenIcon,
                        )
                    },
                )

                "entry:${AuthEntry.Credentials.name}" -> AuthPage(
                    hero = { AuthHero(RvAppleIcon, MaterialShapes.Cookie12Sided) },
                    title = strings.authSignInTitle,
                    hint = strings.authSignInHint,
                    body = {
                        Column(
                            modifier = Modifier.shaken { signInShake.value },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RvTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = strings.authEmailPlaceholder,
                                enabled = !busy,
                                leading = RvPersonIcon,
                                keyboard = KeyboardType.Email,
                            )
                            RvTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = strings.authPasswordPlaceholder,
                                enabled = !busy,
                                secret = true,
                                leading = RvKeyIcon,
                                error = error.ifEmpty { null },
                                focusRequester = passwordFocus,
                                onDone = submitCredentials,
                            )
                        }
                    },
                    actions = {
                        PrivacyNotice(strings = strings, onPrivacy = onPrivacy)
                        RvButton(
                            text = strings.authSignInAction,
                            onClick = submitCredentials,
                            enabled = canContinue,
                        )
                        RvButton(
                            text = strings.authLocalAction,
                            onClick = { steps += AuthStep.LocalChoice },
                            kind = RvButtonKind.Tonal,
                            enabled = !busy,
                            icon = RvCloudOffIcon,
                        )
                    },
                    extra = {
                        AssistChip(
                            onClick = { anisetteOpen = true },
                            enabled = !busy,
                            label = { Text(strings.anisetteTitle) },
                            leadingIcon = {
                                Icon(RvServerIcon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                            },
                            trailingIcon = if (anisette.isNotEmpty()) {
                                {
                                    Icon(
                                        imageVector = RvCheckIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                            shape = CircleShape,
                        )
                    },
                )

                "step:${AuthStep.TwoFactor.name}" -> AuthPage(
                    hero = { AuthHero(RvCodesIcon, MaterialShapes.Gem) },
                    title = strings.authTwoFactorTitle,
                    hint = strings.authTwoFactorHint,
                    body = {
                        RvCodeInput(
                            value = code,
                            onValueChange = { code = it },
                            enabled = !busy,
                            error = codeError,
                            autofocus = here == AuthStep.TwoFactor,
                            onComplete = sendCode,
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = resendCode,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(RvSyncIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(strings.authTwoFactorResend, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    },
                )

                "step:${AuthStep.TrustedDevices.name}" -> AuthPage(
                    hero = avatar,
                    title = strings.authDeviceSelectionTitle,
                    hint = strings.authDeviceSelectionHint,
                    body = {
                        RvSection {
                            devices.forEachIndexed { index, device ->
                                RvSegment(
                                    index = index,
                                    count = devices.size,
                                    onClick = { pickDevice(index) },
                                    leading = {
                                        AuthDeviceBadge(
                                            art = device.art,
                                            tone = RenVault.accents.forIndex(index),
                                            size = 48.dp,
                                        )
                                    },
                                    supporting = { Text(device.model) },
                                    trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                                ) {
                                    Text(device.name)
                                }
                            }
                        }
                    },
                    actions = {},
                )

                "step:${AuthStep.DevicePasscode.name}" -> AuthPage(
                    hero = devices.getOrNull(chosen)?.art?.let { art ->
                        {
                            AuthDeviceBadge(
                                art = art,
                                tone = RenVault.accents.forIndex(chosen),
                                size = 112.dp,
                                arrive = true,
                            )
                        }
                    } ?: avatar,
                    title = strings.authDevicePasscodeTitle,
                    hint = strings.authDevicePasscodeHint(devices.getOrNull(chosen)?.model.orEmpty()),
                    body = {
                        Column(modifier = Modifier.shaken { passcodeShake.value }) {
                            RvTextField(
                                value = passcode,
                                onValueChange = { passcode = it },
                                label = strings.authDevicePasscodePlaceholder,
                                enabled = !busy,
                                secret = true,
                                leading = RvDevicesIcon,
                                error = passcodeError.ifEmpty { null },
                                focusRequester = passcodeFocus,
                                onDone = submitPasscode,
                            )
                        }
                    },
                    actions = {
                        RvButton(
                            text = strings.authContinueAction,
                            onClick = submitPasscode,
                            enabled = passcode.isNotEmpty(),
                        )
                    },
                )

                "step:${AuthStep.LocalChoice.name}" -> AuthPage(
                    hero = { AuthHero(RvCloudOffIcon, MaterialShapes.Cookie9Sided) },
                    title = strings.authLocalTitle,
                    hint = strings.authLocalHint,
                    body = {
                        RvSection {
                            RvSegment(
                                index = 0,
                                count = 2,
                                onClick = { chooseLocal(LocalMode.New) },
                                leading = { SettingBadge(RvAddIcon, RenVault.accents.blue) },
                                supporting = { Text(strings.authLocalNewDesc) },
                                trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                            ) {
                                Text(strings.authLocalNew)
                            }
                            RvSegment(
                                index = 1,
                                count = 2,
                                onClick = { chooseLocal(LocalMode.Join) },
                                leading = { SettingBadge(RvDevicesIcon, RenVault.accents.teal) },
                                supporting = { Text(strings.authLocalJoinDesc) },
                                trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                            ) {
                                Text(strings.authLocalJoin)
                            }
                        }
                    },
                    actions = {},
                )

                "step:${AuthStep.LocalProfile.name}" -> AuthPage(
                    hero = {
                        Box {
                            RvProfileAvatar(size = 112.dp, photo = profileFace, shape = MaterialShapes.Cookie12Sided.toShape())
                            if (pickPhoto != null) {
                                FilledTonalIconButton(
                                    onClick = pickPhoto,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                ) {
                                    Icon(RvAddPhotoIcon, contentDescription = strings.profilePhotoPick)
                                }
                            }
                        }
                    },
                    title = strings.authProfileTitle,
                    hint = strings.authProfileHint,
                    body = {
                        RvTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = strings.profileName,
                            leading = RvPersonIcon,
                            capitalization = KeyboardCapitalization.Words,
                            onDone = openMaster,
                        )
                    },
                    actions = {
                        RvButton(text = strings.authContinueAction, onClick = openMaster)
                    },
                )

                "step:${AuthStep.LocalJoin.name}" -> AuthPage(
                    hero = { AuthHero(if (scanJoin != null) RvQrScanIcon else RvKeyIcon, MaterialShapes.Gem) },
                    title = strings.authJoinTitle,
                    hint = if (scanJoin != null) strings.pairJoinHint else strings.pairJoinManualHint,
                    body = {
                        PairJoinForm(
                            code = joinCode,
                            onCode = { joinCode = it },
                            failure = joinError.ifEmpty { null },
                            onScan = scanJoin,
                            onSubmit = { submitJoin(joinCode) },
                            strings = strings,
                            enabled = !busy,
                        )
                    },
                    actions = {
                        RvButton(
                            text = strings.pairJoinAction,
                            onClick = { submitJoin(joinCode) },
                            enabled = joinCode.isNotBlank(),
                        )
                        restoreFlow?.restore?.let { restore ->
                            RvButton(
                                text = strings.authRestore,
                                onClick = restore,
                                kind = RvButtonKind.Tonal,
                                icon = RvRestoreIcon,
                                enabled = !busy,
                            )
                        }
                        TextButton(
                            onClick = onDone,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(strings.authJoinSkip)
                        }
                    },
                )

                "step:${AuthStep.MasterSetup.name}" -> AuthPage(
                    hero = { AuthHero(RvShieldIcon, MaterialShapes.Clover4Leaf) },
                    title = strings.authMasterSetupTitle,
                    hint = strings.authMasterSetupHint,
                    body = {
                        Column(
                            modifier = Modifier.shaken { masterShake.value },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RvTextField(
                                value = master,
                                onValueChange = { master = it },
                                label = strings.authMasterPlaceholder,
                                enabled = !busy,
                                secret = true,
                                shown = masterShown,
                                onShownChange = { masterShown = it },
                                leading = RvKeyIcon,
                                focusRequester = masterFocus,
                            )

                            val level = strengthOf(master)
                            AnimatedVisibility(visible = master.isNotEmpty()) {
                                RvStrengthMeter(
                                    strength = level,
                                    label = when (level) {
                                        RvPasswordStrength.Weak -> strings.strengthWeak
                                        RvPasswordStrength.Fair -> strings.strengthFair
                                        RvPasswordStrength.Strong -> strings.strengthStrong
                                        RvPasswordStrength.None -> null
                                    },
                                )
                            }

                            RvTextField(
                                value = masterRepeat,
                                onValueChange = { masterRepeat = it },
                                label = strings.authMasterRepeatPlaceholder,
                                enabled = !busy,
                                secret = true,
                                shown = masterShown,
                                onShownChange = { masterShown = it },
                                leading = RvKeyIcon,
                                error = masterHint.ifEmpty { null },
                                onDone = submitMaster,
                            )
                        }
                    },
                    actions = {
                        RvButton(
                            text = strings.authContinueAction,
                            onClick = submitMaster,
                            enabled = masterReady,
                        )
                    },
                )

                else -> Box(Modifier.fillMaxSize())
            }
        }

        AnimatedVisibility(
            visible = canGoBack,
            modifier = Modifier.padding(8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FilledTonalIconButton(onClick = goBack, shapes = IconButtonDefaults.shapes()) {
                Icon(RvBackIcon, contentDescription = strings.navBack)
            }
        }

        RvThemeMenuButton(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))

        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(tween(VeilMs.toInt())),
            exit = fadeOut(tween(VeilMs.toInt())),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(96.dp))

                Text(
                    text = busyTitle,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                )

                Text(
                    text = busyLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }

    RvAnisetteDialog(
        open = anisetteOpen,
        onOpenChange = { anisetteOpen = it },
        current = anisette,
        strings = strings,
        onSave = onAnisette,
    )
}

@Composable
private fun AuthPage(
    hero: @Composable () -> Unit,
    title: String,
    hint: String?,
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val heading: @Composable (Alignment.Horizontal, TextAlign) -> Unit = { align, textAlign ->
        Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            hero()
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
            )
            if (!hint.isNullOrEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign,
                )
            }
        }
    }

    if (isCompact()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(modifier = Modifier.widthIn(max = BodyMax).fillMaxWidth(), contentAlignment = Alignment.Center) {
                heading(Alignment.CenterHorizontally, TextAlign.Center)
            }
            Column(
                modifier = Modifier.widthIn(max = BodyMax).fillMaxWidth().padding(top = 32.dp),
                content = body,
            )
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.widthIn(max = BodyMax).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = actions,
            )
            if (extra != null) {
                Box(
                    modifier = Modifier.widthIn(max = BodyMax).fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { extra() }
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().imePadding().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(40.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    heading(Alignment.Start, TextAlign.Start)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(content = body)
                    Spacer(Modifier.height(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = actions)
                    if (extra != null) {
                        Box(modifier = Modifier.padding(top = 8.dp)) { extra() }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthHero(icon: ImageVector, polygon: RoundedPolygon) {
    val colors = MaterialTheme.colorScheme
    val arrival = remember { Animatable(0f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(Unit) { arrival.animateTo(1f, spring) }

    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -60f * (1f - arrival.value)
                    val scale = 0.6f + 0.4f * arrival.value
                    scaleX = scale
                    scaleY = scale
                }
                .clip(polygon.toShape())
                .background(colors.primaryContainer),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun AuthDeviceBadge(
    art: ImageBitmap?,
    tone: RvTone,
    size: Dp,
    arrive: Boolean = false,
) {
    if (art == null) {
        RvShapeBadge(
            icon = RvDevicesIcon,
            tone = tone,
            polygon = MaterialShapes.Cookie6Sided,
            size = size,
        )
        return
    }

    val arrival = remember { Animatable(if (arrive) 0f else 1f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(Unit) { arrival.animateTo(1f, spring) }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -60f * (1f - arrival.value)
                    val scale = 0.6f + 0.4f * arrival.value
                    scaleX = scale
                    scaleY = scale
                }
                .clip(MaterialShapes.Cookie6Sided.toShape())
                .background(tone.container),
        )
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size * DeviceArtFill)
                .graphicsLayer {
                    val scale = 0.8f + 0.2f * arrival.value
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun PrivacyNotice(strings: RvStrings, onPrivacy: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    val link = remember(colors.primary) {
        val face = SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium)
        TextLinkStyles(style = face, focusedStyle = face, hoveredStyle = face, pressedStyle = face)
    }

    val body = buildAnnotatedString {
        append(strings.authPrivacyNotice)
        append(" ")
        withLink(LinkAnnotation.Clickable(tag = "privacy", styles = link) { onPrivacy() }) {
            append(strings.authPrivacyLink)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = RvVerifiedIcon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
