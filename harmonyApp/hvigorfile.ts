// @ts-nocheck
// 上面这行不要删：@ohos/hvigor-ohos-plugin 不是 npm 依赖，是 hvigor 运行时注入的构建插件
import { appTasks } from '@ohos/hvigor-ohos-plugin';

export default {
  system: appTasks, /* Built-in plugin of Hvigor. It cannot be modified. */
  plugins: []       /* Custom plugin to extend the functionality of Hvigor. */
}
