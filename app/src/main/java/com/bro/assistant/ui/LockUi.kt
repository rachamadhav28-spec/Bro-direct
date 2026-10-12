package com.bro.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 3x3 pattern pad. Calls [onComplete] with the dots touched, like "0-1-4-7". */
@Composable
fun PatternPad(onComplete: (String) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun center(i: Int): Offset {
        val cell = size.width / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }
    fun hit(o: Offset): Int? {
        if (size.width == 0) return null
        val r = size.width / 3f * 0.36f
        return (0..8).firstOrNull { (center(it) - o).getDistance() <= r }
    }

    Canvas(
        Modifier
            .size(280.dp)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> finger = o; path = listOfNotNull(hit(o)) },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)?.let { if (it !in path) path = path + it }
                    },
                    onDragEnd = { val p = path; finger = null; path = emptyList(); if (p.isNotEmpty()) onComplete(p.joinToString("-")) },
                    onDragCancel = { finger = null; path = emptyList() }
                )
            }
    ) {
        // lines
        for (k in 0 until path.size - 1) drawLine(NeonCyan, center(path[k]), center(path[k + 1]), 6f, StrokeCap.Round)
        val f = finger
        if (path.isNotEmpty() && f != null) drawLine(NeonCyan.copy(alpha = 0.5f), center(path.last()), f, 6f, StrokeCap.Round)
        // dots
        for (i in 0..8) {
            val on = i in path
            drawCircle(if (on) NeonCyan else Color(0x66FFFFFF), if (on) 16f else 10f, center(i))
            if (on) drawCircle(NeonCyan.copy(alpha = 0.35f), 34f, center(i), style = Stroke(3f))
        }
    }
}

/** Asks for a PIN, a password or a pattern. Returns the secret ("p:" + dots for patterns). */
@Composable
fun CredentialEntry(type: String, resetKey: Any, onSubmit: (String) -> Unit) {
    key(resetKey) {
        if (type == "pattern") {
            PatternPad { onSubmit("p:$it") }
        } else {
            var text by remember { mutableStateOf("") }
            val go = { if (text.isNotEmpty()) onSubmit(text) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (type == "pin") it.filter(Char::isDigit).take(8) else it.take(64) },
                    singleLine = true,
                    label = { Text(if (type == "pin") "PIN" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (type == "pin") KeyboardType.NumberPassword else KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { go() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { go() }) { Text("OK") }
            }
        }
    }
}

private fun typeLabel(t: String) = when (t) { "pin" -> "PIN"; "password" -> "password"; "pattern" -> "pattern"; else -> "lock" }

/** The lock screen. Nothing from the chat is visible until the right secret is entered. */
@Composable
fun BroLockScreen(
    type: String,
    message: String,
    onSubmit: (String) -> Boolean,
    waitSeconds: () -> Int,
    onBiometric: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    onForgot: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    FuturisticBackground {
        Column(
            Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BRO", color = NeonCyan, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("Enter your BRO ${typeLabel(type)}", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            CredentialEntry(type, attempt) { secret ->
                val w = waitSeconds()
                if (w > 0) { error = "Too many tries. Wait $w seconds."; attempt++ }
                else if (!onSubmit(secret)) {
                    val w2 = waitSeconds()
                    error = if (w2 > 0) "Too many tries. Wait $w2 seconds." else "Wrong ${typeLabel(type)}. Try again."
                    attempt++
                }
            }
            if (error.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(error, color = Color(0xFFEF5350), fontSize = 13.sp) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBiometric != null) OutlinedButton(onClick = onBiometric) { Text("Fingerprint") }
                if (onVoice != null) OutlinedButton(onClick = onVoice) { Text("Say phrase") }
                if (onCancel != null) OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
            if (onForgot != null) TextButton(onClick = onForgot) { Text("Forgot? Fingerprint or email code", fontSize = 12.sp) }
        }
    }
}

/** Set or change the lock: confirm the old one (if any), enter the new one twice. */
@Composable
fun LockSetupFlow(
    newType: String,
    hasOld: Boolean,
    oldType: String,
    verifyOld: (String) -> Boolean,
    waitSeconds: () -> Int,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var stage by remember { mutableStateOf(if (hasOld) "old" else "new") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }

    fun valid(secret: String): String? = when (newType) {
        "pin" -> if (secret.length < 4) "A PIN needs 4 to 8 digits." else null
        "password" -> if (secret.length < 4) "A password needs at least 4 characters." else null
        else -> if (secret.removePrefix("p:").split("-").size < 4) "Join at least 4 dots." else null
    }

    FuturisticBackground {
        Column(
            Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BRO lock", color = NeonCyan, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                when (stage) {
                    "old" -> "Enter your current ${typeLabel(oldType)}"
                    "new" -> "Choose a new ${typeLabel(newType)}"
                    else -> "Enter it again to confirm"
                },
                color = Color.White, fontSize = 16.sp
            )
            Spacer(Modifier.height(18.dp))
            CredentialEntry(if (stage == "old") oldType else newType, "$stage-$attempt") { secret ->
                when (stage) {
                    "old" -> {
                        val w = waitSeconds()
                        if (w > 0) { error = "Too many tries. Wait $w seconds."; attempt++ }
                        else if (verifyOld(secret)) { stage = "new"; error = "" } else { error = "Wrong ${typeLabel(oldType)}."; attempt++ }
                    }
                    "new" -> {
                        val bad = valid(secret)
                        if (bad != null) { error = bad; attempt++ } else { first = secret; stage = "confirm"; error = "" }
                    }
                    else -> if (secret == first) onSave(secret) else { error = "They did not match. Start again."; stage = "new"; attempt++ }
                }
            }
            if (error.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(error, color = Color(0xFFEF5350), fontSize = 13.sp) }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
