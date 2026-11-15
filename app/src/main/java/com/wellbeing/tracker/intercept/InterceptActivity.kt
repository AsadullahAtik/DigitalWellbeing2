package com.wellbeing.tracker.intercept

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wellbeing.tracker.MainActivity
import com.wellbeing.tracker.data.repo.LimitRepository
import com.wellbeing.tracker.data.repo.SettingsRepository
import com.wellbeing.tracker.data.repo.StatsRepository
import com.wellbeing.tracker.domain.AlertTexts
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.BlockReason
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.Format
import com.wellbeing.tracker.domain.LimitEvaluator
import com.wellbeing.tracker.domain.LimitKeys
import com.wellbeing.tracker.domain.PauseReasons
import com.wellbeing.tracker.domain.SnoozePolicy
import com.wellbeing.tracker.domain.TrackedApps
import com.wellbeing.tracker.engine.WellbeingEngine
import com.wellbeing.tracker.service.AlarmPlayer
import com.wellbeing.tracker.service.Permissions
import com.wellbeing.tracker.service.PolicyGate
import com.wellbeing.tracker.ui.theme.Over
import com.wellbeing.tracker.ui.theme.WellbeingTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PauseContext(val line: String, val over: Boolean)

@HiltViewModel
class InterceptViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val limits: LimitRepository,
    private val stats: StatsRepository,
    private val engine: WellbeingEngine,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Uses one of today's snoozes and moves the limit; returns false if not allowed (for example Strict Mode). */
    suspend fun snooze(limitKey: String): Boolean {
        if (!settingsRepo.consumeSnooze()) return false
        limits.addSnoozeBonus(limitKey, SnoozePolicy.bonusFor(LimitKeys.typeOf(limitKey)))
        return true
    }

    suspend fun pauseContext(appId: String, accessibilityOn: Boolean): PauseContext {
        val st = engine.loadState()
        val minutes = st.usage.appMinutes[appId] ?: 0
        val first = TrackedApps.byId(appId)?.contentKinds?.firstOrNull()
        val extra = if (first != null && accessibilityOn) " · ${st.counts[first] ?: 0}টি ${first.unit}" else ""
        return PauseContext("আজ এখানে ${Format.minutes(minutes)} গেছে$extra", LimitEvaluator.appLimitsOver(appId, st.statuses))
    }

    /** Returns how many times the user turned back today. */
    suspend fun recordTurnedBack(): Int {
        stats.addPauseSaved()
        return stats.today().pauseSaved
    }

    suspend fun recordReason(reasonKey: String?) {
        if (reasonKey != null) stats.addReason(reasonKey)
    }
}

/** Full-screen Pause, Block and Alarm screens, shown over the app the user opened. */
@AndroidEntryPoint
class InterceptActivity : ComponentActivity() {
    private val vm: InterceptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = intent.getStringExtra(InterceptContract.EXTRA_MODE) ?: InterceptContract.MODE_BLOCK
        val appId = intent.getStringExtra(InterceptContract.EXTRA_APP_ID)
        val reason = intent.getStringExtra(InterceptContract.EXTRA_REASON)
        val limitKey = intent.getStringExtra(InterceptContract.EXTRA_LIMIT_KEY)
        val seconds = intent.getIntExtra(InterceptContract.EXTRA_SECONDS, 10)
        val current = intent.getIntExtra(InterceptContract.EXTRA_CURRENT, 0)
        val effective = intent.getIntExtra(InterceptContract.EXTRA_EFFECTIVE, 0)

        setContent {
            WellbeingTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler { goHome() }
                    when (mode) {
                        InterceptContract.MODE_PAUSE -> PauseScreen(
                            appId = appId, seconds = seconds, vm = vm,
                            onContinue = { appId?.let { PolicyGate.allow(it) }; finish() },
                            onLeave = { count ->
                                Toast.makeText(this, "ভালো সিদ্ধান্ত। আজ $count বার Pause-এ ফিরে এসেছো।", Toast.LENGTH_LONG).show()
                                goHome()
                            },
                        )
                        InterceptContract.MODE_ALARM -> AlarmScreen(
                            limitKey = limitKey, current = current, effective = effective, vm = vm,
                            onDone = { goHome() }, onSnoozed = { AlarmPlayer.stop(); finish() }, onSeeLimits = { openLimits() },
                        )
                        else -> BlockScreen(appId, reason, remainingFocusText(), onLeave = { goHome() }, onSeeLimits = { openLimits() })
                    }
                }
            }
        }
    }

    private fun remainingFocusText(): String? {
        val s = vm.settings.value
        val now = System.currentTimeMillis()
        return if (s.focusActive(now)) Format.mmss(((s.focusEndsAt - now) / 1000).toInt()) else null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        if (intent.getStringExtra(InterceptContract.EXTRA_MODE) == InterceptContract.MODE_ALARM) AlarmPlayer.stop()
        super.onDestroy()
    }

    private fun goHome() {
        AlarmPlayer.stop()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openLimits() {
        AlarmPlayer.stop()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_LIMITS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun PauseScreen(appId: String?, seconds: Int, vm: InterceptViewModel, onContinue: () -> Unit, onLeave: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val name = appId?.let { TrackedApps.byId(it)?.label } ?: "অ্যাপ"
    val scope = rememberCoroutineScope()
    var left by remember { mutableIntStateOf(seconds) }
    var reason by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<PauseContext?>(null) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1_000); left-- } }
    LaunchedEffect(appId) { if (appId != null) info = vm.pauseContext(appId, Permissions.accessibility(context)) }

    Centered {
        Text("Pause", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("$name খোলার আগে একটু থামো", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.line, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (it.over) Text("আজকের সীমা পার হয়ে গেছে।", color = Over, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (left > 0) "$left" else "✓", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text("শ্বাস নাও। কেন খুলছো?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PauseReasons.all.take(2).forEach { (k, label) -> FilterChip(selected = reason == k, onClick = { reason = k }, label = { Text(label) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PauseReasons.all.drop(2).forEach { (k, label) -> FilterChip(selected = reason == k, onClick = { reason = k }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { scope.launch { onLeave(vm.recordTurnedBack()) } }, modifier = Modifier.fillMaxWidth()) { Text("না, থাক") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { scope.launch { vm.recordReason(reason); onContinue() } },
            enabled = left == 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (left == 0) "তবু খুলব" else "তবু খুলব ($left)") }
    }
}

@Composable
private fun BlockScreen(appId: String?, reason: String?, focusLeft: String?, onLeave: () -> Unit, onSeeLimits: () -> Unit) {
    val name = appId?.let { TrackedApps.byId(it)?.label } ?: "এই অ্যাপ"
    val why = runCatching { BlockReason.valueOf(reason ?: "") }.getOrNull()
    val title = when (why) {
        BlockReason.FOCUS -> "$name ফোকাসের সময় বন্ধ"
        BlockReason.BEDTIME -> "এখন ঘুমের সময়"
        BlockReason.LIMIT -> "$name এখন লক করা"
        null -> "$name এখন বন্ধ"
    }
    val body = when (why) {
        BlockReason.FOCUS -> "ফোকাস চলছে" + (focusLeft?.let { ", $it বাকি" } ?: "") + "। শেষ হলে খুলবে।"
        BlockReason.BEDTIME -> "ঘুমের সময় $name বন্ধ। সকালে দেখা হবে।"
        BlockReason.LIMIT -> "আজকের সীমা শেষ। Strict Mode চালু আছে, তাই আজ আর খোলা যাবে না। কাল সকালে আবার খুলবে।"
        null -> "$name এখন খোলা যাবে না।"
    }
    Centered {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("ঠিক আছে") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSeeLimits, modifier = Modifier.fillMaxWidth()) { Text("সীমা দেখো") }
    }
}

@Composable
private fun AlarmScreen(
    limitKey: String?,
    current: Int,
    effective: Int,
    vm: InterceptViewModel,
    onDone: () -> Unit,
    onSnoozed: () -> Unit,
    onSeeLimits: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val key = limitKey ?: LimitKeys.SCREEN
    val type = LimitKeys.typeOf(key)
    val text = AlertTexts.over(key, current, effective)
    val left = s.snoozeLeft(DateUtil.today())
    val canSnooze = SnoozePolicy.canSnooze(s.strict, left)
    var denied by remember { mutableStateOf(false) }

    Centered {
        Text(text.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Over)
        Spacer(Modifier.height(12.dp))
        Text(text.body, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!canSnooze) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.strict) "Strict Mode চালু, তাই Snooze নেই।" else "আজকের Snooze শেষ।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("বন্ধ করছি") }
        if (canSnooze) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { if (vm.snooze(key)) onSnoozed() else denied = true } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("আরও ${SnoozePolicy.label(type)} · বাকি $left") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSeeLimits, modifier = Modifier.fillMaxWidth()) { Text("সীমা দেখো") }
        if (denied) Text("Snooze এখন পাওয়া যাচ্ছে না", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
