package id.aninusa.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.aninusa.app.BuildConfig
import id.aninusa.app.data.DiagnosticCheck
import id.aninusa.app.data.Diagnostics
import id.aninusa.app.data.LocalStore
import id.aninusa.app.ui.DividerLine
import kotlinx.coroutines.launch
import id.aninusa.app.ui.SegmentedTabs

@Composable
fun SettingsScreen() {
    val store = LocalStore.get()
    val userData by store.data.collectAsState()
    val prefs = userData.prefs
    var showClearDialog by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<DiagnosticCheck>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Pengaturan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Sesuaikan tampilan dan cara membaca",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingSection("Tampilan") {
            Text(
                text = "Tema aplikasi",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                options = listOf("Gelap", "Terang", "Sistem"),
                selectedIndex = when (prefs.themeMode) {
                    "light" -> 1
                    "system" -> 2
                    else -> 0
                },
                onSelect = { index ->
                    val mode = when (index) {
                        1 -> "light"
                        2 -> "system"
                        else -> "dark"
                    }
                    store.updatePrefs { it.copy(themeMode = mode) }
                }
            )
        }

        SettingSection("Pembaca Komik") {
            SwitchRow(
                title = "Mode gulir vertikal",
                subtitle = "Cocok untuk manhwa dan manhua. Matikan untuk mode halaman.",
                checked = prefs.verticalReader,
                onCheckedChange = { value -> store.updatePrefs { it.copy(verticalReader = value) } }
            )
            DividerLine()
            SwitchRow(
                title = "Hemat data",
                subtitle = "Memakai gambar resolusi lebih kecil saat membaca chapter.",
                checked = prefs.dataSaver,
                onCheckedChange = { value -> store.updatePrefs { it.copy(dataSaver = value) } }
            )
            DividerLine()
            Text(
                text = "Bahasa chapter utama",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Dipakai saat membuka detail komik dan mencari judul.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                options = listOf("Indonesia", "English"),
                selectedIndex = if (prefs.mangaLanguage == "en") 1 else 0,
                onSelect = { index ->
                    store.updatePrefs { it.copy(mangaLanguage = if (index == 1) "en" else "id") }
                }
            )
        }

        SettingSection("Data") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showClearDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Hapus riwayat baca",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Favorit tetap tersimpan. Progres baca dan riwayat anime akan dihapus.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingSection("Bantuan") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Diagnostik koneksi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Periksa AniList, MangaDex, dan pemuatan gambar bila data gagal dimuat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Button(onClick = {
                        checking = true
                        checks = emptyList()
                        scope.launch {
                            checks = Diagnostics.run()
                            checking = false
                        }
                    }) { Text("Periksa") }
                }
            }
            if (checks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                checks.forEach { check ->
                    DiagnosticRow(check)
                    DividerLine()
                }
            }
        }

        SettingSection("Tentang") {
            InfoLine("Versi aplikasi", BuildConfig.VERSION_NAME)
            InfoLine("Dukungan Android", "Android 6 (API 23) – Android 17")
            InfoLine("Sumber data anime", "AniList GraphQL API")
            InfoLine("Sumber data komik", "MangaDex API (chapter bahasa Indonesia)")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "AniNusa adalah katalog dan pembaca komik. Aplikasi ini tidak menyimpan " +
                    "maupun menayangkan video anime; tautan menuju platform streaming resmi " +
                    "disediakan agar kamu bisa menonton dengan subtitle Indonesia secara legal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Hapus riwayat?") },
            text = { Text("Progres baca dan riwayat anime akan dihapus dari perangkat ini.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearHistory()
                    showClearDialog = false
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DiagnosticRow(check: DiagnosticCheck) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (check.success) "\u2713" else "\u2715",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (check.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = check.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = check.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
