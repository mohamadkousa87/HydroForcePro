package com.hydroforce.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.camera.CloudVisionVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CloudGreen = Color(0xFF00E676)
private val CloudRed = Color(0xFFD64545)
private const val API_KEY_URL = "https://aistudio.google.com/apikey"

@Composable
fun CloudAiDialog(
    enabled: Boolean,
    apiKey: String,
    onEnabledChange: (Boolean) -> Unit,
    onKeyChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf(apiKey) }
    var showLearn by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<CloudVisionVerifier.KeyStatus?>(null) }

    if (showLearn) {
        LearnHowDialog(onDismiss = { showLearn = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onKeyChange(keyText.trim()); onDismiss() }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق", "Close")) } },
        title = { Text(tr("الفحص السحابي الذكي 100% (Cloud AI) 🌐", "Smart Cloud AI check 100% 🌐")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("تفعيل الفحص السحابي", "Enable cloud check"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                Text(
                    tr("اختياري. عند تفعيله يصبح الحَكَم عند الضغط على توثيق الشرب.", "Optional. When on, it becomes the judge at the verify tap."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; status = null },
                    singleLine = true,
                    label = { Text(tr("مفتاح Gemini API", "Gemini API key")) },
                    trailingIcon = {
                        if (keyText.isNotEmpty()) {
                            TextButton(onClick = { keyText = ""; status = null }) { Text("✕") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Buttons stacked vertically so Arabic words never get squeezed.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.getText()?.text?.let { keyText = it.trim(); status = null } },
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("📋 لصق المفتاح", "📋 Paste")) }
                    Button(
                        onClick = {
                            checking = true; status = null
                            val k = keyText.trim()
                            scope.launch {
                                val s = withContext(Dispatchers.IO) { CloudVisionVerifier.verifyKey(k) }
                                status = s; checking = false
                                if (s is CloudVisionVerifier.KeyStatus.Ok) onKeyChange(k)
                            }
                        },
                        enabled = !checking && keyText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (checking) tr("جارٍ الفحص...", "Checking...") else tr("🔍 فحص وتأكيد", "🔍 Verify")) }
                }
                Button(
                    onClick = { showLearn = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        tr("💡 تعلم كيف تحصل على مفتاحك المجاني", "💡 Get a free key"),
                        fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }
                when (val st = status) {
                    CloudVisionVerifier.KeyStatus.Ok -> StatusBadge(
                        tr("✅ المفتاح صحيح! التدقيق السحابي مربوط بحسابك الآن", "✅ Key valid! Cloud check is linked to your account"), CloudGreen
                    )
                    is CloudVisionVerifier.KeyStatus.Invalid -> StatusBadge(
                        tr("❌ مفتاح غير صالح — ${st.detail}", "❌ Invalid key — ${st.detail}"), CloudRed
                    )
                    CloudVisionVerifier.KeyStatus.NoNetwork -> StatusBadge(
                        tr("❌ لا يوجد إنترنت — تحقق من الاتصال", "❌ No internet — check your connection"), CloudRed
                    )
                    null -> {}
                }
            }
        }
    )
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color).padding(10.dp)
    )
}

@Composable
private fun LearnHowDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { runCatching { uriHandler.openUri(API_KEY_URL) } }) {
                Text(tr("فتح موقع المفتاح المجاني الآن 🔗", "Open the free key site now 🔗"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("رجوع", "Back")) } },
        title = { Text(tr("كيف تحصل على مفتاحك المجاني 💡", "How to get your free key 💡")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    tr(
                        "✅ مجاني 100% مدى الحياة — لا يتطلب بطاقة بنكية، ولا يترتب عليه أي اشتراكات أو رسوم مالية نهائياً (مقدم مجاناً من Google لحسابك الشخصي).",
                        "✅ 100% Free Forever — No credit card required, zero subscriptions or fees (provided free by Google for your personal account)."
                    ),
                    color = Color.Black, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CloudGreen).padding(10.dp)
                )
                Text(tr("الخطوات:", "Steps:"), fontWeight = FontWeight.Bold)
                Text(tr("1) اضغط الزر بالأسفل وسجّل الدخول بحساب Gmail العادي.", "1) Tap the button below and sign in with your normal Gmail."))
                Text(tr("2) اضغط \"Get API key\" ثم \"Create API key\".", "2) Tap \"Get API key\", then \"Create API key\"."))
                Text(tr("3) اضغط أيقونة النسخ (Copy) لنسخ الكود.", "3) Tap the Copy icon to copy the code."))
                Text(tr("4) ارجع هنا واضغط \"📋 لصق المفتاح\" ثم \"🔍 فحص وتأكيد\".", "4) Come back, tap \"📋 Paste\", then \"🔍 Verify\"."))
            }
        }
    )
}
