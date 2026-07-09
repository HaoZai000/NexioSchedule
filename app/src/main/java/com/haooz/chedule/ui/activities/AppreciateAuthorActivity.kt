/** 赞赏作者页面 */
package com.haooz.chedule.ui.activities
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars

import com.haooz.chedule.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

private val sampleAppreciations = listOf(
    AppreciationItem("mendacious", "¥0.88", "2026-07-03"),
)

class AppreciateAuthorActivity : ComponentActivity() {
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
                AppreciateAuthorScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun AppreciateAuthorScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 30f).coerceIn(0f, 1f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = if (blurAlpha > 0f) {
                    Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors)
                } else {
                    Modifier
                },
                color = topBarColor,
                title = "赞赏作者",
                largeTitle = "赞赏作者",
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 20.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
                    }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 赞赏码卡片
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.zanshangma),
                                contentDescription = "赞赏码",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                // 赞赏列表标题
                item {
                    SmallTitle(
                        text = "赞赏明细",
                        modifier = Modifier.offset(x = (-16).dp)
                    )
                }

                // 赞赏列表
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            sampleAppreciations.forEachIndexed { index, item ->
                                AppreciationListItem(item = item)
                                if (index < sampleAppreciations.lastIndex) {
                                    // 分割线
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(0.5.dp)
                                            .background(MiuixTheme.colorScheme.surfaceVariant)
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

private data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
)



@Composable
private fun AppreciationListItem(item: AppreciationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像占位
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.nickname.first().toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = item.nickname,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.time,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }

        Text(
            text = item.amount,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary
        )
    }
}
