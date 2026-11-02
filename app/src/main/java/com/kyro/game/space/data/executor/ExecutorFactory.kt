package com.kyro.game.space.data.executor

import com.kyro.game.space.data.model.ExecutionMethod

class ExecutorFactory(
    val shizukuExecutor: ShizukuCommandExecutor = ShizukuCommandExecutor(),
    val libAdbExecutor: LibAdbCommandExecutor = LibAdbCommandExecutor(),
    val rootExecutor: RootCommandExecutor = RootCommandExecutor()
) {

    fun getExecutor(method: ExecutionMethod): CommandExecutor {
        return when (method) {
            ExecutionMethod.SHIZUKU -> shizukuExecutor
            ExecutionMethod.MANUAL_ADB -> libAdbExecutor
            ExecutionMethod.ROOT -> rootExecutor
        }
    }
}
