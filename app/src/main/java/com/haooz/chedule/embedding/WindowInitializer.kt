package com.haooz.chedule.embedding

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitController
import com.haooz.chedule.R

/**
 * Activity 嵌入规则初始化器
 *
 * 通过 App Startup 在应用启动时自动加载 res/xml/main_split_config.xml 分屏规则。
 * 规则定义了"我的页 → 二级页面"的分屏（34% / 66%）。
 *
 * 分割线由 SettingsScreen 自行在 UI 层绘制（绘制在左侧页面最右侧），
 * 不使用 window embedding 的 DividerAttributes，避免过渡动画期间颜色异常。
 */
class WindowInitializer : Initializer<RuleController> {

    override fun create(context: Context): RuleController {
        val ruleController = RuleController.getInstance(context)
        val rules = RuleController.parseRules(context, R.xml.main_split_config)
        ruleController.setRules(rules)

        // 诊断日志
        val splitController = SplitController.getInstance(context)
        Log.i(TAG, "Activity 嵌入规则已加载，规则数量: ${rules.size}")
        Log.i(TAG, "  分屏支持状态: ${splitController.splitSupportStatus}")

        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

    companion object {
        private const val TAG = "ActivityEmbedding"
    }
}
