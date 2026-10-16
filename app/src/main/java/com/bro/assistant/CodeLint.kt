package com.bro.assistant

/**
 * Cheap, certain repairs for the two mistakes the AI makes most in generated Compose code:
 * a used symbol that was never imported, and an experimental Material3 API without the opt-in.
 */
object CodeLint {
    private const val F = "androidx.compose.foundation"
    private const val M = "androidx.compose.material3"
    private const val R = "androidx.compose.runtime"
    private const val U = "androidx.compose.ui"

    /** usage pattern -> import. A pattern only counts when the file does not already import that name. */
    private val symbols: List<Pair<Regex, String>> = listOf(
        "\\bRow\\s*[({]" to "$F.layout.Row", "\\bColumn\\s*[({]" to "$F.layout.Column", "\\bBox\\s*[({]" to "$F.layout.Box",
        "\\bSpacer\\s*\\(" to "$F.layout.Spacer", "\\bArrangement\\." to "$F.layout.Arrangement",
        "\\.fillMaxSize\\(" to "$F.layout.fillMaxSize", "\\.fillMaxWidth\\(" to "$F.layout.fillMaxWidth", "\\.fillMaxHeight\\(" to "$F.layout.fillMaxHeight",
        "\\.padding\\(" to "$F.layout.padding", "\\.size\\(" to "$F.layout.size", "\\.height\\(" to "$F.layout.height", "\\.width\\(" to "$F.layout.width",
        "\\.weight\\(" to "", "\\.aspectRatio\\(" to "$F.layout.aspectRatio",
        "\\bLazyColumn\\s*[({]" to "$F.lazy.LazyColumn", "\\bLazyRow\\s*[({]" to "$F.lazy.LazyRow",
        "\\bitems\\s*\\(" to "$F.lazy.items", "\\bitemsIndexed\\s*\\(" to "$F.lazy.itemsIndexed",
        "\\.background\\(" to "$F.background", "\\.border\\(" to "$F.border", "\\.clickable\\b" to "$F.clickable",
        "\\.verticalScroll\\(" to "$F.verticalScroll", "\\brememberScrollState\\(" to "$F.rememberScrollState",
        "\\bCircleShape\\b" to "$F.shape.CircleShape", "\\bRoundedCornerShape\\(" to "$F.shape.RoundedCornerShape",
        "\\bText\\s*\\(" to "$M.Text", "\\bButton\\s*\\(" to "$M.Button", "\\bOutlinedButton\\s*\\(" to "$M.OutlinedButton",
        "\\bTextButton\\s*\\(" to "$M.TextButton", "\\bIconButton\\s*\\(" to "$M.IconButton", "\\bScaffold\\s*\\(" to "$M.Scaffold",
        "\\bMaterialTheme\\b" to "$M.MaterialTheme", "\\bTopAppBar\\s*\\(" to "$M.TopAppBar", "\\bCard\\s*\\(" to "$M.Card",
        "\\bSurface\\s*\\(" to "$M.Surface", "\\bHorizontalDivider\\s*\\(" to "$M.HorizontalDivider", "\\bDivider\\s*\\(" to "$M.Divider",
        "\\bOutlinedTextField\\s*\\(" to "$M.OutlinedTextField", "\\bTextField\\s*\\(" to "$M.TextField",
        "\\bSwitch\\s*\\(" to "$M.Switch", "\\bCheckbox\\s*\\(" to "$M.Checkbox",
        "\\bLaunchedEffect\\s*\\(" to "$R.LaunchedEffect", "\\bremember\\s*[({]" to "$R.remember",
        "\\bmutableStateOf\\s*[(<]" to "$R.mutableStateOf", "\\bmutableIntStateOf\\s*\\(" to "$R.mutableIntStateOf",
        "\\bmutableFloatStateOf\\s*\\(" to "$R.mutableFloatStateOf", "\\bderivedStateOf\\s*[({]" to "$R.derivedStateOf",
        "\\brememberCoroutineScope\\s*\\(" to "$R.rememberCoroutineScope", "\\bDisposableEffect\\s*\\(" to "$R.DisposableEffect",
        "\\bModifier\\b" to "$U.Modifier", "\\bAlignment\\b" to "$U.Alignment", "\\bColor\\s*[(.]" to "$U.graphics.Color",
        "\\.dp\\b" to "$U.unit.dp", "\\.sp\\b" to "$U.unit.sp", "\\bFontWeight\\b" to "$U.text.font.FontWeight",
        "\\bTextAlign\\b" to "$U.text.style.TextAlign", "\\bTextOverflow\\b" to "$U.text.style.TextOverflow",
        "\\.clip\\(" to "$U.draw.clip", "\\.alpha\\(" to "$U.draw.alpha", "\\.scale\\(" to "$U.draw.scale", "\\.rotate\\(" to "$U.draw.rotate",
        "\\.graphicsLayer\\b" to "$U.graphics.graphicsLayer", "\\bBrush\\." to "$U.graphics.Brush",
        "\\bContentScale\\b" to "$U.layout.ContentScale", "\\bLocalContext\\b" to "$U.platform.LocalContext",
        "\\bComposable\\b" to "$R.Composable",
        "\\banimateFloatAsState\\s*\\(" to "androidx.compose.animation.core.animateFloatAsState",
        "\\banimateColorAsState\\s*\\(" to "androidx.compose.animation.animateColorAsState",
        "\\banimateDpAsState\\s*\\(" to "androidx.compose.animation.core.animateDpAsState",
        "\\bAnimatedVisibility\\s*\\(" to "androidx.compose.animation.AnimatedVisibility",
        "\\bAnimatedContent\\s*\\(" to "androidx.compose.animation.AnimatedContent",
        "\\bspring\\s*\\(" to "androidx.compose.animation.core.spring", "\\btween\\s*\\(" to "androidx.compose.animation.core.tween",
        "\\bAnimatable\\s*\\(" to "androidx.compose.animation.core.Animatable",
        "\\brememberInfiniteTransition\\s*\\(" to "androidx.compose.animation.core.rememberInfiniteTransition",
        "\\binfiniteRepeatable\\s*\\(" to "androidx.compose.animation.core.infiniteRepeatable",
        "\\bLinearEasing\\b" to "androidx.compose.animation.core.LinearEasing", "\\bFastOutSlowInEasing\\b" to "androidx.compose.animation.core.FastOutSlowInEasing",
        "\\bRepeatMode\\b" to "androidx.compose.animation.core.RepeatMode",
        "\\bfadeIn\\s*\\(" to "androidx.compose.animation.fadeIn", "\\bfadeOut\\s*\\(" to "androidx.compose.animation.fadeOut",
        "\\bslideInVertically\\s*\\(" to "androidx.compose.animation.slideInVertically", "\\bslideOutVertically\\s*\\(" to "androidx.compose.animation.slideOutVertically",
        "\\bslideInHorizontally\\s*\\(" to "androidx.compose.animation.slideInHorizontally", "\\bslideOutHorizontally\\s*\\(" to "androidx.compose.animation.slideOutHorizontally",
        "\\bscaleIn\\s*\\(" to "androidx.compose.animation.scaleIn", "\\bscaleOut\\s*\\(" to "androidx.compose.animation.scaleOut",
        "\\bviewModel\\s*\\(" to "androidx.lifecycle.viewmodel.compose.viewModel",
        "\\bcollectAsStateWithLifecycle\\s*\\(" to "androidx.lifecycle.compose.collectAsStateWithLifecycle",
        "\\bviewModelScope\\b" to "androidx.lifecycle.viewModelScope",
        "\\bdelay\\s*\\(" to "kotlinx.coroutines.delay", "\\blaunch\\s*\\{" to "kotlinx.coroutines.launch"
    ).filter { it.second.isNotEmpty() }.map { Regex(it.first) to it.second }

    private val experimental = Regex("\\b(TopAppBar|CenterAlignedTopAppBar|LargeTopAppBar|ModalBottomSheet|ExposedDropdownMenuBox|DatePicker|TimePicker|SearchBar|FilterChip|AssistChip)\\b")

    fun fix(src: String): String {
        var s = src
        val importedNames = Regex("(?m)^import\\s+([\\w.]+)").findAll(s).map { it.groupValues[1] }.toSet()
        val wildcards = importedNames.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }.toSet()
        val simple = importedNames.map { it.substringAfterLast('.') }.toSet()
        val add = linkedSetOf<String>()
        val code = s.lines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") }.joinToString("\n")
        for ((rx, imp) in symbols) {
            if (!rx.containsMatchIn(code)) continue
            val name = imp.substringAfterLast('.')
            if (name in simple || imp.substringBeforeLast('.') in wildcards) continue
            // the file defines or declares it itself
            if (Regex("(?:class|object|interface|fun|val|var)\\s+$name\\b").containsMatchIn(code)) continue
            add.add(imp)
        }
        if (Regex("\\bby\\s+(?:remember|[\\w.]+\\.collectAsState|animate\\w+AsState)").containsMatchIn(code) && "$R.getValue" !in importedNames) add.add("$R.getValue")
        if (Regex("\\bby\\s+remember\\w*\\s*\\{[^}]*mutableStateOf|\\bby\\s+remember\\s*\\{\\s*mutable").containsMatchIn(code) && "$R.setValue" !in importedNames && Regex("\\bvar\\s+\\w+\\s+by\\b").containsMatchIn(code)) add.add("$R.setValue")
        if (add.isNotEmpty()) {
            val pkgEnd = Regex("(?m)^package\\s+[\\w.]+.*$").find(s)?.range?.last ?: -1
            val block = add.joinToString("\n") { "import $it" }
            s = if (pkgEnd >= 0) s.substring(0, pkgEnd + 1) + "\n\n" + block + s.substring(pkgEnd + 1) else block + "\n" + s
        }
        if (experimental.containsMatchIn(code) && "ExperimentalMaterial3Api" !in s) {
            s = "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n" + s
        }
        return s
    }

}
