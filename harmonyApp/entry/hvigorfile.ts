// @ts-nocheck —— 同根目录 hvigorfile.ts：解析不到 @ohos/hvigor-ohos-plugin 是正常现象，
// 它由 hvigor 在构建时提供；这行注释只用来关掉 IDE 的误报。
import { hapTasks } from '@ohos/hvigor-ohos-plugin';

export default {
  system: hapTasks, /* Built-in plugin of Hvigor. It cannot be modified. */
  plugins: []       /* Custom plugin to extend the functionality of Hvigor. */
}
