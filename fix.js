const fs = require('fs');
const p = 'C:\\Users\\43908\\Desktop\\KeCB\\app\\src\\main\\java\\com\\haooz\\chedule\\ui\\screens\\CourseEditScreen.kt';
let c = fs.readFileSync(p, 'utf8');

// 1. 添加编辑状态变量
if (!c.includes('showEditCourseSheet')) {
  c = c.replace(
    '    var showAddCourseSheet by remember { mutableStateOf(false) }',
    '    var showAddCourseSheet by remember { mutableStateOf(false) }\n    var showEditCourseSheet by remember { mutableStateOf(false) }\n    var editingGroup by remember { mutableStateOf<CourseGroup?>(null) }'
  );
}

// 2. 替换CourseGroupCard调用
c = c.replace(
  /CourseGroupCard\([\s\S]*?getOccupiedWeeks = \{ dow, ss, es ->[\s\S]*?getOccupiedWeeks\(dow, ss, es, group\.courses\.map \{ it\.id \}\)\s*\}\s*\)/,
  `CourseGroupCard(\n                                                    group = group,\n                                                    sectionTimes = sectionTimes,\n                                                    onEdit = { g -> editingGroup = g; showEditCourseSheet = true }\n                                                )`
);

// 3. 替换整个CourseGroupCard函数（从@Composable到下一个// ===之间）
const fnStart = c.indexOf('@Composable\nprivate fun CourseGroupCard(');
const endMarker = '\n// ===================== CourseEditScreen =====================';
const endIdx = c.indexOf(endMarker, fnStart);
if (fnStart === -1 || endIdx === -1) {
  console.log('ERROR: fnStart=' + fnStart + ' endIdx=' + endIdx);
  process.exit(1);
}

const newFn = `@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    sectionTimes: Map<Int, String>,
    onEdit: (CourseGroup) -> Unit,
) {
    val course = group.courses.first()
    val dayLabels = remember { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日") }
    val dayText = remember(course.dayOfWeek) { dayLabels.getOrNull(course.dayOfWeek - 1) ?: "" }
    val sectionText = remember(course.startSection, course.endSection) { "第\${course.startSection}-\${course.endSection}节" }
    val weekTitle = remember(course) { course.getWeekText() }

    Row(modifier = Modifier.fillMaxWidth().offset(x = (-16).dp)) { SmallTitle(text = weekTitle) }

    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 地点
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("地点", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Text(course.classroom.ifEmpty { "未设置" }, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (course.classroom.isEmpty()) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurface)
            }
            // 教师
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("教师", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Text(course.teacher.ifEmpty { "未设置" }, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (course.teacher.isEmpty()) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)))
            // 上课星期
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("上课星期", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Text(dayText, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)))
            // 上课节次
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("上课节次", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Text(sectionText, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)))
            // 上课周次
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("上课周次", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Text(weekTitle, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)))
            // 编辑按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onEdit(group) }, modifier = Modifier.padding(end = 12.dp, bottom = 8.dp)) {
                    Icon(MiuixIcons.Edit, "编辑", modifier = Modifier.size(22.dp), tint = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }

    if (group.courses.size > 1) {
        Text("包含 \${group.courses.size} 个相同配置的课程", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(start = 32.dp, top = 4.dp))
    }
}

`;
c = c.substring(0, fnStart) + newFn + c.substring(endIdx);

// 4. 在添加课程弹窗后面加上编辑课程弹窗
const addSheet = `getOccupiedWeeks = { dow, ss, es, excludeIds ->
                                getOccupiedWeeks(dow, ss, es, excludeIds)
                            }
                        )`;
const lastIdx = c.lastIndexOf(addSheet);
if (lastIdx !== -1) {
  c = c.substring(0, lastIdx + addSheet.length) + `

                        // 编辑课程底部弹窗
                        AddEditCourseBottomSheet(
                            show = showEditCourseSheet,
                            courses = editingGroup?.courses ?: emptyList(),
                            backdrop = backdrop,
                            liquidGlassBackdrop = if (isLiquidGlass) liquidGlassBackdrop else null,
                            fullscreen = true,
                            onDismissRequest = { showEditCourseSheet = false },
                            onConfirm = { updatedCourse ->
                                onCourseUpdated(updatedCourse)
                                showEditCourseSheet = false
                            },
                            getOccupiedWeeks = { dow, ss, es, excludeIds ->
                                getOccupiedWeeks(dow, ss, es, excludeIds)
                            }
                        )` + c.substring(lastIdx + addSheet.length);
}

fs.writeFileSync(p, c, 'utf8');
console.log('Done. Lines:', c.split('\n').length);
