package de.goork.songflip.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.goork.songflip.R
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.theme.StateActiveGreen
import de.goork.songflip.ui.theme.StateErrorRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestStudioBottomSheet(
    onDismissRequest: () -> Unit,
    selectedTargetKey: String,
    settingsRepository: SettingsRepository,
    odesliRepository: OdesliRepository
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var testInputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var conversionResult by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.test_section_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.test_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = testInputUrl,
                onValueChange = { testInputUrl = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.test_placeholder),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (testInputUrl.isEmpty()) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text.toString()
                                if (text.startsWith("http://") || text.startsWith("https://")) {
                                    testInputUrl = text
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = { testInputUrl = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            Button(
                onClick = {
                    if (testInputUrl.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isLoading = true
                        conversionResult = null
                        isError = false
                        scope.launch {
                            val res = odesliRepository.resolveTargetUrl(
                                inputUrl = testInputUrl.trim(),
                                targetPlatformKey = selectedTargetKey,
                                customApiUrl = settingsRepository.customApiUrl,
                                customApiToken = settingsRepository.customApiToken
                            )
                            isLoading = false
                            when (res) {
                                is OdesliResult.Success -> {
                                    conversionResult = res.targetUrl
                                }
                                is OdesliResult.Error -> {
                                    isError = true
                                    conversionResult = res.message
                                }
                            }
                        }
                    }
                },
                enabled = testInputUrl.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.test_converting), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        text = stringResource(R.string.test_button),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            AnimatedVisibility(
                visible = conversionResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                conversionResult?.let { result ->
                    val textColor = if (isError) StateErrorRed else StateActiveGreen
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isError) {
                                stringResource(R.string.test_error, result)
                            } else {
                                stringResource(R.string.test_result, result)
                            },
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (!isError) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("SongFlip Link", result)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_copy), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                                        context.startActivity(viewIntent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_open), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
