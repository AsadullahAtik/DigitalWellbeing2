package com.wellbeing.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.StrictRules
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings) {
    var askDisableStrict by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("সেটিংস", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { SectionTitle("সতর্কতা") }
        item {
            WCard {
                SwitchRow("Notification", "৮০% এ সতর্কবার্তা", s.notify) { vm.setNotify(it) }
                SwitchRow("Alarm স্ক্রিন", "১০০% এ পুরো স্ক্রিনে সতর্কতা", s.alarm) { vm.setAlarm(it) }
                SwitchRow("Alarm-এর শব্দ", null, s.alarmSound, enabled = s.alarm) { vm.setAlarmSound(it) }
            }
        }
        item { SectionTitle("Pause স্ক্রিন") }
        item {
            WCard {
                SwitchRow("অ্যাপ খোলার আগে থামো", "Facebook, TikTok, YouTube খুললে কয়েক সেকেন্ড অপেক্ষা", s.pauseEnabled) { vm.setPauseEnabled(it) }
                if (s.pauseEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("অপেক্ষা: ${s.pauseSeconds} সেকেন্ড")
                        FilledTonalIconButton(onClick = { vm.setPauseSeconds(s.pauseSeconds - 5) }) { Text("−") }
                        FilledTonalIconButton(onClick = { vm.setPauseSeconds(s.pauseSeconds + 5) }) { Text("+") }
                    }
                }
            }
        }
        item { SectionTitle("Strict Mode") }
        item {
            WCard {
                SwitchRow(
                    "Strict Mode",
                    "সীমা শেষ হলে App আর খোলা যাবে না। চালু থাকলে সীমা শুধু কমানো যাবে, Snooze থাকবে না। বন্ধ করতে ${StrictRules.DISABLE_WAIT_SECONDS} সেকেন্ড অপেক্ষা করতে হবে।",
                    s.strict,
                ) { on -> if (on) vm.setStrict(true) else askDisableStrict = true }
            }
        }
        item { SectionTitle("অনুমতি") }
        item { SetupChecklist(showWhenDone = true) }
    }

    if (askDisableStrict) {
        var left by remember { mutableIntStateOf(StrictRules.DISABLE_WAIT_SECONDS) }
        LaunchedEffect(Unit) { while (left > 0) { delay(1_000); left-- } }
        AlertDialog(
            onDismissRequest = { askDisableStrict = false },
            title = { Text("Strict Mode বন্ধ করবে?") },
            text = { Text("সীমা এড়ানোর জন্য বন্ধ করছো না তো? ${StrictRules.DISABLE_WAIT_SECONDS} সেকেন্ড অপেক্ষা করলে তবেই বন্ধ করতে পারবে।") },
            confirmButton = {
                TextButton(enabled = left == 0, onClick = { vm.setStrict(false); askDisableStrict = false }) {
                    Text(if (left == 0) "বন্ধ করো" else "অপেক্ষা ($left)")
                }
            },
            dismissButton = { TextButton(onClick = { askDisableStrict = false }) { Text("চালু থাকুক") } },
        )
    }
}

