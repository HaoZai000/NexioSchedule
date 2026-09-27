package com.haooz.chedule.data

/**
 * 课表文件夹：把多个课表归为一组，便于切换页分类。
 *
 * 一个课表最多属于一个文件夹（其余课表位于根目录「全部课表」下）。
 * 文件夹只保存课表名引用，不改动 getScheduleNames() 的全局顺序。
 */
data class ScheduleFolder(
    val id: String,
    val name: String,
    val schedules: List<String> = emptyList()
)
