/**
 * 系统备份 / 迁移扩展。
 *
 * 课表数据全在本机的 nexio.db 里，用户换机时系统级备份是最后一道保险，
 * 所以这个 Ability 必须声明（module.json5 的 extensionAbilities + backup_config.json）。
 * 真正的数据打包由系统做，这里只留扩展点与日志；等备份链路接上，
 * 在 onBackup / onRestore 里挂 AppStorage 的快照钩子。
 */

import { hilog } from '@kit.PerformanceAnalysisKit';
import { BackupExtensionAbility, BundleVersion } from '@kit.CoreFileKit';

const DOMAIN = 0x0000;
const TAG = 'nexio-backup';

export default class EntryBackupAbility extends BackupExtensionAbility {
  async onBackup(): Promise<void> {
    hilog.info(DOMAIN, TAG, 'onBackup');
    await Promise.resolve();
  }

  async onRestore(bundleVersion: BundleVersion): Promise<void> {
    hilog.info(DOMAIN, TAG, 'onRestore ok %{public}s', JSON.stringify(bundleVersion));
    await Promise.resolve();
  }
}
