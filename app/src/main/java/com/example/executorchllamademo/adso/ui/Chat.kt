package com.example.executorchllamademo.adso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.adso.AdsoViewModel
import com.example.executorchllamademo.adso.ChatMsg
import com.example.executorchllamademo.adso.llm.LlamaEngine
import com.example.executorchllamademo.ui.components.OrbState
import com.example.executorchllamademo.ui.theme.AdsoTheme
import java.text.DateFormat
import java.util.Date

/** Monochrome palette for the chat: neutral greys, the orb is the only colour. */
private class Mono(dark: Boolean) {
    val bg = if (dark) Color(0xFF0B0B0C) else Color(0xFFF4F4F5)
    val surface = if (dark) Color(0xFF161618) else Color(0xFFFFFFFF)
    val raised = if (dark) Color(0xFF232326) else Color(0xFFE6E6E8)
    val line = if (dark) Color(0xFF2A2A2E) else Color(0xFFDADADD)
    val text = if (dark) Color(0xFFEDEDEF) else Color(0xFF111113)
    val muted = if (dark) Color(0xFF9A9AA0) else Color(0xFF5F5F66)
    val mine = if (dark) Color(0xFF2C2C30) else Color(0xFF1C1C1F)
    val onMine = Color(0xFFF5F5F7)
    val action = if (dark) Color(0xFFF2F2F4) else Color(0xFF111113)
    val onAction = if (dark) Color(0xFF0B0B0C) else Color(0xFFFFFFFF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AdsoViewModel) {
    val m = Mono(AdsoTheme.colors.isDark)
    var draft by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val items = vm.chatMessages.size + if (vm.chatStreaming != null) 1 else 0
    LaunchedEffect(items, vm.chatStreaming?.length?.div(40)) { if (items > 0) list.animateScrollToItem(items - 1) }
    val orbState = when {
        vm.chatOrbThinking -> OrbState.THINKING
        vm.chatBusy -> OrbState.GENERATING
        draft.isNotBlank() -> OrbState.THINKING
        else -> OrbState.IDLE
    }
    val send = { if (draft.isNotBlank() && !vm.chatBusy) { vm.sendChat(draft); draft = "" } }

    Box(Modifier.fillMaxSize().background(m.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Header: back · model · history · new chat
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MonoIcon(m, Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) { vm.back() }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { showModels = true }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LlamaEngine.modelName?.substringBefore("-Instruct")?.replace("-", " ") ?: "Llama 3.2", color = m.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = m.muted, modifier = Modifier.size(18.dp))
                    }
                    Text("On this phone · offline", color = m.muted, fontSize = 12.sp)
                }
                MonoIcon(m, Icons.Rounded.History, tr("Chats")) { showHistory = true }
                Spacer(Modifier.width(6.dp))
                MonoIcon(m, Icons.Rounded.Edit, tr("New chat")) { vm.newChat() }
            }

            if (vm.chatMessages.isEmpty() && vm.chatStreaming == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Spacer(Modifier.size(12.dp))
                    Orb(orbState, 170.dp)
                    Text("Ask me anything about ${vm.course.title}.", color = m.text, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text("I know your goal and your gaps, and I answer right here on your phone.", color = m.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                    vm.chatSuggestions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(m.surface)
                                .border(1.dp, m.line, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { vm.sendChat(s) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(s, color = m.text, fontSize = 14.sp, modifier = Modifier.weight(1f)) }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Orb(orbState, 64.dp) }
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.chatMessages) { msg -> Bubble(m, msg) }
                    vm.chatStreaming?.let { live ->
                        item { Bubble(m, ChatMsg(false, if (live.isEmpty()) "Thinking…" else "$live ▌", ""), faded = live.isEmpty()) }
                    }
                }
            }

            // Input
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("${tr("Message")}…", color = m.muted) },
                    maxLines = 4, shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = m.surface, unfocusedContainerColor = m.surface,
                        focusedBorderColor = m.muted, unfocusedBorderColor = m.line,
                        focusedTextColor = m.text, unfocusedTextColor = m.text, cursorColor = m.text,
                    ),
                )
                val busy = vm.chatBusy
                val actionLabel = if (busy) tr("Stop") else tr("Send")
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (busy || draft.isNotBlank()) m.action else m.raised)
                        .clickable(role = Role.Button) { if (busy) vm.stopChat() else send() }
                        .semantics { contentDescription = actionLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (busy) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, null, tint = if (busy || draft.isNotBlank()) m.onAction else m.muted)
                }
            }
        }

        // Chat history drawer
        if (showHistory) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { showHistory = false })
            Column(
                Modifier.fillMaxHeight().width(300.dp).background(m.surface).statusBarsPadding().navigationBarsPadding().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Chats"), color = m.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    MonoIcon(m, Icons.Rounded.Close, "Close") { showHistory = false }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Pill).background(m.action).clickable(role = Role.Button) { vm.newChat(); showHistory = false },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Text("+  ${tr("New chat")}", color = m.onAction, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (vm.chats.isEmpty()) Text("No chats yet. They stay on this phone.", color = m.muted, fontSize = 13.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(vm.chats, key = { it.id }) { chat ->
                        val on = chat.id == vm.chatId
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp)).background(if (on) m.raised else Color.Transparent)
                                .clickable(role = Role.Button) { vm.openChatHistory(chat.id); showHistory = false }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, color = m.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(chat.updated)), color = m.muted, fontSize = 11.sp)
                            }
                            MonoIcon(m, Icons.Rounded.DeleteOutline, "Delete chat", size = 40.dp, plain = true) { vm.deleteChat(chat.id) }
                        }
                    }
                }
                Text("Chats are saved on this phone only.", color = m.muted, fontSize = 11.sp)
            }
        }

        // Model picker
        if (showModels) {
            ModalBottomSheet(onDismissRequest = { showModels = false }, containerColor = AdsoTheme.colors.background) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                    Text(tr("Choose a model"), color = AdsoTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                    ModelCard(vm, onAdvanced = { showModels = false })
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Mono, msg: ChatMsg, faded: Boolean = false) {
    if (msg.mine) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                msg.text, color = m.onMine, fontSize = 15.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(m.mine).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            OrbDot(22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(msg.text, color = if (faded) m.muted else m.text, fontSize = 15.sp, lineHeight = 23.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).background(m.surface).border(1.dp, m.line, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                if (msg.meta.isNotBlank()) Text(msg.meta, color = m.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** A tiny static orb for message avatars (the live 3D orb is shown once at the top). */
@Composable
private fun OrbDot(size: Dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF02040C), Color(0xFF0A2272), Color(0xFF6A94FF)), center = Offset(this.size.width / 2, this.size.height / 2), radius = this.size.minDimension / 2))
    }
}

@Composable
private fun MonoIcon(m: Mono, icon: ImageVector, description: String, size: Dp = 44.dp, plain: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).then(if (plain) Modifier else Modifier.background(m.surface).border(1.dp, m.line, CircleShape))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = m.muted, modifier = Modifier.size(20.dp)) }
}
