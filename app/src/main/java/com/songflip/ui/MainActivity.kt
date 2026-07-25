package com.songflip.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songflip.R
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import com.songflip.ui.theme.SongFlipTheme
import kotlinx.coroutines.launch

import com.songflip.data.SettingsRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SongFlipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { OdesliRepository() }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var testInputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var conversionResult by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val targetServices = listOf(
        "youtubeMusic" to stringResource(R.string.target_youtube_music),
        "appleMusic" to stringResource(R.string.target_apple_music),
        "spotify" to stringResource(R.string.target_spotify),
        "tidal" to stringResource(R.string.target_tidal),
        "deezer" to stringResource(R.string.target_deezer)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.status_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.status_active),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Target Service Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.target_service_label),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                targetServices.forEach { (key, label) ->
                    val isSelected = selectedTargetKey == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTargetKey = key
                            settingsRepository.targetPlatform = key
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Test Converter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.test_section_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

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
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        if (testInputUrl.isNotBlank()) {
                            isLoading = true
                            conversionResult = null
                            isError = false
                            scope.launch {
                                val res = repository.resolveTargetUrl(
                                    testInputUrl.trim(),
                                    selectedTargetKey
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.test_converting))
                    } else {
                        Text(stringResource(R.string.test_button))
                    }
                }

                conversionResult?.let { result ->
                    val textColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isError) {
                                stringResource(R.string.test_error, result)
                            } else {
                                stringResource(R.string.test_result, result)
                            },
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!isError) {
                            OutlinedButton(
                                onClick = {
                                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                                    context.startActivity(viewIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Öffnen / Open Link")
                            }
                        }
                    }
                }
            }
        }
    }
}
