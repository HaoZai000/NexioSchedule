package com.haooz.chedule.i18n

/**
 * 首次启动引导文案。
 *
 * 中文取自小米开机引导（com.android.provision v17）的资源表原文，英文为对应翻译，
 * 保证与 HyperOS 引导页的措辞一致。
 */
object OnboardingStr {

    val back = L("返回", "Back")
    val next = L("继续", "Continue")
    val start = L("开始使用", "All set")

    // ---- 设置语言 ----
    val languageTitle = L("设置语言", "Set language")
    val languageDesc = L("选择您要使用的语言", "Choose the language you want to use")
    val chinese = L("简体中文", "简体中文")
    val english = L("English", "English")

    // ---- 协议与声明 ----
    val termsTitle = L("协议与声明", "Terms & statement")
    val termsDesc = L(
        "您需要同意《Nexio课程表隐私政策》才可继续使用本应用，" +
            "我们将严格遵守相关法律法规和该政策以保护您的个人信息。",
        "You need to agree to the Nexio Schedule Privacy Policy before continuing. " +
            "We strictly follow applicable laws and this policy to protect your personal information.",
    )
    val agreeTerms = L(
        "我已阅读并同意《Nexio课程表隐私政策》",
        "I have read and agree to the Nexio Schedule Privacy Policy",
    )

    // ---- 位置服务 ----
    val locationTitle = L("位置服务", "Location services")
    val locationDesc = L(
        "允许应用或网站使用设备当前的位置信息，提供基于位置的服务。",
        "Allow apps and websites to use your device's location for location-based services.",
    )

    // ---- 基础设置 ----
    val basicTitle = L("基础设置", "Basic settings")
    val basicDesc = L(
        "以下选项会作为应用的初始偏好，之后随时可以在设置里修改。",
        "The following options are your initial preferences. You can change them anytime in Settings.",
    )

    val locationSwitch = L("使用位置信息", "Use location")

    // ---- 完成页 ----
    // 完成页正文只有一句说明：标题位置留给与欢迎页同款的「Nexio课程表」字标，
    // 这条 title 只作为该步骤的语义标题，不参与渲染。
    val finishTitle = L("Nexio课程表", "Nexio Schedule")
    val finishDesc = L(
        "设置已完成，开始安排你的课程吧。",
        "You're all set. Start planning your classes.",
    )
}
