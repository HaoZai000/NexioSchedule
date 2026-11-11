package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.activities.today.toCardData
import com.haooz.chedule.ui.activities.today.weatherIconRes
import com.haooz.chedule.ui.components.WeatherAlertBadge
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.weather.WeatherStore
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课表页顶栏左侧的天气条：图标 + 当前温度 + 最高级别预警徽标，点击进天气详情。
 *
 * 数据只读 [WeatherStore]，且**必须**走 [toCardData] —— 今日卡用的是同一套映射与取整，
 * 这里直接读快照会因 `roundToInt` 与日夜判定口径不同，出现两页温度/图标对不上的问题。
 */
@Composable
internal fun ScheduleWeatherBadge(modifier: Modifier = Modifier) {
    val snapshot by WeatherStore.snapshot.collectAsState()
    // 与今日卡一致：没有可用的实况就不画
    val weather = snapshot?.toCardData() ?: return
    if (!weather.loaded || weather.temperature.isNaN()) return
    val router = LocalAppRouter.current

    Row(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(8.dp))
            .clickable { router.navigate(AppRoute.Weather) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Image(
            painter = painterResource(
                weatherIconRes(weather.weatherType, isNight = weather.isNight())
            ),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "${weather.temperature.toInt()}°",
            style = MiuixTheme.textStyles.body2,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        weather.alert?.let { alert ->
            WeatherAlertBadge(alert = alert)
        }
    }
}
