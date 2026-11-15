package com.wellbeing.tracker.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import com.wellbeing.tracker.service.Permissions
import com.wellbeing.tracker.ui.theme.Ok
import com.wellbeing.tracker.ui.theme.Warn

private data class Step(val title: String, val why: String, val required: Boolean, val done: Boolean, val action: () -> Unit)

/**
 * Non-blocking setup list. Android needs these permissions to be granted by the user
 * in system screens, so they cannot be skipped, but the app is usable while they are missing.
 * [showWhenDone] keeps it visible in Settings; Today hides it once everything is set.
 */
@Composable
fun SetupChecklist(showWhenDone: Boolean) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Reading the counter here makes the list re-check permissions after returning from system settings.
    val checkedAt = refresh
    var askAccessibility by remember { mutableStateOf(false) }
    fun open(i: Intent) = context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    val steps = listOf(
        Step("Usage Access", "স্ক্রিন টাইম ও অ্যাপ ব্যবহার পড়ার জন্য। এটা ছাড়া কিছুই ট্র্যাক হয় না।", true,
            Permissions.usageAccess(context)) { open(Permissions.usageAccessIntent()) },
        Step("অন্য অ্যাপের ওপর দেখানো", "Pause, Block আর Alarm স্ক্রিন অ্যাপের ওপর আনার জন্য।", true,
            Permissions.overlay(context)) { open(Permissions.overlayIntent(context)) },
        Step("Notification", "৮০% আর ১০০% সতর্কবার্তার জন্য।", true,
            Permissions.notifications(context)) { notifLauncher.launch("android.permission.POST_NOTIFICATIONS") },
        Step("Accessibility (ঐচ্ছিক)", "Reels, Shorts আর ভিডিও গোনার জন্য। শুধু Facebook, TikTok, YouTube দেখে, কিছু পড়ে বা সংরক্ষণ করে না।", false,
            Permissions.accessibility(context)) { askAccessibility = true },
        Step("ব্যাটারি সীমাহীন (প্রস্তাবিত)", "কিছু ফোন ব্যাকগ্রাউন্ডে অ্যাপ বন্ধ করে দেয়; এটা দিলে ট্র্যাকিং চলতে থাকে।", false,
            Permissions.batteryUnrestricted(context)) { open(Permissions.batteryIntent(context)) },
    )
    val allDone = steps.all { it.done }
    if (allDone && !showWhenDone) return

    WCard {
        Text(if (allDone) "সেটআপ সম্পূর্ণ" else "সেটআপ বাকি আছে", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        steps.forEach { step ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (step.done) Text("✓", color = Ok, style = MaterialTheme.typography.titleMedium)
                else OutlinedButton(onClick = step.action) { Text(if (step.required) "চালু করো" else "খোলো", color = if (step.required) Warn else Color.Unspecified) }
            }
        }
    }

    if (askAccessibility) {
        AlertDialog(
            onDismissRequest = { askAccessibility = false },
            title = { Text("Accessibility Service কেন লাগে") },
            text = {
                Text(
                    "Facebook, TikTok ও YouTube-এ Reels বা ভিডিও স্ক্রল হচ্ছে কি না, শুধু তা গোনার জন্য।\n\n" +
                        "এটা তোমার মেসেজ, পাসওয়ার্ড বা App-এর ভেতরের লেখা পড়ে না। শুধু সংখ্যা ফোনে জমা রাখে, ইন্টারনেটে কিছু পাঠায় না।\n\n" +
                        "না দিলে শুধু সময়ের হিসাব ও সীমা কাজ করবে।"
                )
            },
            confirmButton = {
                TextButton(onClick = { askAccessibility = false; open(Permissions.accessibilityIntent()) }) { Text("রাজি, সেটিংস খোলো") }
            },
            dismissButton = { TextButton(onClick = { askAccessibility = false }) { Text("এখন না") } },
        )
    }
}

