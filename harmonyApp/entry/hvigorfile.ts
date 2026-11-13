// @ts-nocheck —— 同根目录 hvigorfile.ts：解析不到 @ohos/hvigor-ohos-plugin 是正常现象，
// 它由 hvigor 在构建时提供；这行注释只用来关掉 IDE 的误报。
import { hapTasks, OhosPluginId } from '@ohos/hvigor-ohos-plugin';
import { HvigorNode, HvigorPlugin } from '@ohos/hvigor';
import { spawnSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * 打包前先把科特林侧的动态库链到最新。
 *
 * 那层是独立工程（仓库根下的 `ohos/`，Gradle + 科特林原生），hvigor 不认识它：
 * `cpp/CMakeLists.txt` 里 `libnexioohos.so` 是 `IMPORTED`，对 hvigor 就是个预编译文件。
 * 不跑这一步，包里打进的是**上一次**编出来的库 —— 源码改了而包里没变，一声不响。
 *
 * 构建模式跟着 hvigor 走：调试包链调试库，发布包链发布库（两者不能混用）。
 * Gradle 是增量的，没改动时一两秒返回。
 *
 * 输出走临时文件、只在失败时打尾部：科特林原生 release 编译会刷几百行
 * `[CAdapterGenerator] ...`，直接透传会把真正的报错埋掉。
 */
function nexioSharedLib(): HvigorPlugin {
  return {
    pluginId: 'nexio-shared-lib',
    apply(node: HvigorNode) {
      node.registerTask({
        name: 'linkNexioSharedLib',
        run() {
          const ctx: any = node.getContext(OhosPluginId.OHOS_HAP_PLUGIN);
          // 大小写不敏感：取到的值万一不是小写，静默选错变体就等于白干（还会打旧库）
          const rawMode: string = String(ctx?.getBuildMode?.() ?? '').toLowerCase();
          const isRelease: boolean = rawMode === 'release';
          const variant: string = isRelease ? 'Release' : 'Debug';
          const ohosDir: string = findOhosDir();
          const javaHome: string = resolveJavaHome();

          const logFile: string = path.join(os.tmpdir(), `nexio-link-${variant.toLowerCase()}.log`);
          const fd: number = fs.openSync(logFile, 'w');
          const started: number = Date.now();
          let status: number | null = null;
          try {
            const res = spawnSync('./gradlew', [`:ohosApp:link${variant}SharedOhosArm64`], {
              cwd: ohosDir,
              env: { ...process.env, JAVA_HOME: javaHome },
              stdio: ['ignore', fd, fd],
            });
            status = res.status;
          } finally {
            fs.closeSync(fd);
          }

          const seconds: number = Math.round((Date.now() - started) / 1000);
          if (status !== 0) {
            console.error(`[nexio] 链${isRelease ? '发布' : '调试'}版动态库失败，Gradle 输出尾部：`);
            const lines: string[] = fs.readFileSync(logFile, 'utf8').trimEnd().split('\n');
            console.error(lines.slice(-40).join('\n'));
            throw new Error(
              `链动态库失败。完整日志：${logFile}；` +
                `JAVA_HOME 不是 JDK21 时也会挂在这里。`,
            );
          }
          console.log(
            `[nexio] ${isRelease ? '发布' : '调试'}版动态库已就绪（${seconds} 秒，日志 ${logFile}）`,
          );
        },
        // 早于模块的 PreBuild：CMake 链接（BuildNativeWithNinja）时必须已经拿到新库
        postDependencies: ['default@PreBuild'],
      });
    },
  };
}

/**
 * 找可用的 JDK21。
 *
 * `~/jdk21` 下可能同时存在多个版本（含下载中断后缺 `lib/modules` 的坏安装），
 * 只认同时有 `bin/java` 与 `lib/modules` 的那一个；`NEXIO_JAVA_HOME` 优先。
 */
function resolveJavaHome(): string {
  if (process.env.NEXIO_JAVA_HOME) {
    return process.env.NEXIO_JAVA_HOME;
  }
  const base: string = path.join(process.env.HOME ?? os.homedir(), 'jdk21');
  try {
    const candidates: string[] = fs.readdirSync(base)
      .filter((d: string): boolean => d.endsWith('.jdk'))
      .sort()
      .reverse();
    for (const name of candidates) {
      const home: string = path.join(base, name, 'Contents', 'Home');
      if (
        fs.existsSync(path.join(home, 'bin', 'java')) &&
        fs.existsSync(path.join(home, 'lib', 'modules'))
      ) {
        return home;
      }
    }
  } catch (e) {
    // 目录不存在时走下面的兜底路径，让 Gradle 自己报错
  }
  return `${base}/jdk-21.0.2.jdk/Contents/Home`;
}

/** 从当前模块往上找 `ohos/gradlew`（DevEco 与命令行的当前目录不同，不能写死相对路径）。 */
function findOhosDir(): string {
  const roots: string[] = [__dirname, process.cwd()].filter((d: string): boolean => !!d);
  for (const root of roots) {
    let dir: string = root;
    for (let depth = 0; depth < 4; depth++) {
      const candidate: string = path.join(dir, 'ohos', 'gradlew');
      if (fs.existsSync(candidate)) {
        return path.dirname(candidate);
      }
      dir = path.dirname(dir);
    }
  }
  throw new Error('找不到 ohos/gradlew —— 科特林侧的工程目录不在预期位置');
}

export default {
  system: hapTasks, /* Built-in plugin of Hvigor. It cannot be modified. */
  plugins: [nexioSharedLib()] /* Custom plugin to extend the functionality of Hvigor. */
}
