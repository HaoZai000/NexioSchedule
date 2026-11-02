# sqlite3（鸿蒙 arm64 静态库）

鸿蒙 NDK 的 sysroot 里**没有** sqlite3（144 个库里没有），而 SQLDelight 的
`native-driver` 走 cinterop 直接调 `sqlite3_*`，所以必须自带一份。

| 项 | 值 |
|---|---|
| 版本 | SQLite 3.49.1（amalgamation，官方 `sqlite-amalgamation-3490100.zip`） |
| 源码 | `sqlite3.c` / `sqlite3.h`（官方单文件发布） |
| 产物 | `lib/arm64-v8a/libsqlite3.a` |

## 重新编译

```bash
SDK=/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/native
cd <sqlite-amalgamation 目录>
$SDK/llvm/bin/clang --target=aarch64-linux-ohos --sysroot=$SDK/sysroot -O2 \
  -c sqlite3.c -o sqlite3.o \
  -DSQLITE_THREADSAFE=1 -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_RTREE -DSQLITE_ENABLE_JSON1 \
  -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_ENABLE_MATH_FUNCTIONS -DSQLITE_OMIT_LOAD_EXTENSION \
  -DSQLITE_DEFAULT_MEMSTATUS=0 -DSQLITE_TEMP_STORE=2 -DHAVE_USLEEP=1
$SDK/llvm/bin/llvm-ar rcs libsqlite3.a sqlite3.o
cp libsqlite3.a <repo>/ohos/third_party/sqlite/lib/arm64-v8a/
```

编译期开关与上游一致：FTS5 / RTREE / JSON / 列元数据全开，禁掉扩展加载。
