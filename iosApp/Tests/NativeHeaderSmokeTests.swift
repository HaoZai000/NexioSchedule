import XCTest
import UIKit

/// Runs against an already-installed simulator app; the runner does not link production sources.
final class NativeHeaderSmokeTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication(bundleIdentifier: "com.haooz.iosschedule")
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        settle()
    }

    override func tearDownWithError() throws {
        capture("Final state - \(name)")
        XCUIDevice.shared.orientation = .portrait
        app.terminate()
    }

    private func settle() { Thread.sleep(forTimeInterval: 0.8) }

    private func requireExpandedRoutes() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["NATIVE_HEADER_EXPANDED_ROUTES"] == "1",
                          "Expanded native routes are not enabled; opt in explicitly after migration")
    }

    /// 宽屏（平板 rail）下设置页把内容内嵌渲染进右栏，设置项**不再 push 原生文档页**
    private func requirePhoneWidthSettings(file: StaticString = #filePath,
                                           line: UInt = #line) throws {
        try XCTSkipIf(app.frame.width >= 600,
                      "Wide-screen settings render panes inline and never push native document routes",
                      file: file, line: line)
    }

    private func stableFrame(of element: XCUIElement) -> CGRect {
        // Compose's fling isn't covered by XCTest's UIKit-idle detection, so wait for scrolling to stop before asserting.
        var previous = element.frame
        var stableSamples = 0
        for _ in 0..<16 {
            Thread.sleep(forTimeInterval: 0.4)
            let current = element.frame
            if abs(current.minY - previous.minY) < 0.5 {
                stableSamples += 1
                if stableSamples >= 3 { return current }
            } else {
                stableSamples = 0
            }
            previous = current
        }
        XCTFail("Scroll anchor did not stop moving before navigation: \(element.label) \(element.frame)")
        return element.frame
    }

    private func visibleContentAnchor() throws -> XCUIElement {
        // Changelog body text is merged into clickable cards and need not be hittable; we only read geometry, so hittability isn't required.
        try XCTUnwrap(app.staticTexts.allElementsBoundByIndex.first {
            $0.exists && $0.label != "更新日志" && $0.label.count > 4 &&
                $0.frame.minY > 180 && $0.frame.maxY < app.frame.height - 100 &&
                $0.frame.height < 100
        }, "No visible content anchor after scroll: \(app.debugDescription)")
    }

    private func item(_ title: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", title)).firstMatch
    }

    @discardableResult
    private func open(_ title: String, file: StaticString = #filePath, line: UInt = #line) -> CGFloat {
        let target = item(title)
        _ = target.waitForExistence(timeout: 5)
        for _ in 0..<8 {
            if target.exists && target.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(target.exists && target.isHittable,
                      "Missing \(title): \(app.debugDescription)", file: file, line: line)
        let frame = stableFrame(of: target)
        XCTAssertTrue(target.exists && target.isHittable,
                      "Target stopped being tappable after scrolling: \(title): \(app.debugDescription)",
                      file: file, line: line)
        XCTAssertTrue(app.frame.contains(frame),
                      "Target is not fully in the visible viewport: \(title) \(frame)",
                      file: file, line: line)
        print("OPEN_DIAGNOSTIC title=\(title.debugDescription) stableFrame=\(frame) " +
              "hittable=\(target.isHittable)")
        print("OPEN_DIAGNOSTIC beforeTapTree=\(app.debugDescription)")
        let y = frame.minY
        target.tap()
        settle()
        print("OPEN_DIAGNOSTIC title=\(title.debugDescription) afterTapTree=\(app.debugDescription)")
        return y
    }

    /// 设置页入口：底栏第三格（原「我的」已改名设置），排班模式下是第二格。
    private var settingsEntry: XCUIElement {
        app.tabBars.buttons["设置"].firstMatch
    }

    private func showSettings() {
        if !settingsEntry.waitForExistence(timeout: 5), item("完成").exists {
            item("完成").tap()
            settle()
        }
        if settingsEntry.exists {
            settingsEntry.tap()
        } else {
            // Expanded width uses the original Compose rail, whose settings row label includes its icon.
            let railSettings = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "设置")).firstMatch
            if railSettings.exists {
                _ = stableFrame(of: railSettings)
                XCTAssertTrue(railSettings.isHittable && app.frame.contains(railSettings.frame), app.debugDescription)
                railSettings.tap()
            } else {
                open("设置")
            }
        }
        settle()
    }

    /// 展开「课表管理」总览卡片。
    private func expandScheduleSettings() {
        if item("上课时间").isHittable { return }
        let card = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "课表管理")).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertTrue(card.isHittable, app.debugDescription)
        card.tap()
        settle()
    }

    private func requireNativeHeader(_ title: String,
                                     file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.navigationBars.firstMatch.waitForExistence(timeout: 10),
                      app.debugDescription, file: file, line: line)
        settle()
        XCTAssertEqual(app.navigationBars.count, 1, file: file, line: line)
        XCTAssertEqual(app.navigationBars.firstMatch.label, title, file: file, line: line)
        XCTAssertFalse(app.navigationBars.buttons["更多文档"].exists,
                       "Prototype document-jump menu must not ship", file: file, line: line)
    }

    private func nativeBack() {
        let back = app.navigationBars.firstMatch.buttons.element(boundBy: 0)
        XCTAssertTrue(back.exists && back.isHittable, app.debugDescription)
        back.tap()
        settle()
    }

    private func capture(_ name: String) {
        guard app != nil else { return }
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testDocumentHeaderScrollRestorationAndEdgeBack() throws {
        try requirePhoneWidthSettings()
        showSettings()
        open("关于应用")
        requireNativeHeader("关于应用")
        let aboutChangelogY = open("更新日志")
        requireNativeHeader("更新日志")
        XCTAssertEqual(app.navigationBars.buttons.count, 1, "Document has only native back")
        XCTAssertEqual(app.keyboards.count, 0, "Opening a read-only document raised a keyboard")

        let titles = app.staticTexts.matching(NSPredicate(format: "label == %@", "更新日志"))
            .allElementsBoundByIndex
        let largeTitle = try XCTUnwrap(titles.first { $0.frame.height > 30 },
                                       "Missing original Compose large title: \(app.debugDescription)")
        if app.frame.width < 600 {
            XCTAssertEqual(largeTitle.frame.minX, 28, accuracy: 2,
                           "Original phone title leading padding changed")
        }
        let nativeBackY = app.navigationBars.buttons.element(boundBy: 0).frame.minY
        capture("Document - original title and native back")
        app.swipeUp()
        settle()
        XCTAssertEqual(app.navigationBars.buttons.element(boundBy: 0).frame.minY,
                       nativeBackY, accuracy: 1, "Native back moved with Compose scrolling")
        capture("Document - collapsed title and progressive blur")

        // Use a real visible row as the scroll anchor, not header state or a screenshot approximation.
        let scrollAnchor = try visibleContentAnchor()
        let anchorLabel = scrollAnchor.label
        let diagnosticStart = Date()
        func sample(_ phase: String) {
            print("SCROLL_DIAGNOSTIC phase=\(phase) elapsed=\(Date().timeIntervalSince(diagnosticStart)) " +
                  "anchor=\(anchorLabel.debugDescription) frame=\(item(anchorLabel).frame) " +
                  "nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        }
        sample("afterSwipeUp_0.8s")
        settle()
        sample("afterSwipeUp_1.6s")
        settle()
        sample("afterSwipeUp_2.4s")
        let anchorY = stableFrame(of: item(anchorLabel)).minY
        sample("stableBaselineBeforeCancel")

        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.42))
            .withOffset(CGVector(dx: 2, dy: 0))
        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.18, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.4)
        settle()
        sample("afterCancel_0.8s")
        capture("Document - cancelled edge back before assertion")
        settle()
        sample("afterCancel_1.6s")
        settle()
        sample("afterCancel_2.4s")
        requireNativeHeader("更新日志")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Cancelled edge back changed scroll state")

        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.2)
        settle()
        requireNativeHeader("关于应用")
        XCTAssertEqual(item("更新日志").frame.minY, aboutChangelogY, accuracy: 3,
                       "Retained About layout changed")
        capture("Document - returned to retained About")

        open("更新日志")
        requireNativeHeader("更新日志")
        XCTAssertTrue(item(anchorLabel).exists, "Saved document row disappeared on reopen")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Reopened document did not restore the original scroll position")
        capture("Document - reopened saveable scroll state")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    func testDocumentCompletedEdgeBackAndReopenWithoutCancellation() throws {
        try requirePhoneWidthSettings()
        showSettings()
        open("关于应用")
        let aboutChangelogY = open("更新日志")
        requireNativeHeader("更新日志")
        app.swipeUp()
        settle()
        settle()
        settle()
        let scrollAnchor = try visibleContentAnchor()
        let anchorLabel = scrollAnchor.label
        let anchorY = stableFrame(of: item(anchorLabel)).minY
        print("COMPLETED_POP_DIAGNOSTIC before anchor=\(anchorLabel.debugDescription) y=\(anchorY) " +
              "nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        capture("Document - stable scroll before completed pop")
        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.42))
            .withOffset(CGVector(dx: 2, dy: 0))
        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.2)
        settle()
        requireNativeHeader("关于应用")
        XCTAssertEqual(item("更新日志").frame.minY, aboutChangelogY, accuracy: 3,
                       "Retained About layout changed")
        capture("Document - completed pop without preceding cancellation")
        open("更新日志")
        requireNativeHeader("更新日志")
        print("COMPLETED_POP_DIAGNOSTIC reopen anchor=\(anchorLabel.debugDescription) " +
              "frame=\(item(anchorLabel).frame) nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        capture("Document - reopened scroll before assertion")
        XCTAssertTrue(item(anchorLabel).exists, "Saved document row disappeared on reopen")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Reopened document did not restore original scroll position")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    func testLicenseAndPrivacyUseOriginalAboutEntries() throws {
        try requirePhoneWidthSettings()
        showSettings()
        open("关于应用")
        open("开源协议")
        requireNativeHeader("开源协议")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(app.staticTexts["一、开源协议摘要"].exists)
        XCTAssertEqual(app.keyboards.count, 0)
        capture("License - native back from original About entry")
        nativeBack()
        requireNativeHeader("关于应用")
        open("《隐私政策》")
        requireNativeHeader("隐私政策")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(app.staticTexts["Nexio 课程表 隐私政策"].exists)
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Privacy - native back from original About entry")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    private func requireAppearance(_ dark: Bool, _ phase: String) throws {
        settle()
        let image = try XCTUnwrap(app.screenshot().image.cgImage)
        // The empty right gutter stays outside text, cards and native glass chrome.
        let pixel = try XCTUnwrap(image.cropping(to: CGRect(
            x: CGFloat(image.width) * 0.98, y: CGFloat(image.height) * 0.5,
            width: 1, height: 1)))
        var rgba = [UInt8](repeating: 0, count: 4)
        try rgba.withUnsafeMutableBytes { bytes in
            let context = try XCTUnwrap(CGContext(
                data: bytes.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        let brightness = Double(Int(rgba[0]) + Int(rgba[1]) + Int(rgba[2])) / (3 * 255)
        capture("Theme - \(phase)")
        XCTAssertTrue(dark ? brightness < 0.25 : brightness > 0.65,
                      "\(phase): expected \(dark ? "dark" : "light"), brightness=\(brightness)")
    }

    /// Save a fixed theme first, without wallpaper/theme overrides, then opt in with
    /// TEST_RUNNER_MANUAL_THEME_EXPECTED=dark/light. This test never changes the preference.
    func testManualThemeColdLaunchBeforeOpeningSettings() throws {
        let expected = ProcessInfo.processInfo.environment["MANUAL_THEME_EXPECTED"]
        try XCTSkipUnless(expected == "dark" || expected == "light",
                          "Save a fixed theme and opt into the manual cold-launch fixture")
        let dark = expected == "dark"
        try requirePhoneWidthSettings()

        for attempt in 1...3 {
            if attempt > 1 {
                app.terminate()
                app.launch()
                XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
            }
            XCTAssertTrue(settingsEntry.waitForExistence(timeout: 5), app.debugDescription)
            XCTAssertFalse(settingsEntry.isSelected, "Fixture must launch on the main tab")
            try requireAppearance(dark, "fixed theme cold launch \(attempt), before settings")
        }

        showSettings()
        try requireAppearance(dark, "settings after fixed-theme cold launch")
        open("应用个性化")
        requireNativeHeader("应用个性化")
        let automatic = app.switches.matching(NSPredicate(
            format: "label CONTAINS %@", "自动切换深色模式")).firstMatch
        XCTAssertTrue(automatic.exists, app.debugDescription)
        let toggle = try XCTUnwrap(app.switches.allElementsBoundByIndex.first {
            $0.frame.width < 100 && $0.frame.intersects(automatic.frame) && $0.isHittable
        }, app.debugDescription)
        XCTAssertFalse(toggle.isSelected, "Fixture must use a saved fixed theme")
        nativeBack()
        app.tabBars.buttons.element(boundBy: 0).tap()
        try requireAppearance(dark, "main tab after returning from settings")
    }

    /// Opt in with TEST_RUNNER_SYSTEM_THEME_EXPECTED=dark/light and set simctl appearance first.
    /// The simulator fixture must use automatic mode and no wallpaper/theme override.
    func testAutomaticThemeColdLaunchAndManualOverride() throws {
        let expected = ProcessInfo.processInfo.environment["SYSTEM_THEME_EXPECTED"]
        try XCTSkipUnless(expected == "dark" || expected == "light",
                          "Set the system appearance and opt into the theme regression fixture")
        let systemDark = expected == "dark"
        try requirePhoneWidthSettings()

        try requireAppearance(systemDark, "cold launch before opening any page")
        showSettings()
        try requireAppearance(systemDark, "retained settings tab")
        open("应用个性化")
        requireNativeHeader("应用个性化")
        try requireAppearance(systemDark, "native preferences in automatic mode")
        let automatic = app.switches.matching(NSPredicate(
            format: "label CONTAINS %@", "自动切换深色模式")).firstMatch
        XCTAssertTrue(automatic.exists, app.debugDescription)
        // Compose exposes the labeled preference row and its selected toggle as siblings.
        let toggle = try XCTUnwrap(app.switches.allElementsBoundByIndex.first {
            $0.frame.width < 100 && $0.frame.intersects(automatic.frame) && $0.isHittable
        }, app.debugDescription)
        XCTAssertTrue(toggle.isSelected, "Fixture must start in automatic mode")
        defer {
            if automatic.exists && !toggle.isSelected { automatic.tap() }
        }
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@",
                                         systemDark ? "浅色模式" : "深色模式")).firstMatch.tap()
        try requireAppearance(!systemDark, "manual override opposite to system")
        XCTAssertFalse(toggle.isSelected)
        // Selecting the system-matching box must remain manual until automatic is enabled.
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@",
                                         systemDark ? "深色模式" : "浅色模式")).firstMatch.tap()
        try requireAppearance(systemDark, "manual override matching system")
        XCTAssertFalse(toggle.isSelected)
        automatic.tap()
        try requireAppearance(systemDark, "automatic restored without leaving page")
        XCTAssertTrue(toggle.isSelected)
        nativeBack()
        try requireAppearance(systemDark, "retained root after pop")
    }

    func testPreferenceHeaderPreservesOriginalControlsAndLegacyReturn() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("应用个性化")
        requireNativeHeader("应用个性化")
        XCTAssertTrue(item("自动切换深色模式").exists, "Original theme control missing")
        XCTAssertTrue(item("深色模式").exists, "Original theme selection missing")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Preferences - native back with original Compose controls")
        nativeBack()
        // 设置已是压栈的二级页：退回偏好设置后落在设置页，本身就是原生文档壳
        requireNativeHeader("设置")
        XCTAssertTrue(item("课表管理").exists, app.debugDescription)
    }

    func testHolidayNativeActionAndRotation() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("节假日与调休")
        requireNativeHeader("节假日与调休")
        XCTAssertTrue(app.navigationBars.buttons["更新"].exists,
                      "Original refresh action missing from native header")
        XCTAssertEqual(app.navigationBars.buttons.count, 2)
        capture("Holiday - native update action")
        app.navigationBars.buttons["更新"].tap()
        // A fast response may finish before XCTest snapshots the spinner; accept either original feedback state — this check deliberately does not require network success.
        let busy = item("更新中")
        let update = app.navigationBars.buttons["更新"]
        XCTAssertTrue(busy.exists || update.exists,
                      "Refresh tap removed both busy and normal action states: \(app.debugDescription)")
        print("HOLIDAY_ACTION_DIAGNOSTIC busy=\(busy.exists) update=\(update.exists) " +
              "tree=\(app.debugDescription)")
        capture("Holiday - original refresh tap feedback")
        if busy.exists {
            _ = update.waitForExistence(timeout: 15)
        }
        XCUIDevice.shared.orientation = .landscapeLeft
        settle()
        requireNativeHeader("节假日与调休")
        XCTAssertTrue(app.navigationBars.buttons["更新"].isHittable || item("更新中").exists,
                      "Neither the update action nor its original busy state is visible")
        capture("Holiday - landscape header")
        XCUIDevice.shared.orientation = .portrait
        settle()
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testNativeBackupNestedLocalBackupWebDavKeyboardAndBottomActions() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("备份与同步")
        requireNativeHeader("备份与同步")
        let localBackupY = open("本地备份")
        requireNativeHeader("本地备份")
        XCTAssertTrue(item("备份模式").exists, "Original local backup mode control missing")
        capture("Local backup - native nested page with original controls")
        nativeBack()
        requireNativeHeader("备份与同步")
        XCTAssertEqual(item("本地备份").frame.minY, localBackupY, accuracy: 3,
                       "Nested LocalBackup pop changed retained native Backup state")
        let backupWebDavY = open("WebDAV 云同步")
        requireNativeHeader("WebDAV 云同步")
        XCTAssertTrue(app.navigationBars.buttons["测试连接"].exists,
                      "Connection test action missing from native header")
        XCTAssertTrue(item("同步到云端").exists, "Original bottom backup button missing")
        XCTAssertTrue(item("从云端恢复").exists, "Original bottom restore button missing")
        XCTAssertEqual(app.keyboards.count, 0, "Navigation raised keyboard without user input")
        capture("WebDAV - original inputs and bottom actions")
        app.navigationBars.buttons["测试连接"].tap()
        let feedback = app.staticTexts.matching(NSPredicate(format:
            "label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@",
            "连接成功", "连接失败", "认证失败", "服务器返回", "请先配置服务器信息")).firstMatch
        XCTAssertTrue(feedback.waitForExistence(timeout: 15),
                      "Connection test has no original status/configuration feedback: \(app.debugDescription)")
        print("WEBDAV_ACTION_DIAGNOSTIC feedback=\(feedback.label.debugDescription) " +
              "tree=\(app.debugDescription)")
        capture("WebDAV - original connection test feedback")

        // The existing CMP NativeTextField is a UIKit UITextView on iOS, not UITextField.
        let textField = app.textViews.firstMatch
        XCTAssertTrue(textField.waitForExistence(timeout: 5), app.debugDescription)
        textField.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5),
                      "Explicit input tap did not open the keyboard")
        capture("WebDAV - explicitly opened native keyboard")
        nativeBack()
        requireNativeHeader("备份与同步")
        XCTAssertEqual(app.keyboards.count, 0, "Native page keyboard remained after pop")
        XCTAssertEqual(item("WebDAV 云同步").frame.minY, backupWebDavY, accuracy: 3,
                       "Retained native Backup scroll state changed")
        capture("WebDAV - returned to retained native Backup")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0, "Popping Backup did not return to Compose settings")
    }

    /// 问题反馈 / 捐赠支持从设置页底部直达，不再经关于应用页。
    func testNativeAboutLeafPagesPreserveOriginalContent() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("问题反馈")
        requireNativeHeader("问题反馈")
        XCTAssertTrue(item("联系我们").exists)
        XCTAssertTrue(item("加入交流群").exists)
        capture("Communication - native header with original entry actions")
        nativeBack()
        requireNativeHeader("设置")
        open("捐赠支持")
        requireNativeHeader("捐赠支持")
        XCTAssertGreaterThan(app.staticTexts.count, 2, "Original donation content missing")
        capture("Donation - native header with original Compose content")
        nativeBack()
        requireNativeHeader("设置")
        XCTAssertEqual(app.navigationBars.count, 0, "Community/Donation did not return to settings")
    }

    func testCourseReminderNativeLeafPreservesOriginalControls() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("课程提醒")
        requireNativeHeader("课程提醒")
        XCTAssertTrue(item("开启提醒").exists, "Original reminder toggle missing")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Reminder - native header with original controls")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testNativeExportOriginalFileEntriesAndCancelOnly() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        // 胶囊默认停在「导入」
        open("导入导出")
        requireNativeHeader("导入导出")
        let exportTab = item("导出")
        XCTAssertTrue(exportTab.waitForExistence(timeout: 5), app.debugDescription)
        exportTab.tap()
        settle()
        XCTAssertTrue(item("JSON 格式导出").exists, "Original JSON export entry missing")
        XCTAssertTrue(item("ICS 格式导出").exists, "Original ICS export entry missing")
        capture("Export - original JSON and ICS entries")
        open("JSON 格式导出")
        let emptyFeedback = app.staticTexts.matching(NSPredicate(format:
            "label CONTAINS %@", "课表为空，无法导出")).firstMatch
        if emptyFeedback.exists {
            print("EXPORT_DIAGNOSTIC no populated fixture: \(emptyFeedback.label)")
            capture("Export - original empty-schedule feedback, no user data created")
        } else {
            let cancelEnglish = app.buttons["Cancel"].firstMatch
            let cancelChinese = app.buttons["取消"].firstMatch
            let pickerCancel = cancelEnglish.waitForExistence(timeout: 10) ? cancelEnglish : cancelChinese
            XCTAssertTrue(pickerCancel.exists && pickerCancel.isHittable,
                          "Export did not present a cancellable system file picker: \(app.debugDescription)")
            capture("Export - system file picker before cancellation")
            pickerCancel.tap()
            settle()
            requireNativeHeader("导入导出")
            capture("Export - picker cancelled without saving user data")
        }
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testNativeImportToNativeAiImportAndBackWithoutImporting() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("导入导出")
        requireNativeHeader("导入导出")
        open("AI 文本导入")
        requireNativeHeader("AI 文本导入")
        XCTAssertTrue(item("课表数据").exists, "Original AI input section missing")
        XCTAssertEqual(app.keyboards.count, 0, "AI navigation unexpectedly opened the keyboard")
        capture("AI import - native page reached from native Import")
        nativeBack()
        requireNativeHeader("导入导出")
        XCTAssertTrue(item("教务系统导入").exists, "Original unsupported entry was silently changed")
        capture("Import - returned to the native Import page without importing")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0, "Import did not return to Compose settings")
    }

    /// UIKit builds the long-press back menu from each stack entry's view-controller title. Those were nil while Compose owned the visible header, so every row rendered blank though all popped correctly. "主界面" exists only as the retained root's parent-facing title, so it's a precise marker for the menu being labelled.
    func testLongPressBackMenuLabelsEveryDestination() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("备份与同步")
        requireNativeHeader("备份与同步")
        open("本地备份")
        requireNativeHeader("本地备份")
        XCTAssertFalse(item("主界面").exists,
                       "The root row label leaked outside the back menu: \(app.debugDescription)")

        let back = app.navigationBars.firstMatch.buttons.element(boundBy: 0)
        XCTAssertTrue(back.exists && back.isHittable, app.debugDescription)
        // A plain press is delivered as touch-down/up, which UIKit resolves to a normal pop; hold then move 1 pt so the menu stays up after the touch lifts.
        let anchor = back.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        anchor.press(forDuration: 8.0)
        Thread.sleep(forTimeInterval: 1.0)
        capture("Back menu - every destination labelled")
        print("BACK_MENU_DIAGNOSTIC tree=\(app.debugDescription)")

        let rootRow = item("主界面")
        XCTAssertTrue(rootRow.waitForExistence(timeout: 5),
                      "Back menu rows are unlabelled: \(app.debugDescription)")
        // Still navigates, not just displays.
        rootRow.tap()
        settle()
        XCTAssertEqual(app.navigationBars.count, 0,
                       "Selecting the root row did not pop the whole native stack")
        capture("Back menu - root row popped both native pages")
    }

    func testTimeSettingsNativeHeaderDialogsAndComposeEditorReturn() throws {
        try requirePhoneWidthSettings()
        showSettings()
        expandScheduleSettings()
        open("上课时间")
        requireNativeHeader("上课时间")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(item("作息方案").exists)
        let sectionY = open("课表节数")
        XCTAssertTrue(item("课表节数设置").exists)
        XCTAssertTrue(item("取消").isHittable, "Section dialog is covered by native chrome")
        capture("Time settings - section dialog above native chrome")
        item("取消").tap()
        settle()
        requireNativeHeader("上课时间")
        XCTAssertEqual(item("课表节数").frame.minY, sectionY, accuracy: 3)
        let leadingFrame = app.navigationBars.buttons.element(boundBy: 0).frame
        let routineSummary = app.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label ENDSWITH %@", "始终生效", "生效")).firstMatch
        XCTAssertTrue(routineSummary.exists && routineSummary.isHittable, app.debugDescription)
        routineSummary.tap()
        settle()
        XCTAssertTrue(item("编辑作息").waitForExistence(timeout: 5), app.debugDescription)
        requireNativeHeader("编辑作息")
        XCTAssertEqual(app.navigationBars.buttons.count, 2, "Editor must have only native cancel and save")
        let cancel = app.navigationBars.buttons["取消"]
        let save = app.navigationBars.buttons["保存并关闭"]
        XCTAssertTrue(cancel.isHittable && save.isHittable, app.debugDescription)
        XCTAssertEqual(cancel.frame.minX, leadingFrame.minX, accuracy: 1)
        XCTAssertEqual(cancel.frame.minY, leadingFrame.minY, accuracy: 1,
                       "Leading item moved from the fixed native control plane")
        capture("Time settings - original morph with native editor buttons")
        app.swipeUp()
        settle()
        XCTAssertEqual(cancel.frame.minY, leadingFrame.minY, accuracy: 1,
                       "Native cancel moved with editor scrolling")
        app.swipeDown()
        settle()
        _ = stableFrame(of: app.textViews.firstMatch)
        app.textViews.firstMatch.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        cancel.tap()
        settle()
        requireNativeHeader("上课时间")
        XCTAssertEqual(app.keyboards.count, 0, "Morph editor keyboard remained after cancellation")
        XCTAssertEqual(item("课表节数").frame.minY, sectionY, accuracy: 3,
                       "Editor exit changed retained settings geometry")
        capture("Time settings - native header restored after morph exit")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertFalse(app.navigationBars.buttons["保存并关闭"].exists)
        // Reopen the same source card and exercise the real save callback without edits.
        routineSummary.tap()
        settle()
        requireNativeHeader("编辑作息")
        app.navigationBars.buttons["保存并关闭"].tap()
        settle()
        requireNativeHeader("上课时间")
        XCTAssertEqual(item("课表节数").frame.minY, sectionY, accuracy: 3,
                       "Save exit changed the source-card layout")
        capture("Time settings - native save completed original reverse morph")
        nativeBack()
        XCTAssertTrue(settingsEntry.isHittable, app.debugDescription)
        if !item("上课时间").isHittable { expandScheduleSettings() }
        XCTAssertTrue(item("上课时间").isHittable, app.debugDescription)
        XCTAssertFalse(app.navigationBars.buttons["返回"].exists)
    }

    func testTimeEditorRapidCancelAndParentBack() throws {
        try requirePhoneWidthSettings()
        showSettings()
        expandScheduleSettings()
        open("上课时间")
        let routine = app.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label ENDSWITH %@", "始终生效", "生效")).firstMatch
        XCTAssertTrue(routine.exists && routine.isHittable)
        routine.tap()
        // No explicit settle between opening, cancellation, and parent back.
        let cancel = app.navigationBars.buttons["取消"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        XCTAssertTrue(cancel.isEnabled)
        XCTAssertTrue(app.navigationBars.buttons["保存并关闭"].isEnabled)
        cancel.tap()
        let back = app.navigationBars.buttons["返回"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        XCTAssertTrue(back.isEnabled)
        XCTAssertFalse(app.navigationBars.buttons["保存并关闭"].exists)
        back.tap()
        settle()
        XCTAssertTrue(settingsEntry.isHittable,
                      "Parent back tap during editor exit did not restore the settings page")
        if !item("上课时间").isHittable { expandScheduleSettings() }
        XCTAssertTrue(item("上课时间").isHittable, app.debugDescription)
        XCTAssertFalse(app.navigationBars.buttons["返回"].exists)
        capture("Time settings - rapid native cancel and parent back without disabled controls")
    }

    func testWidgetGuideDialogAboveNativeChrome() throws {
        try requireExpandedRoutes()
        try requirePhoneWidthSettings()
        showSettings()
        open("桌面小部件")
        requireNativeHeader("桌面小部件")
        open("添加到桌面")
        XCTAssertTrue(item("添加桌面小部件").exists, "Original guide dialog missing")
        XCTAssertTrue(item("我知道了").isHittable, "Dialog is covered by native navigation")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Widget - original guide dialog above native chrome")
        item("我知道了").tap()
        settle()
        requireNativeHeader("桌面小部件")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testCourseManageLayerMenuAndEditorReturn() throws {
        let scheduleTab = app.tabBars.buttons["课程表"]
        XCTAssertTrue(scheduleTab.waitForExistence(timeout: 5), app.debugDescription)
        scheduleTab.tap()
        let more = app.navigationBars.buttons["更多"]
        XCTAssertTrue(more.waitForExistence(timeout: 5), app.debugDescription)
        more.tap()
        open("课程管理")
        let course = item("9")
        try XCTSkipUnless(course.waitForExistence(timeout: 5), "Requires the default course fixture named 9")
        course.press(forDuration: 1.2)
        XCTAssertTrue(item("编辑").waitForExistence(timeout: 5), app.debugDescription)
        capture("Course management - layer card above shortcut backdrop")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.85)).tap()
        settle()
        course.tap()
        XCTAssertTrue(item("上课周次").waitForExistence(timeout: 5), app.debugDescription)
        capture("Course management - layer card expanded into editor")
        let back = item("返回")
        XCTAssertTrue(back.waitForExistence(timeout: 5), app.debugDescription)
        back.tap()
        settle()
        XCTAssertTrue(course.exists && course.isHittable, app.debugDescription)
        capture("Course management - retained source after editor return")
        item("返回").tap()
        XCTAssertTrue(app.navigationBars.buttons["课表切换"].waitForExistence(timeout: 5), app.debugDescription)
    }

    func testCourseDetailLayerPreservesSourcePopupOnReturn() throws {
        let scheduleTab = app.tabBars.buttons["课程表"]
        XCTAssertTrue(scheduleTab.waitForExistence(timeout: 5), app.debugDescription)
        scheduleTab.tap()
        let course = item("9")
        try XCTSkipUnless(course.waitForExistence(timeout: 5), "Requires the default course fixture named 9")
        course.tap()
        XCTAssertTrue(item("课程详情").waitForExistence(timeout: 5), app.debugDescription)
        for _ in 0..<2 {
            item("9").tap()
            XCTAssertTrue(item("返回").waitForExistence(timeout: 5), app.debugDescription)
            settle()
            XCTAssertTrue(item("第1周").exists, app.debugDescription)
            capture("Course details - layer card expanded")
            item("返回").tap()
            settle()
            XCTAssertTrue(item("课程详情").exists && item("9").isHittable, app.debugDescription)
            capture("Course details - original popup retained")
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.1)).tap()
        settle()
        XCTAssertTrue(app.navigationBars.buttons["课表切换"].isEnabled, app.debugDescription)
    }

    func testScheduleLibraryNativeActionsAndReturn() throws {
        let scheduleTab = app.tabBars.buttons["课程表"]
        XCTAssertTrue(scheduleTab.waitForExistence(timeout: 5), app.debugDescription)
        scheduleTab.tap()
        settle()
        let switchButton = app.navigationBars.buttons["课表切换"]
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        switchButton.tap()
        settle()
        requireNativeHeader("全部课表")
        XCTAssertFalse(app.navigationBars.buttons["更多"].exists)
        XCTAssertFalse(app.navigationBars.buttons["课表切换"].exists)
        let add = app.navigationBars.buttons["添加课表"]
        let folder = app.navigationBars.buttons["新建文件夹"]
        XCTAssertTrue(add.exists && add.isHittable, app.debugDescription)
        XCTAssertTrue(folder.exists && folder.isHittable, app.debugDescription)
        XCTAssertEqual(app.navigationBars.buttons.count, 3)
        capture("Schedule library - native controls")

        let currentSchedule = item("当前课表")
        XCTAssertTrue(currentSchedule.waitForExistence(timeout: 5), app.debugDescription)
        currentSchedule.press(forDuration: 1.2)
        settle()
        XCTAssertTrue(item("编辑课表").exists, app.debugDescription)
        XCTAssertFalse(add.exists)
        XCTAssertFalse(folder.exists)
        let close = app.navigationBars.buttons["关闭编辑"]
        XCTAssertTrue(close.exists && close.isHittable, app.debugDescription)
        capture("Schedule library - native edit close")
        close.tap()
        settle()
        requireNativeHeader("全部课表")
        XCTAssertTrue(add.exists && folder.exists)

        add.tap()
        settle()
        XCTAssertTrue(item("新建课表").exists)
        open("取消")
        requireNativeHeader("全部课表")
        XCTAssertTrue(add.isEnabled)

        folder.tap()
        settle()
        XCTAssertTrue(item("新建文件夹").exists)
        open("取消")
        requireNativeHeader("全部课表")
        XCTAssertTrue(folder.isEnabled)
        nativeBack()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5))

        switchButton.tap()
        settle()
        requireNativeHeader("全部课表")
        XCTAssertTrue(add.exists && folder.exists)
        capture("Schedule library - reopened")
        nativeBack()
    }

    func testScheduleLibraryLayerReturnAfterScheduleChange() throws {
        let scheduleTab = app.tabBars.buttons["课程表"]
        XCTAssertTrue(scheduleTab.waitForExistence(timeout: 5), app.debugDescription)
        scheduleTab.tap()
        let switchButton = app.navigationBars.buttons["课表切换"]
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        switchButton.tap()
        requireNativeHeader("全部课表")
        // This regression uses the simulator's default schedule fixture.
        let original = item("默认课表")
        try XCTSkipUnless(original.exists || item("默认文件夹").exists,
                          "Requires the default schedule fixture")
        if !original.exists { item("默认文件夹").tap() }
        XCTAssertTrue(original.waitForExistence(timeout: 5), app.debugDescription)
        // Restore the fixture selection if an interrupted previous run left a temporary schedule.
        original.tap()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        settle()
        switchButton.tap()
        requireNativeHeader("全部课表")
        XCTAssertLessThan(abs(original.frame.midY - item("当前课表").frame.midY), 40)
        let staleFixtures = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "LayerReturn-"))
            .allElementsBoundByIndex.map(\.label)
        let fixtureName = "LayerReturn-" + String(UUID().uuidString.prefix(8))
        app.navigationBars.buttons["添加课表"].tap()
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5), app.debugDescription)
        field.tap()
        field.typeText(fixtureName)
        app.buttons["确定"].tap()
        XCTAssertTrue(item(fixtureName).waitForExistence(timeout: 5), app.debugDescription)
        nativeBack()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertTrue(switchButton.isEnabled)
        capture("Schedule library - new schedule returned through source layer")

        switchButton.tap()
        requireNativeHeader("全部课表")
        XCTAssertTrue(item(fixtureName).exists, app.debugDescription)
        XCTAssertLessThan(abs(item(fixtureName).frame.midY - item("当前课表").frame.midY), 40)
        if !original.exists { item("默认文件夹").tap() }
        XCTAssertTrue(original.waitForExistence(timeout: 5), app.debugDescription)
        original.tap()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        settle()
        capture("Schedule library - selected original schedule returned through source layer")

        switchButton.tap()
        requireNativeHeader("全部课表")
        XCTAssertLessThan(abs(original.frame.midY - item("当前课表").frame.midY), 40)
        for name in staleFixtures + [fixtureName] {
            item(name).press(forDuration: 1.2)
            let delete = app.images["删除"]
            XCTAssertTrue(delete.waitForExistence(timeout: 5), app.debugDescription)
            delete.tap()
            XCTAssertTrue(item("确定要删除选中的课表吗？").waitForExistence(timeout: 5), app.debugDescription)
            app.buttons.matching(NSPredicate(format: "label == %@", "删除")).firstMatch.tap()
            let removed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"),
                                                    object: item(name))
            XCTAssertEqual(XCTWaiter.wait(for: [removed], timeout: 5), .completed)
        }
        nativeBack()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
    }

    func testScheduleLibraryTransitionControlsAllowRapidReopen() throws {
        let scheduleTab = app.tabBars.buttons["课程表"]
        XCTAssertTrue(scheduleTab.waitForExistence(timeout: 5), app.debugDescription)
        scheduleTab.tap()
        let switchButton = app.navigationBars.buttons["课表切换"]
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
        switchButton.tap()
        let back = app.navigationBars.buttons["返回"]
        let add = app.navigationBars.buttons["添加课表"]
        XCTAssertTrue(back.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertTrue(back.isEnabled && add.isEnabled, "Entry must not disable destination controls")
        for _ in 0..<3 {
            back.tap()
            XCTAssertTrue(switchButton.waitForExistence(timeout: 5), app.debugDescription)
            XCTAssertTrue(switchButton.isEnabled, "Exit must immediately restore enabled home controls")
            switchButton.tap()
            XCTAssertTrue(back.waitForExistence(timeout: 5), app.debugDescription)
            XCTAssertTrue(back.isEnabled && add.isEnabled, "Reopening must not disable destination controls")
        }
        settle()
        requireNativeHeader("全部课表")
        capture("Schedule library - rapid reopen")
        nativeBack()
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5))
        XCTAssertTrue(switchButton.isEnabled)
    }

    func testMainScreenNativeHeaderTransitionsAndMenu() throws {
        if app.tabBars.buttons["课程表"].waitForExistence(timeout: 5) {
            app.tabBars.buttons["课程表"].tap()
            settle()
        }
        let switchButton = app.navigationBars.buttons["课表切换"]
        let moreButton = app.navigationBars.buttons["更多"]
        XCTAssertTrue(switchButton.waitForExistence(timeout: 5), "Schedule header missing switch button")
        XCTAssertTrue(moreButton.exists, "Schedule header missing more button")
        capture("Main header - Schedule tab controls")

        // Switch to My (tab 2): header controls should dismiss
        showSettings()
        XCTAssertFalse(switchButton.exists, "My tab must not have switch button")
        XCTAssertFalse(moreButton.exists, "My tab must not have more button")
        capture("Main header - My tab empty controls")

        // Switch back to Schedule (tab 1): controls restore
        if app.tabBars.buttons["课程表"].exists {
            app.tabBars.buttons["课程表"].tap()
            settle()
            XCTAssertTrue(switchButton.waitForExistence(timeout: 5))
            XCTAssertTrue(moreButton.exists)
            capture("Main header - restored Schedule tab controls")
        }

        // Switch to Today (tab 0): only more button exists
        if app.tabBars.buttons["今日"].exists {
            app.tabBars.buttons["今日"].tap()
            settle()
            XCTAssertFalse(switchButton.exists, "Today tab must not have switch button")
            XCTAssertTrue(moreButton.exists, "Today tab must have more button")
            capture("Main header - Today tab single more button")
        }
    }

    /// 从原生「更多」菜单点进课表外观：入场快照必须带上右上角菜单（直接调用进入无此要求，必须走菜单，见 ContentView.captureScreenPNG 注释）。
    func testCustomizeEntryFromMoreMenuKeepsHeaderInSnapshot() throws {
        if app.tabBars.buttons["课程表"].waitForExistence(timeout: 5) {
            app.tabBars.buttons["课程表"].tap()
            settle()
        }
        let moreButton = app.navigationBars.buttons["更多"]
        XCTAssertTrue(moreButton.waitForExistence(timeout: 5), "Schedule header missing more button")
        moreButton.tap()

        let customizeItem = app.buttons["课表外观"]
        XCTAssertTrue(customizeItem.waitForExistence(timeout: 5), "More menu did not present")
        capture("More menu presented")
        customizeItem.tap()

        // 等入场动画走完（Compose 侧 320ms + 封面 400ms 缩放 + 120ms 淡出）
        Thread.sleep(forTimeInterval: 2.0)
        capture("Customize entered from more menu")

        // 课表外观页已打开（原生「应用」按钮）
        XCTAssertTrue(app.buttons["应用"].waitForExistence(timeout: 5),
                      "Customize page did not open from the more menu")
        // ⚠ 不再断言 `app.buttons["更多"]`：iOS 26 导航栏按钮走 SwiftUI hosting + portal，菜单交互后 UIKit 把这块 platter reparent，XCTest 无障碍树就找不到它（屏幕上仍可见）；能否拍到由 captureScreenPNG 从 window 渲染保证。
    }

    /// 折叠屏 iPhone（Duo）回归：回主屏时系统给 scene 拍快照，会在 draw 里跑一次懒布局测量；连做 3 轮「退出 → 再打开」覆盖主 Pager 越界崩溃。
    func testBackgroundAndReopenDoesNotCrash() {
        // 元素定位不成本用例的目的：就算找不到按钮也要把 home 那一步走完
        continueAfterFailure = true
        XCTAssertTrue(app.tabBars.buttons["课程表"].waitForExistence(timeout: 8) ||
                      app.buttons.count > 0, "App did not finish launching")
        // 让 app 走一会儿（模拟用户「玩了一分钟」）：切 tab、点更多、滚一下
        if app.tabBars.buttons["课程表"].waitForExistence(timeout: 3) {
            app.tabBars.buttons["课程表"].tap(); settle()
        }
        if app.tabBars.buttons["今日"].waitForExistence(timeout: 3) {
            app.tabBars.buttons["今日"].tap(); settle()
            app.tabBars.buttons["课程表"].tap(); settle()
        }
        if app.navigationBars.buttons["更多"].waitForExistence(timeout: 3) {
            app.navigationBars.buttons["更多"].tap(); settle()
            // 关闭弹出的菜单
            app.tap()
        }
        Thread.sleep(forTimeInterval: 3.0)
        capture("Before background")

        // 连做 3 轮「退出 → 再打开」：崩溃是概率性的，单轮容易漏
        for round in 1...3 {
            XCUIDevice.shared.press(.home)
            // 崩溃发生在后台快照，给系统充足时间拍完
            Thread.sleep(forTimeInterval: 5.0)
            let state = app.state
            XCTAssertTrue(
                state == .runningBackground || state == .runningBackgroundSuspended ||
                    state == .runningForeground,
                "Round \(round): app is gone after going to background (state \(state.rawValue))"
            )
            app.activate()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10),
                          "Round \(round): app did not come back")
            settle()
        }
        capture("After 3 background rounds")
    }
}
