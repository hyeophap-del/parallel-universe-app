package app.paralleluniverse.transfer

import java.io.IOException
import java.io.InputStream

/** PC에서 받은 파일을 저장하는 곳. 서버가 Android 타입에 묶이지 않도록 분리했다. */
interface TransferStorage {
    /** [length]바이트를 저장할 여유가 있는지 미리 확인한다. */
    fun hasSpaceFor(length: Long): Boolean

    /**
     * [input]에서 [length]바이트를 읽어 [name]으로 저장하고, 실제로 저장된 이름을 돌려준다
     * (같은 이름이 있으면 바뀔 수 있다). 실패하면 부분 파일을 지운다.
     * 공간이 부족하면 [StorageFullException]을 던진다.
     */
    @Throws(IOException::class)
    fun save(name: String, length: Long, input: InputStream): String
}

/** 휴대폰에서 PC로 보내려고 고른 파일. [size]를 모르면 -1. */
data class SharedFileInfo(val id: String, val name: String, val size: Long)

/** 휴대폰에서 고른 파일 목록과 내용. */
interface SharedFiles {
    fun list(): List<SharedFileInfo>

    /** 파일을 열 수 없으면(권한이 사라졌거나 삭제됨) null. */
    fun open(id: String): InputStream?
}
