package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.panel.HealthLevel
import dev.gnacho.netgrip.domain.panel.HealthScore
import dev.gnacho.netgrip.domain.panel.HealthScoreCalculator
import dev.gnacho.netgrip.domain.panel.HealthTone
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.ShellTab
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.GroupLabel
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.login.PlatformBackHandler
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private data class NavEntry(val tab: ShellTab, val icon: ImageVector, val group: String)

private val Navigation = listOf(
    NavEntry(ShellTab.PANEL, NetGripIcons.Panel, "Resumen"),
    NavEntry(ShellTab.CLIENTS, NetGripIcons.Clients, "Tu red"),
    NavEntry(ShellTab.WIFI, NetGripIcons.Wifi, "Tu red"),
    NavEntry(ShellTab.PORTS, NetGripIcons.Ports, "Tu red"),
    NavEntry(ShellTab.SETTINGS, NetGripIcons.Settings, "Sistema"),
)

private val DrawerWidth = 252.dp
private val RailWidth = 76.dp
private val MaxContentWidth = 1100.dp

/**
 * Shell con sesion iniciada. En movil la navegacion es un menu lateral
 * desplegable, como el Drawer del panel web. En tablet se convierte en un rail
 * de iconos y en pantallas anchas en un menu lateral fijo.
 */
@Composable
fun ShellScreen(
    sessionViewModel: SessionViewModel,
    platformBackHandler: PlatformBackHandler = { _, _ -> },
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val api = sessionViewModel.api ?: return
    val viewModel = remember(api) { ShellViewModel(api) }
    var tab by remember { mutableStateOf(ShellTab.PANEL) }
    val panel by viewModel.panel.collectAsState()
    val sessionExpired by viewModel.sessionExpired.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) sessionViewModel.onSessionExpired()
    }

    val health = remember(panel.system, panel.wan, panel.mode) {
        HealthScoreCalculator.compute(panel.system, panel.wan, panel.mode)
    }
    val host = panel.board?.hostname?.takeIf { it.isNotBlank() }
        ?: hostFromUrl(sessionViewModel.baseUrl)

    fun select(target: ShellTab) {
        tab = target
        scope.launch { drawerState.close() }
    }

    platformBackHandler(drawerState.isOpen || tab != ShellTab.PANEL) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else tab = ShellTab.PANEL
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val permanent = maxWidth >= 1000.dp
        val rail = maxWidth >= 720.dp && !permanent

        val navigation: @Composable (Boolean) -> Unit = { labels ->
            NavigationList(
                selected = tab,
                host = host,
                health = health,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                showLabels = labels,
                onSelect = ::select,
                onLogout = { sessionViewModel.logout() },
            )
        }

        val content: @Composable (Boolean) -> Unit = { showMenu ->
            ShellContent(
                tab = tab,
                host = host,
                health = health,
                showMenu = showMenu,
                darkTheme = themeMode == ThemeMode.DARK,
                onMenu = { scope.launch { drawerState.open() } },
                onRefresh = { viewModel.refresh(tab) },
                onThemeToggle = {
                    onThemeModeChange(if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
                },
                onLogout = { sessionViewModel.logout() },
            ) {
                when (tab) {
                    ShellTab.PANEL -> PanelScreen(viewModel)
                    ShellTab.CLIENTS -> ClientsScreen(viewModel)
                    ShellTab.WIFI -> WifiScreen(viewModel)
                    ShellTab.PORTS -> PortsScreen(viewModel)
                    ShellTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        baseUrl = sessionViewModel.baseUrl,
                        onLogout = { sessionViewModel.logout() },
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
            }
        }

        when {
            permanent || rail -> Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(if (permanent) DrawerWidth else RailWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        navigation(permanent)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                content(false)
            }
            else -> ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    ) {
                        navigation(true)
                    }
                },
            ) {
                content(true)
            }
        }
    }
}

@Composable
private fun ShellContent(
    tab: ShellTab,
    host: String,
    health: HealthScore,
    showMenu: Boolean,
    darkTheme: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(NetGripIcons.Menu, contentDescription = "Abrir menú", modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$host · ${healthSummary(health)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(NetGripIcons.Refresh, contentDescription = "Actualizar", modifier = Modifier.size(20.dp))
            }
            if (showMenu) {
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        if (darkTheme) NetGripIcons.Sun else NetGripIcons.Moon,
                        contentDescription = "Cambiar tema",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onLogout) {
                Icon(
                    NetGripIcons.Logout,
                    contentDescription = "Cerrar sesión",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NavigationList(
    selected: ShellTab,
    host: String,
    health: HealthScore,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showLabels: Boolean,
    onSelect: (ShellTab) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = if (showLabels) 12.dp else 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NetGripIcons.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (showLabels) {
                Column {
                    Text(
                        "NetGrip",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(host, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Navigation.groupBy { it.group }.forEach { (group, entries) ->
                if (showLabels) {
                    GroupLabel(group)
                } else {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                entries.forEach { entry ->
                    NavigationItem(
                        entry = entry,
                        selected = entry.tab == selected,
                        showLabel = showLabels,
                        onClick = { onSelect(entry.tab) },
                    )
                }
            }
        }

        if (showLabels) {
            Pill(
                text = healthSummary(health),
                tone = healthTone(health),
                dot = true,
            )
            Spacer(Modifier.height(12.dp))
            SegmentedControl(
                options = listOf(
                    ThemeMode.SYSTEM to "Auto",
                    ThemeMode.LIGHT to "Claro",
                    ThemeMode.DARK to "Oscuro",
                ),
                selected = themeMode,
                onSelect = onThemeModeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        } else {
            IconButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    NetGripIcons.Logout,
                    contentDescription = "Cerrar sesión",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    entry: NavEntry,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(10.dp) else Arrangement.Center,
    ) {
        if (showLabel) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
        }
        Icon(entry.icon, contentDescription = entry.tab.label, modifier = Modifier.size(18.dp), tint = contentColor)
        if (showLabel) {
            Text(
                entry.tab.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun healthTone(health: HealthScore): Tone = when (health.tone) {
    HealthTone.OK -> Tone.SUCCESS
    HealthTone.WARN -> Tone.WARN
    HealthTone.DANGER -> Tone.DANGER
}

private fun healthSummary(health: HealthScore): String = when (health.level) {
    HealthLevel.EXCELLENT -> "Todo en orden"
    HealthLevel.GOOD -> "Funciona bien"
    HealthLevel.ATTENTION -> "${health.reasons.size} cosas a revisar"
    HealthLevel.CRITICAL -> "Requiere atención"
}

private fun hostFromUrl(url: String?): String {
    if (url.isNullOrBlank()) return "Sin conexión"
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .substringBefore('/')
        .substringBefore(':')
        .ifBlank { "Sin conexión" }
}
