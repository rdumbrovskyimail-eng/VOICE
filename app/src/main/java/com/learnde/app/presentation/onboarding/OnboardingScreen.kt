// Путь: app/src/main/java/com/learnde/app/presentation/onboarding/OnboardingScreen.kt
package com.learnde.app.presentation.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: DataStore<AppSettings>
) : ViewModel() {

    /** Сохранить ключ и завершить онбординг. */
    fun finish(key: String) = viewModelScope.launch {
        store.updateData { it.copy(apiKey = key.trim(), onboardingDone = true) }
    }

    /** Пропустить (ключ введут позже в настройках). */
    fun skip() = viewModelScope.launch {
        store.updateData { it.copy(onboardingDone = true) }
    }
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf("") }

    val bg = Color(0xFF0E0F13)
    val accent = Color(0xFF4F8DFD)

    Surface(color = bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("👋 GeminiVoice", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Голосовой ассистент на базе Gemini Live. Чтобы начать, нужен бесплатный API-ключ Google AI Studio.",
                color = Color(0xFFB7BBC4), fontSize = 15.sp, lineHeight = 22.sp
            )

            Step("1", "Открой Google AI Studio и войди в Google-аккаунт.")
            Step("2", "Нажми «Create API key» и скопируй ключ (он начинается с AIza…).")
            Step("3", "Вставь ключ ниже — и можно говорить.")

            Button(
                onClick = { uri.openUri(AI_STUDIO_KEY_URL) },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("🔑 Получить ключ в Google AI Studio", color = Color.White, fontWeight = FontWeight.Medium) }

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Вставь API-ключ (AIza…)", color = Color.DarkGray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    cursorColor = accent,
                    focusedBorderColor = accent, unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = bg, unfocusedContainerColor = bg
                )
            )

            Button(
                onClick = { vm.finish(key); onDone() },
                enabled = key.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    disabledContainerColor = Color(0xFF2A2D33)
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Продолжить", color = Color.White, fontWeight = FontWeight.Bold) }

            TextButton(
                onClick = { vm.skip(); onDone() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Пропустить (ввести ключ позже в настройках)", color = Color(0xFF8A8F98), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Text(n, color = Color(0xFF4F8DFD), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color(0xFFD3D6DC), fontSize = 14.sp, lineHeight = 20.sp)
    }
}
