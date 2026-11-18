package app.paralleluniverse.transfer

/** 8080부터 차례로 열어 보고 처음 성공한 서버를 돌려준다. */
object PortPicker {
    val DEFAULT_PORTS: IntRange = 8080..8090

    /** [bind]가 null이 아닌 값을 돌려준 첫 포트의 결과. 모두 실패하면 null. */
    fun <T : Any> firstBound(ports: IntRange = DEFAULT_PORTS, bind: (Int) -> T?): T? {
        for (port in ports) {
            bind(port)?.let { return it }
        }
        return null
    }
}
