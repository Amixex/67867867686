package com.amix.tutor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amix.tutor.R
import com.amix.tutor.auth.PromoOutcome
import com.amix.tutor.ui.theme.AmixBg
import com.amix.tutor.ui.theme.AmixLine
import com.amix.tutor.ui.theme.AmixMuted
import com.amix.tutor.ui.theme.AmixText
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Экран Pro: описание, цена и строка промокода (проверка только на сервере, нужен интернет и вход). Оплата будет на этапе «Оплата». */
@Composable
fun ProScreen(
    proUntil: Long,
    signedIn: Boolean,
    onRedeem: suspend (String) -> PromoOutcome,
    onNeedLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var messageRes by remember { mutableIntStateOf(0) }
    var messageDays by remember { mutableIntStateOf(0) }
    var messageOk by remember { mutableStateOf(false) }

    fun submit() {
        if (code.isBlank()) {
            messageRes = R.string.promo_enter
            messageOk = false
            return
        }
        if (!signedIn) {
            messageRes = R.string.promo_login_first
            messageOk = false
            onNeedLogin()
            return
        }
        scope.launch {
            busy = true
            val r = onRedeem(code)
            busy = false
            messageOk = r is PromoOutcome.Ok
            if (r is PromoOutcome.Ok) messageDays = r.days
            messageRes = when (r) {
                is PromoOutcome.Ok -> R.string.promo_ok
                PromoOutcome.NotFound, PromoOutcome.BadRequest -> R.string.promo_not_found
                PromoOutcome.Used -> R.string.promo_used
                PromoOutcome.RateLimited -> R.string.promo_rate_limited
                PromoOutcome.NoConnection -> R.string.no_internet
                PromoOutcome.LoginRequired -> R.string.promo_login_first
                is PromoOutcome.Failed -> R.string.promo_error
            }
            if (r is PromoOutcome.Ok) code = ""
            if (r is PromoOutcome.LoginRequired) onNeedLogin()
        }
    }

    ScreenColumn {
        PanelCard {
            Text(
                stringResource(R.string.pro_eyebrow),
                color = AmixMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.title_pro), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pro_description), color = AmixMuted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("200 ₽", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pro_month), color = AmixMuted, fontSize = 14.sp)
            }
            if (proUntil > System.currentTimeMillis()) {
                Spacer(Modifier.height(10.dp))
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(proUntil))
                Text(stringResource(R.string.pro_active_until, date), color = Color(0xFF4ADE80), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { /* оплата появится на этапе «Оплата» */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AmixBg)
            ) {
                Text(stringResource(R.string.buy_pro), fontWeight = FontWeight.Bold)
            }
        }

        PanelCard {
            Text(stringResource(R.string.promo_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.promo_ph), color = AmixMuted) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AmixText, unfocusedTextColor = AmixText, cursorColor = AmixText,
                    focusedBorderColor = Color(0x33FFFFFF), unfocusedBorderColor = AmixLine
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AmixBg)
            ) {
                Text(stringResource(R.string.promo_apply), fontWeight = FontWeight.Bold)
            }
            if (messageRes != 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(messageRes).replace("{days}", messageDays.toString()),
                    color = if (messageOk) Color(0xFF4ADE80) else Color(0xFFFF9A9A),
                    fontSize = 13.sp
                )
            }
        }
    }
}
