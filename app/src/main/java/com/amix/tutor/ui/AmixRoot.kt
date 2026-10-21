package com.amix.tutor.ui

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amix.tutor.ChatViewModel
import com.amix.tutor.CrashReporter
import com.amix.tutor.R
import com.amix.tutor.auth.AmixUser
import com.amix.tutor.data.ChatEntity
import com.amix.tutor.ui.theme.AmixBg
import com.amix.tutor.ui.theme.AmixLine
import com.amix.tutor.ui.theme.AmixMuted
import com.amix.tutor.ui.theme.AmixPanel
import com.amix.tutor.ui.theme.AmixPanel2
import com.amix.tutor.ui.theme.AmixText
import com.amix.tutor.ui.theme.AmixTheme
import java.util.Locale
import kotlinx.coroutines.launch

object Routes {
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val VOICE = "voice"
}

/** Активность-хозяин: нужна для входа через браузер (LocalContext ниже подменяется на локализованный). */
val LocalHostActivity = staticCompositionLocalOf<Activity?> { null }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Язык выбирается в приложении (RU/EN), независимо от языка системы.
 * Контекст подменяется обёрткой вокруг Activity: ресурсы на нужном языке, а Activity остаётся в цепочке
 * контекстов. Иначе функциям, которым нужна Activity (окно запроса разрешения микрофона), её не найти
 * и приложение закрывается при входе на экран голоса.
 */
@Composable
fun ProvideLanguage(lang: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized: Context = remember(lang, base) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        val configured = base.createConfigurationContext(config)
        object : ContextWrapper(base) {
            override fun getResources() = configured.resources
        }
    }
    val activity = base.findActivity()
    val resultOwner = activity as? ActivityResultRegistryOwner
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalHostActivity provides activity
    ) {
        if (resultOwner != null) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides resultOwner) { content() }
        } else {
            content()
        }
    }
}

@Composable
fun AmixRoot(
    appVm: AppViewModel = hiltViewModel(),
    chatVm: ChatViewModel = hiltViewModel()
) {
    val lang by appVm.language.collectAsStateWithLifecycle()
    ProvideLanguage(lang) {
        AmixTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = AmixBg, contentColor = AmixText) {
                AmixShell(appVm, chatVm, lang)
                CrashDialog()
            }
        }
    }
}

@Composable
private fun AmixShell(appVm: AppViewModel, chatVm: ChatViewModel, lang: String) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.CHAT
    val voiceMode = route == Routes.VOICE
    val chats by appVm.chats.collectAsStateWithLifecycle()
    val user by appVm.user.collectAsStateWithLifecycle()
    val proUntil by appVm.proUntil.collectAsStateWithLifecycle()
    val activity = LocalHostActivity.current
    var showAccount by remember { mutableStateOf(false) }

    fun go(target: String) {
        scope.launch { drawer.close() }
        nav.navigate(target) {
            launchSingleTop = true
            popUpTo(Routes.CHAT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(
                route = route,
                chats = chats,
                onClose = { scope.launch { drawer.close() } },
                onGo = { go(it) },
                onNewChat = {
                    chatVm.newChat()
                    go(Routes.CHAT)
                },
                onOpenChat = { id ->
                    chatVm.openChat(id)
                    go(Routes.CHAT)
                },
                user = user,
                onAccount = {
                    scope.launch { drawer.close() }
                    showAccount = true
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize().background(if (voiceMode) Color.Black else AmixBg)) {
            // звёзды и виньетка везде; «чёрная дыра» только на экране голоса
            BlackHoleBackground(voiceMode)
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                if (!voiceMode) {
                    TopBar(
                        lang = lang,
                        user = user,
                        onMenu = { scope.launch { drawer.open() } },
                        onLang = { appVm.toggleLanguage() },
                        onAccount = { showAccount = true }
                    )
                }
                NavHost(
                    navController = nav,
                    startDestination = Routes.CHAT,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.CHAT) { ChatScreen(chatVm) }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            chats = chats,
                            onOpen = { id ->
                                chatVm.openChat(id)
                                go(Routes.CHAT)
                            },
                            onClear = { chatVm.clearAllChats() }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            lang = lang,
                            onLang = { appVm.setLanguage(it) },
                            onWipe = { chatVm.clearAllChats() }
                        )
                    }
                    composable(Routes.PRO) {
                        ProScreen(
                            proUntil = proUntil,
                            signedIn = user != null,
                            onRedeem = { appVm.redeem(it) },
                            onNeedLogin = { showAccount = true }
                        )
                    }
                    composable(Routes.VOICE) { VoiceScreen(onBack = { go(Routes.CHAT) }) }
                }
            }
            if (voiceMode) {
                Box(Modifier.systemBarsPadding().padding(start = 13.dp, top = 13.dp)) {
                    BrandPill(onClick = { scope.launch { drawer.open() } })
                }
            }
        }
    }

    if (showAccount) {
        AccountDialog(
            user = user,
            proUntil = proUntil,
            onDismiss = { showAccount = false },
            onGoogle = {
                if (activity != null) appVm.signInGoogle(activity)
                else com.amix.tutor.auth.SignInOutcome.Failure(com.amix.tutor.auth.AuthErrorKind.OTHER, "no activity")
            },
            onEmailIn = { e, pw -> appVm.signInEmail(e, pw) },
            onEmailNew = { e, pw -> appVm.createEmail(e, pw) },
            onSignOut = { appVm.signOut() }
        )
    }
}

/** Окно с отчётом о сбое: показывается при следующем запуске, текст можно скопировать. */
@Composable
private fun CrashDialog() {
    val report by CrashReporter.report.collectAsStateWithLifecycle()
    val text = report ?: return
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { CrashReporter.dismiss() },
        containerColor = AmixPanel,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.crash_text), color = AmixMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                Text(stringResource(R.string.crash_copy), color = AmixText)
            }
        },
        dismissButton = {
            TextButton(onClick = { CrashReporter.dismiss() }) {
                Text(stringResource(R.string.close), color = AmixMuted)
            }
        }
    )
}

/** В режиме голоса вместо верхней панели остаётся «таблетка» с логотипом (как .brand в voice-mode). */
@Composable
private fun BrandPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Color(0x59050505))
            .border(1.dp, Color(0x16FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AmixLogo(40.dp)
        Spacer(Modifier.width(10.dp))
        Text("Amix", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopBar(lang: String, user: AmixUser?, onMenu: () -> Unit, onLang: () -> Unit, onAccount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onMenu)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AmixLogo(40.dp)
            Spacer(Modifier.width(12.dp))
            Text("Amix", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        CircleButton(text = lang.uppercase(), onClick = onLang)
        Spacer(Modifier.width(8.dp))
        AccountButton(user = user, onClick = onAccount)
    }
}

@Composable
private fun CircleButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AmixPanel2)
            .border(1.dp, AmixLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerContent(
    route: String,
    chats: List<ChatEntity>,
    onClose: () -> Unit,
    onGo: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (Long) -> Unit,
    user: AmixUser?,
    onAccount: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = AmixPanel,
        drawerContentColor = AmixText
    ) {
        Column(Modifier.fillMaxHeight().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmixLogo(40.dp)
                Spacer(Modifier.width(12.dp))
                Text("Amix", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "✕",
                    color = AmixMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(10.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.new_chat), color = AmixText)
            }
            Spacer(Modifier.height(8.dp))
            DrawerItem("✧", stringResource(R.string.nav_chat), route == Routes.CHAT, null) { onGo(Routes.CHAT) }
            DrawerItem("＋", stringResource(R.string.new_chat), false, null, onNewChat)
            DrawerItem("◉", stringResource(R.string.nav_voice), route == Routes.VOICE, stringResource(R.string.voice_soon_short)) { onGo(Routes.VOICE) }
            DrawerItem("◷", stringResource(R.string.nav_history), route == Routes.HISTORY, null) { onGo(Routes.HISTORY) }
            DrawerItem("⚙", stringResource(R.string.nav_settings), route == Routes.SETTINGS, null) { onGo(Routes.SETTINGS) }
            DrawerItem("✦", stringResource(R.string.nav_pro), route == Routes.PRO, null) { onGo(Routes.PRO) }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recent_chats),
                color = AmixMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(chats, key = { it.id }) { chat ->
                    Text(
                        chat.title,
                        maxLines = 1,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenChat(chat.id) }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { /* документы появятся на этапе «Документы» */ }) {
                    Text(stringResource(R.string.privacy), color = AmixMuted, fontSize = 13.sp)
                }
                if (user == null) {
                    Button(
                        onClick = onAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AmixBg)
                    ) {
                        Text(stringResource(R.string.login), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onAccount).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AmixAvatar(user, 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(user.name ?: user.email.orEmpty(), maxLines = 1, fontSize = 13.sp, modifier = Modifier.widthIn(max = 120.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: String, label: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) AmixPanel2 else Color.Transparent)
            .border(1.dp, if (selected) AmixLine else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.width(30.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (badge != null) Chip(badge)
    }
}
