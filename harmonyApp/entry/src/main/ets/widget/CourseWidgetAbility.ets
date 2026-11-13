import { FormExtensionAbility, formBindingData, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { addFormId, buildBinding, readWidgetData, removeFormId } from './WidgetStore';

/**
 * 课程表桌面卡片的提供方。
 *
 * 数据全部来自 Kotlin 写的 `widget_snapshot.json`（见 `OhosWidgetSnapshot`），
 * 这里不查数据库、不算周次 —— 只负责把快照塞进 `formBindingData`。
 *
 * 刷新时机：
 * - 加卡 / 系统定时（`form_config.json` 的 `updateDuration`，最快 30 分钟）→ onUpdateForm；
 * - 课表或提醒设置变更 → Kotlin 触发 `refreshWidgets`（见 `WidgetStore.refreshCourseWidgets`）。
 */
export default class CourseWidgetAbility extends FormExtensionAbility {

  onAddForm(want: Want): formBindingData.FormBindingData {
    const formId = this.extractFormId(want);
    if (formId !== '') {
      // 不 await：onAddForm 必须同步返回数据
      addFormId(this.context, formId);
    }
    return buildBinding(readWidgetData(this.context));
  }

  onUpdateForm(formId: string): void {
    formProvider.updateForm(formId, buildBinding(readWidgetData(this.context)))
      .catch((e: BusinessError) => {
        hilog.warn(0x0000, 'nexio', 'widget updateForm failed: %{public}s', JSON.stringify(e));
      });
  }

  onRemoveForm(formId: string): void {
    removeFormId(this.context, formId);
  }

  private extractFormId(want: Want): string {
    const params = want.parameters;
    if (params === undefined) {
      return '';
    }
    const value = params['ohos.extra.param.key.form_identity'];
    return typeof value === 'string' ? value as string : '';
  }
}
