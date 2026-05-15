package eu.kanade.tachiyomi.ui.entries.novel

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.source.novel.resolver.model.PaginationType
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class OmniBuilderScreen(val url: String) : Screen() {

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { OmniBuilderScreenModel(url) }
        val state by screenModel.state.collectAsState()
        
        var webViewRef: WebView? by remember { mutableStateOf(null) }

        LaunchedEffect(state) {
            val current = state
            if (current is OmniBuilderScreenModel.State.Active) {
                webViewRef?.evaluateJavascript("window.TadamiPicker.setInteractionMode(${current.isInteractionMode});", null)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(MR.strings.omnibuilder_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        val current = state
                        if (current is OmniBuilderScreenModel.State.Active) {
                            val isInteract = current.isInteractionMode
                            TextButton(onClick = { screenModel.toggleInteractionMode() }) {
                                Icon(
                                    imageVector = if (isInteract) Icons.Default.TouchApp else Icons.Default.AdsClick,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isInteract) 
                                        stringResource(MR.strings.omnibuilder_mode_interact) 
                                    else 
                                        stringResource(MR.strings.omnibuilder_mode_select)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                BuilderBottomPanel(state, screenModel) { navigator.pop() }
            }
        ) { paddingValues ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(screenModel.bridge, "TadamiBridge")
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                val js = try {
                                    context.assets.open("TadamiPicker.js").bufferedReader().use { it.readText() }
                                } catch (e: Exception) {
                                    null
                                }
                                if (js != null) {
                                    view.evaluateJavascript(js, null)
                                    view.evaluateJavascript("window.TadamiPicker.enable();", null)
                                }
                                if (screenModel.state.value is OmniBuilderScreenModel.State.Init) {
                                    screenModel.start()
                                }
                            }
                        }
                        
                        webViewRef = this
                        loadUrl(this@OmniBuilderScreen.url)
                    }
                }
            )
        }
    }

    @Composable
    private fun BuilderBottomPanel(
        state: OmniBuilderScreenModel.State,
        screenModel: OmniBuilderScreenModel,
        onPop: () -> Unit
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                when (state) {
                    is OmniBuilderScreenModel.State.Init -> Text(stringResource(MR.strings.omnibuilder_loading))
                    is OmniBuilderScreenModel.State.StepTitle -> {
                        Text(stringResource(MR.strings.omnibuilder_step_1_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { screenModel.skipCurrentStep() }) { Text(stringResource(MR.strings.omnibuilder_action_skip)) }
                        }
                    }
                    is OmniBuilderScreenModel.State.StepCover -> {
                        Text(stringResource(MR.strings.omnibuilder_step_2_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { screenModel.goBack() }) { Text(stringResource(MR.strings.omnibuilder_action_back)) }
                            Button(onClick = { screenModel.skipCurrentStep() }) { Text(stringResource(MR.strings.omnibuilder_action_skip)) }
                        }
                    }
                    is OmniBuilderScreenModel.State.StepChapters -> {
                        Text(stringResource(MR.strings.omnibuilder_step_3_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(MR.strings.omnibuilder_step_3_desc), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { screenModel.toggleInteractionMode() }) {
                                Icon(
                                    imageVector = if (state.isInteractionMode) Icons.Default.TouchApp else Icons.Default.AdsClick,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (state.isInteractionMode)
                                        stringResource(MR.strings.omnibuilder_mode_interact)
                                    else
                                        stringResource(MR.strings.omnibuilder_mode_select)
                                )
                            }
                            OutlinedButton(onClick = { screenModel.goBack() }) { Text(stringResource(MR.strings.omnibuilder_action_back)) }
                        }
                    }
                    is OmniBuilderScreenModel.State.StepPagination -> {
                        Text(stringResource(MR.strings.omnibuilder_step_4_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(MR.strings.omnibuilder_step_4_desc), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { screenModel.goBack() }) { Text(stringResource(MR.strings.omnibuilder_action_back)) }
                            Button(onClick = { screenModel.skipCurrentStep() }) { Text(stringResource(MR.strings.omnibuilder_action_skip)) }
                        }
                    }
                    is OmniBuilderScreenModel.State.PaginationTypeSelection -> {
                        Text(stringResource(MR.strings.omnibuilder_step_pagination_type), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { screenModel.goBack() }) { Text(stringResource(MR.strings.omnibuilder_action_back)) }
                            Button(onClick = { screenModel.setPaginationType(PaginationType.AJAX_SELECT) }) { Text(stringResource(MR.strings.omnibuilder_pagination_dropdown)) }
                            Button(onClick = { screenModel.setPaginationType(PaginationType.NEXT_LINK) }) { Text(stringResource(MR.strings.omnibuilder_pagination_next)) }
                        }
                    }
                    is OmniBuilderScreenModel.State.Review -> {
                        Text(stringResource(MR.strings.omnibuilder_review_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { screenModel.goBack() }, modifier = Modifier.weight(1f)) { Text(stringResource(MR.strings.omnibuilder_action_back)) }
                            Button(onClick = { screenModel.saveRule { onPop() } }, modifier = Modifier.weight(1f)) { 
                                Text(stringResource(MR.strings.omnibuilder_action_save)) 
                            }
                        }
                    }
                }
            }
        }
    }
}
