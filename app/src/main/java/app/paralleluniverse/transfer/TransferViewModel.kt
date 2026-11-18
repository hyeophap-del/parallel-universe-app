package app.paralleluniverse.transfer

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 전송 화면 상태. */
sealed interface TransferState {
    /** 아직 시작하지 않았거나 화면을 떠나 멈춘 상태. */
    data object Stopped : TransferState
    data class Running(val url: String, val pin: String) : TransferState
    data object NoNetwork : TransferState
    data object NoPort : TransferState
    data object TimedOut : TransferState
}

/** 휴대폰에서 PC로 보내려고 고른 파일. [size]를 모르면 -1. */
data class SharedItem(val id: String, val uri: Uri, val name: String, val size: Long)

/** PC에서 받은 파일. */
data class ReceivedItem(val name: String, val size: Long)

/**
 * 전송 서버의 수명을 관리한다. 화면 회전에도 서버가 유지되도록 ViewModel에 둔다.
 * 서버 시작은 짧은 작업(포트 bind)이라 메인 스레드에서 하고, 멈추기는 소켓을 닫으므로 별도 스레드에서 한다.
 */
class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<TransferState>(TransferState.Stopped)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val _shared = MutableStateFlow<List<SharedItem>>(emptyList())
    val shared: StateFlow<List<SharedItem>> = _shared.asStateFlow()

    private val _received = MutableStateFlow<List<ReceivedItem>>(emptyList())
    val received: StateFlow<List<ReceivedItem>> = _received.asStateFlow()

    private val storage = DownloadStore(application)
    val saveLocation: String get() = storage.locationLabel

    /** 시스템 파일 선택 화면이 열려 있는 동안에는 onStop이 와도 서버를 멈추지 않는다. */
    @Volatile
    var pickerOpen: Boolean = false

    private var server: TransferServer? = null
    private var idle: IdleTracker? = null
    private var idleJob: Job? = null
    private val nextId = AtomicInteger(0)

    private val page: ByteArray by lazy {
        application.assets.open(PAGE_ASSET).use { it.readBytes() }
    }

    private val sharedFiles = object : SharedFiles {
        override fun list(): List<SharedFileInfo> =
            _shared.value.map { SharedFileInfo(it.id, it.name, it.size) }

        override fun open(id: String): InputStream? {
            val item = _shared.value.firstOrNull { it.id == id } ?: return null
            return try {
                getApplication<Application>().contentResolver.openInputStream(item.uri)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 서버를 연다. 이미 열려 있으면 아무것도 하지 않는다. */
    fun start() {
        if (server != null) return
        val host = NetworkAddress.find(getApplication<Application>())
        if (host == null) {
            _state.value = TransferState.NoNetwork
            return
        }
        val auth = PinAuthenticator()
        val tracker = IdleTracker(IDLE_TIMEOUT_MS)
        val started = PortPicker.firstBound { port ->
            val candidate = TransferServer(
                host = host,
                port = port,
                page = page,
                auth = auth,
                idle = tracker,
                storage = storage,
                shared = sharedFiles,
                onReceived = ::onReceived,
            )
            try {
                candidate.start(TransferServer.SOCKET_READ_TIMEOUT_MS, true)
                candidate
            } catch (e: IOException) {
                null
            }
        }
        if (started == null) {
            _state.value = TransferState.NoPort
            return
        }
        auth.onPinChanged = { pin ->
            _state.update { current -> if (current is TransferState.Running) current.copy(pin = pin) else current }
        }
        server = started
        idle = tracker
        _state.value = TransferState.Running(started.url, auth.pin)
        watchIdle(tracker)
    }

    /** 서버를 닫는다. 세션과 PIN은 사라지고, 고른 파일 목록은 화면이 살아 있는 동안 유지된다. */
    fun stop() {
        idleJob?.cancel()
        idleJob = null
        idle = null
        val running = server ?: return
        server = null
        thread(name = "transfer-stop", isDaemon = true) { running.stop() }
        if (_state.value is TransferState.Running) _state.value = TransferState.Stopped
    }

    fun addShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        idle?.touch()
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { uris.map { describe(it) } }
            _shared.update { current -> current + items.filter { item -> current.none { it.uri == item.uri } } }
        }
    }

    fun removeShared(id: String) {
        _shared.update { list -> list.filterNot { it.id == id } }
    }

    private fun describe(uri: Uri): SharedItem {
        var name: String? = null
        var size = -1L
        runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val display = name ?: uri.lastPathSegment ?: FileNames.FALLBACK
        return SharedItem(nextId.incrementAndGet().toString(), uri, FileNames.sanitize(display), size)
    }

    private fun onReceived(name: String, size: Long) {
        _received.update { listOf(ReceivedItem(name, size)) + it }
    }

    private fun watchIdle(tracker: IdleTracker) {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            while (true) {
                delay(IDLE_CHECK_MS)
                if (tracker.isIdle()) {
                    stop()
                    _state.value = TransferState.TimedOut
                    break
                }
            }
        }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val PAGE_ASSET = "transfer/index.html"
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        const val IDLE_CHECK_MS = 15_000L
    }
}
