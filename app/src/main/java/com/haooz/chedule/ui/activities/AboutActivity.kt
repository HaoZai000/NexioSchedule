/** 关于页面 */
package com.haooz.chedule.ui.activities
import android.annotation.SuppressLint
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars

import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import com.haooz.chedule.effect.BgEffectBackground
import com.haooz.chedule.ui.components.BlurredBar
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.components.rememberBlurBackdrop
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isInDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass"
    val liquidGlassBackdrop = if (isLiquidGlass) {
        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    } else null

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val appName = remember {
        packageInfo?.applicationInfo?.labelRes?.let {
            context.getString(it)
        } ?: context.applicationInfo?.nonLocalizedLabel?.toString() ?: "Nexio课程表"
    }
    val appVersion = remember {
        packageInfo?.versionName ?: "未知版本"
    }

    val backdrop = rememberBlurBackdrop()
    val lazyListState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }

    var dynamicBackground by remember { mutableStateOf(true) }

    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val cardBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }

    Scaffold(
        topBar = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                    tintIntensity = scrollProgress * 0.2f,
                ) {
                    SmallTopAppBar(
                        color = Color.Transparent,
                        title = "关于应用",
                        modifier = Modifier.zIndex(1f),
                        navigationIcon = {}
                    )
                    LiquidTopBarButton(
                        onClick = { onBack() },
                        backdrop = liquidGlassBackdrop,
                        icon = MiuixIcons.Medium.ChevronBackward,
                        contentDescription = "返回",
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                        iconSize = 22.dp,
                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                        useBackdropShadow = true
                    )
                }
            } else {
                val barColor = if (blurActive) {
                    Color.Transparent
                } else {
                    if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
                }
                val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                )
                BlurredBar(backdrop, blurActive) {
                    SmallTopAppBar(
                        title = "关于应用",
                        scrollBehavior = scrollBehavior,
                        color = barColor,
                        titleColor = titleColor,
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            IconButton(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onBack() },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                    else Modifier
                )
        ) {
            BgEffectBackground(
                dynamicBackground = dynamicBackground,
                isFullSize = true,
                modifier = Modifier.fillMaxSize(),
                bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
                alpha = { 1f - scrollProgress },
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = innerPadding.calculateTopPadding() + 72.dp,
                        start = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
                        end = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val appIcon = remember {
                    val drawable = context.applicationInfo.icon.let { resId ->
                        context.getDrawable(resId)
                    }
                    if (drawable != null) {
                        val bitmap = createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1)
                        )
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap.asImageBitmap()
                    } else null
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                            clip = true
                            shape = RoundedRectangle(24.dp)
                            alpha = 1 - iconProgress
                            scaleX = 1 - (iconProgress * 0.05f)
                            scaleY = 1 - (iconProgress * 0.05f)
                        }
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp),
                        )
                    } else {
                        Text(
                            text = appName.take(1),
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                        )
                    }
                }
                Text(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 5.dp)
                        .graphicsLayer {
                            val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - nameProgress
                            scaleX = 1 - (nameProgress * 0.05f)
                            scaleY = 1 - (nameProgress * 0.05f)
                        }
                        .then(
                            if (backdrop != null) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedRectangle(16.dp),
                                    blurRadius = 150f,
                                    colors = BlurDefaults.blurColors(
                                        blendColors = logoBlend,
                                    ),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    text = appName,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 35.sp,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - verProgress
                            scaleX = 1 - (verProgress * 0.05f)
                            scaleY = 1 - (verProgress * 0.05f)
                        },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = "v$appVersion",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .then(
                        if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        else Modifier
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() +
                            if (isLiquidGlass) { if (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() > 0.dp) -20.dp else (-32).dp } else 0.dp,
                    start = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
                    end = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
                ),
            ) {
                item(key = "logoSpacer") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    )
                }

                item(key = "about") {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp)
                                .then(
                                    if (backdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedRectangle(20.dp),
                                            blurRadius = 60f,
                                            colors = BlurDefaults.blurColors(
                                                blendColors = cardBlend,
                                            ),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = CardDefaults.defaultColors(
                                if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.background,
                                Color.Transparent,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "更新设置",
                                    onClick = {
                                        val intent = Intent(context, UpdateSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "项目Gitee仓库",
                                    onClick = {
                                        uriHandler.openUri("https://gitee.com/com_haooz_account/hyper_schedule")
                                    }
                                )
                                AboutItem(title = "应用框架", value = "Jetpack Compose + Miuix UI")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .then(
                                    if (backdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedRectangle(20.dp),
                                            blurRadius = 60f,
                                            colors = BlurDefaults.blurColors(
                                                blendColors = cardBlend,
                                            ),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = CardDefaults.defaultColors(
                                if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.background,
                                Color.Transparent,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AboutItem(title = "开源许可", value = "Apache 2.0")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        var expanded by remember { mutableStateOf(true) }
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 90f else 0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "arrowRotation"
                        )
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { expanded = !expanded }
                                .then(
                                    if (backdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedRectangle(20.dp),
                                            blurRadius = 60f,
                                            colors = BlurDefaults.blurColors(
                                                blendColors = cardBlend,
                                            ),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = CardDefaults.defaultColors(
                                if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.background,
                                Color.Transparent,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 17.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "特别致谢",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = MiuixIcons.ChevronForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer {
                                                rotationZ = rotation
                                            },
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                AnimatedVisibility(
                                    visible = expanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Miuix",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/compose-miuix-ui/miuix")
                                                }
                                            )
                                            Text(
                                                text = "Yukonga",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Capsule",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/Kyant0/Capsule")
                                                }
                                            )
                                            Text(
                                                text = "Kyant0",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "OkHttp",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/square/okhttp")
                                                }
                                            )
                                            Text(
                                                text = "Square",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "warehouse",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/XingHeYuZhuan/shiguang_warehouse")
                                                }
                                            )
                                            Text(
                                                text = "XingHeYuZhuan",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "HyperNotification",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/limczhh/HyperNotification")
                                                }
                                            )
                                            Text(
                                                text = "limczhh",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Shizuku",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/RikkaApps/Shizuku")
                                                }
                                            )
                                            Text(
                                                text = "RikkaApps",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Backdrop",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    uriHandler.openUri("https://github.com/Kyant0/AndroidLiquidGlass")
                                                }
                                            )
                                            Text(
                                                text = "Kyant0",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(
                                color = Color.Transparent,
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "© 2026 Nexio课程表 · 作者:",
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                    Text(
                                        text = "Haooz",
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                uriHandler.openUri("https://www.coolapk.com/u/29693763")
                                            }
                                            .padding(start = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "赞赏作者",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            val intent = Intent(context, AppreciateAuthorActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AboutItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }
}
