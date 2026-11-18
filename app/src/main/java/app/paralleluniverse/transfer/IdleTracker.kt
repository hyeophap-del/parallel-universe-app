package app.paralleluniverse.transfer

/**
 * 마지막 활동(로그인, 업로드·다운로드 바이트, 휴대폰에서 파일 고르기) 이후 [timeoutMs]가 지났는지 본다.
 * 브라우저의 목록 새로고침(폴링)은 활동으로 치지 않는다. 전송 중에는 바이트가 오갈 때마다 [touch]된다.
 */
class IdleTracker(
    private val timeoutMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var last = clock()

    fun touch() {
        last = clock()
    }

    fun isIdle(): Boolean = clock() - last >= timeoutMs
}
