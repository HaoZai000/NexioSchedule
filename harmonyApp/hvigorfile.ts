// @ts-nocheck
// 上面这行不要删：@ohos/hvigor-ohos-plugin 不是 npm 依赖，是 hvigor 运行时注入的构建插件，
// IDE 的 ArkTS/TS 检查解析不到它的类型声明，必然报 "Cannot find module"。官方脚手架
// 模板里本来就有这行，手写工程漏掉就是满屏红线、但构建照常成功。
import { appTasks } from '@ohos/hvigor-ohos-plugin';

export default {
  system: appTasks, /* Built-in plugin of Hvigor. It cannot be modified. */
  plugins: []       /* Custom plugin to extend the functionality of Hvigor. */
}
