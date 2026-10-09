package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Android：真正的 IO 池，与迁移前行为完全一致。 */
internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO
