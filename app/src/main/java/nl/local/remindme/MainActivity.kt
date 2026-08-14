package nl.local.remindme

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/* ----------------------------------------------------------------- */
/*  Palette                                                           */
/* ----------------------------------------------------------------- */

private val Ink = Color(0xFF12161C)
private val Slab = Color(0xFF1A1F27)
private val Edge = Color(0xFF2B333F)
private val Mist = Color(0xFF8794A5)
private val Chalk = Color(0xFFECF1F7)
private val Amber = Color(0xFFF2A33C)
private val Rose = Color(0xFFE0808F)
private val Chip = Color(0xFF232B35)
private val Faded = Color(0xFF525D6C)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1206),
    background = Ink,
    onBackground = Chalk,
    surface = Slab,
    onSurface = Chalk,
    surfaceVariant = Slab,
    onSurfaceVariant = Mist,
    outline = Edge,
    error = Rose
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        Scheduler.reschedule(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Surface(color = Ink) { App() }
            }
        }
    }
}

/* ----------------------------------------------------------------- */
/*  Navigation                                                        */
/* ----------------------------------------------------------------- */

private sealed interface Screen {
    data object Main : Screen
    data object Week : Screen
    data class Edit(val reminder: Reminder?) : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(Store.load(context)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var resumeTick by remember { mutableIntStateOf(0) }

    // Permissions and the clock are re-read every time the app returns to the front.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun commit(next: Config) {
        config = next
        Store.save(context, next)
        Scheduler.reschedule(context)
    }

    when (val current = screen) {
        Screen.Main -> MainScreen(
            config = config,
            resumeTick = resumeTick,
            onCommit = { commit(it) },
            onEdit = { screen = Screen.Edit(it) },
            onWeek = { screen = Screen.Week }
        )

        Screen.Week -> WeekScreen(
            config = config,
            onCommit = { commit(it) },
            onBack = { screen = Screen.Main }
        )

        is Screen.Edit -> EditScreen(
            original = current.reminder,
            onSave = { saved ->
                val list = if (config.reminders.any { it.id == saved.id }) {
                    config.reminders.map { if (it.id == saved.id) saved else it }
                } else {
                    config.reminders + saved
                }
                commit(config.copy(reminders = list))
                screen = Screen.Main
            },
            onDelete = { id ->
                commit(config.copy(reminders = config.reminders.filterNot { it.id == id }))
                screen = Screen.Main
            },
            onBack = { screen = Screen.Main }
        )
    }
}

/* ----------------------------------------------------------------- */
/*  Main screen                                                       */
/* ----------------------------------------------------------------- */

@Composable
private fun MainScreen(
    config: Config,
    resumeTick: Int,
    onCommit: (Config) -> Unit,
    onEdit: (Reminder?) -> Unit,
    onWeek: () -> Unit
) {
    val today = LocalDate.now()
    val dayType = config.dayTypeFor(today)
    val minuteNow = remember(resumeTick) { LocalDateTime.now().let { it.hour * 60 + it.minute } }
    val remaining = remember(config, resumeTick) {
        Scheduler.remainingToday(config, LocalDateTime.now())
    }
    val next = remaining.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {

        Surface(color = Slab, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Edge)) {
            Column(Modifier.padding(18.dp)) {
                Label("Next notification")
                Spacer(Modifier.height(10.dp))
                if (next != null) {
                    Text(
                        hhmm(next.first),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${next.second.emoji}  ${next.second.title}", fontSize = 15.sp, color = Chalk)
                    Spacer(Modifier.height(4.dp))
                    Text("${remaining.size} more today", fontSize = 12.sp, color = Mist)
                } else {
                    Text(
                        "Nothing left today",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Mist
                    )
                }
            }
        }

        PermissionNotices(resumeTick)

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(
                    "Today is " + today.dayOfWeek
                        .getDisplayName(TextStyle.FULL, Locale.getDefault())
                        .lowercase(Locale.getDefault())
                )
                if (config.overrides.containsKey(today.toString())) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "reset",
                        fontSize = 11.sp,
                        color = Amber,
                        modifier = Modifier.clickable {
                            onCommit(config.copy(overrides = config.overrides - today.toString()))
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Segmented(selected = dayType) { picked ->
                onCommit(config.copy(overrides = config.overrides + (today.toString() to picked)))
            }
            Spacer(Modifier.height(8.dp))
            Text("Decides which times fire today only.", fontSize = 12.sp, color = Mist)
        }

        Column {
            Label("Reminders")
            Spacer(Modifier.height(10.dp))
            config.reminders.forEach { reminder ->
                ReminderCard(reminder, dayType, minuteNow) { onEdit(reminder) }
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEdit(null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Chalk, contentColor = Ink)
            ) { Text("Add reminder", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = onWeek,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Edge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist)
            ) { Text("Usual week") }
        }

        Text(
            "Notifications keep arriving with the app closed. Nothing leaves this phone.",
            fontSize = 11.sp,
            color = Mist,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    dayType: DayType,
    minuteNow: Int,
    onClick: () -> Unit
) {
    val times = reminder.timesFor(dayType)

    Surface(
        color = Slab,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Edge),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reminder.emoji, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    reminder.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reminder.active) Chalk else Mist,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                !reminder.active -> Text("Paused", fontSize = 11.sp, color = Amber)

                times.isEmpty() -> Text(
                    "Silent on ${dayType.label.lowercase(Locale.getDefault())} days",
                    fontSize = 12.sp,
                    color = Mist
                )

                else -> WrapGrid(times, perRow = 5) { minute ->
                    val past = minute <= minuteNow
                    Text(
                        hhmm(minute),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (past) Faded else Chalk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (past) Color.Transparent else Chip)
                            .border(1.dp, if (past) Edge else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- */
/*  Permission notices                                                */
/* ----------------------------------------------------------------- */

@Composable
private fun PermissionNotices(resumeTick: Int) {
    val context = LocalContext.current
    var asked by remember { mutableIntStateOf(0) }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { asked++ }

    val notificationsOn = remember(resumeTick, asked) {
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false
    }
    val exactOn = remember(resumeTick) {
        val manager = context.getSystemService(AlarmManager::class.java)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager?.canScheduleExactAlarms() == true
    }
    val batteryFree = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    if (notificationsOn && exactOn && batteryFree) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notificationsOn) {
            Notice("Notifications are off, so nothing will reach you.", "Turn on") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
        }
        if (!exactOn) {
            Notice("Without alarms & reminders access, pings can drift by minutes.", "Allow") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        }
        if (!batteryFree) {
            Notice("Battery optimisation can delay or silence overnight pings.", "Exempt") {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF241D10),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF4A3A1B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 12.sp, color = Chalk, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) {
                Text(action, color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

/* ----------------------------------------------------------------- */
/*  Usual week                                                        */
/* ----------------------------------------------------------------- */

@Composable
private fun WeekScreen(config: Config, onCommit: (Config) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Header("Your usual week", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "What each weekday starts as. Switching today on the main screen is a one-off " +
                "and leaves this alone.",
            fontSize = 12.sp,
            color = Mist,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(18.dp))

        (1..7).forEach { day ->
            Text(
                DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Chalk
            )
            Spacer(Modifier.height(6.dp))
            Segmented(selected = config.week[day] ?: DayType.HOME) { picked ->
                onCommit(config.copy(week = config.week + (day to picked)))
            }
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCommit(Defaults.config) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Edge),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist)
        ) { Text("Reset everything to the starting set") }
    }
}

/* ----------------------------------------------------------------- */
/*  Reminder editor                                                   */
/* ----------------------------------------------------------------- */

private val EMOJIS = listOf("💧", "📓", "🐈", "🧺", "💊", "🧘", "🚶", "📞", "🌱", "🦷", "☀️", "🔔")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScreen(
    original: Reminder?,
    onSave: (Reminder) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(original?.title ?: "") }
    var emoji by remember { mutableStateOf(original?.emoji ?: "💧") }
    var active by remember { mutableStateOf(original?.active ?: true) }
    var times by remember {
        mutableStateOf(DayType.entries.associateWith { original?.timesFor(it).orEmpty() })
    }

    fun put(type: DayType, values: List<Int>) {
        times = times + (type to values.distinct().sorted())
    }

    fun pickTime(type: DayType) {
        val hourNow = LocalDateTime.now().hour
        TimePickerDialog(
            context,
            { _, hour, minute -> put(type, times[type].orEmpty() + (hour * 60 + minute)) },
            hourNow,
            0,
            true
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Header(if (original == null) "New reminder" else "Edit reminder", onBack)
        Spacer(Modifier.height(20.dp))

        Label("The message you'll see")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Drink a glass of water", color = Faded) },
            singleLine = true,
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slab,
                unfocusedContainerColor = Slab,
                focusedBorderColor = Amber,
                unfocusedBorderColor = Edge,
                focusedTextColor = Chalk,
                unfocusedTextColor = Chalk,
                cursorColor = Amber
            )
        )

        Spacer(Modifier.height(20.dp))
        Label("Icon")
        Spacer(Modifier.height(8.dp))
        WrapGrid(EMOJIS, perRow = 6) { candidate ->
            val on = emoji == candidate
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Chip else Slab)
                    .border(1.dp, if (on) Chalk else Edge, RoundedCornerShape(10.dp))
                    .clickable { emoji = candidate }
            ) { Text(candidate, fontSize = 18.sp) }
        }

        DayType.entries.forEach { type ->
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label("${type.label} days")
                Text(
                    "copy to all",
                    fontSize = 11.sp,
                    color = Mist,
                    modifier = Modifier.clickable {
                        val source = times[type].orEmpty()
                        times = DayType.entries.associateWith { source }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))

            // null stands for the trailing "add a time" button.
            val slots: List<Int?> = times[type].orEmpty() + listOf(null)
            WrapGrid(slots, perRow = 4) { minute ->
                if (minute == null) {
                    Text(
                        "+ time",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Mist,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .border(1.dp, Edge, RoundedCornerShape(9.dp))
                            .clickable { pickTime(type) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Slab)
                            .border(1.dp, Edge, RoundedCornerShape(9.dp))
                            .clickable { put(type, times[type].orEmpty().filterNot { it == minute }) }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Text(hhmm(minute), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Chalk)
                        Spacer(Modifier.width(7.dp))
                        Text("✕", fontSize = 11.sp, color = Mist)
                    }
                }
            }

            if (times[type].orEmpty().isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Silent on ${type.label.lowercase(Locale.getDefault())} days.",
                    fontSize = 11.sp,
                    color = Mist
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (active) "Active" else "Paused",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Chalk
            )
            Switch(
                checked = active,
                onCheckedChange = { active = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Amber,
                    uncheckedTrackColor = Slab,
                    uncheckedBorderColor = Edge
                )
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                Notifications.show(
                    context,
                    Reminder(
                        id = original?.id ?: "preview",
                        emoji = emoji,
                        title = title.ifBlank { "Test notification" }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Edge),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist)
        ) { Text("Send a test notification") }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (original != null) {
                OutlinedButton(
                    onClick = { onDelete(original.id) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Edge),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)
                ) { Text("Delete") }
            }
            Button(
                onClick = {
                    onSave(
                        Reminder(
                            id = original?.id ?: "r${System.currentTimeMillis()}",
                            emoji = emoji,
                            title = title.trim(),
                            active = active,
                            times = times
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Chalk, contentColor = Ink)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/* ----------------------------------------------------------------- */
/*  Shared pieces                                                     */
/* ----------------------------------------------------------------- */

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            fontSize = 30.sp,
            color = Mist,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Chalk)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
        color = Mist
    )
}

@Composable
private fun Segmented(selected: DayType, onSelect: (DayType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slab)
            .border(1.dp, Edge, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DayType.entries.forEach { type ->
            val on = type == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Chalk else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 9.dp)
            ) {
                Text(
                    type.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Ink else Mist,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Wraps items onto fixed-size rows. Keeps the UI off the experimental FlowRow API. */
@Composable
private fun <T> WrapGrid(
    items: List<T>,
    perRow: Int = 4,
    content: @Composable (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { content(it) }
            }
        }
    }
}
