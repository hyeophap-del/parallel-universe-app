package app.paralleluniverse.profile

/** 개인 공간 → 평행 공간 명령. 한 줄 텍스트로 주고받는다: `PU1<TAB>ACTION<TAB>ARG` */
data class BridgeCommand(val action: Action, val arg: String = "") {

    enum class Action { CLONE, UNINSTALL, RENAME, DESTROY }

    companion object {
        private const val MAGIC = "PU1"
        private const val SEP = '\t'
        const val REPLY_OK = "OK"
        const val REPLY_FAILED = "FAILED"

        fun clone(packageName: String) = BridgeCommand(Action.CLONE, packageName)
        fun uninstall(packageName: String) = BridgeCommand(Action.UNINSTALL, packageName)
        fun rename(name: String) = BridgeCommand(Action.RENAME, name)
        fun destroy() = BridgeCommand(Action.DESTROY)

        fun encode(command: BridgeCommand): String =
            listOf(MAGIC, command.action.name, sanitize(command.arg)).joinToString(SEP.toString())

        /** 우리 형식이 아니면 null. 다른 프로그램이 같은 포트를 쓰는 경우를 걸러 낸다. */
        fun decode(line: String): BridgeCommand? {
            val parts = line.split(SEP, limit = 3)
            if (parts.size != 3 || parts[0] != MAGIC) return null
            val action = Action.entries.firstOrNull { it.name == parts[1] } ?: return null
            if (action != Action.DESTROY && parts[2].isBlank()) return null
            return BridgeCommand(action, parts[2])
        }

        private fun sanitize(arg: String) = arg.replace(SEP, ' ').replace('\n', ' ').replace('\r', ' ')
    }
}
