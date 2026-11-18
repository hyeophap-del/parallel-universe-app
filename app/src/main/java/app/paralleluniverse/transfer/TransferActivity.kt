package app.paralleluniverse.transfer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.paralleluniverse.ui.ParallelTheme

/**
 * 평행파일: 같은 Wi-Fi의 PC 브라우저와 파일을 주고받는 화면.
 * `Intent(context, TransferActivity::class.java)`로 연다.
 *
 * 화면이 보이는 동안(onStart~onStop)에만 서버를 연다. 화면 회전이나 시스템 파일 선택 화면 때문에
 * onStop이 오는 경우에는 서버를 유지한다. 전송이 화면 꺼짐으로 끊기지 않도록 화면을 켜 둔다.
 */
class TransferActivity : ComponentActivity() {
    private val viewModel: TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            ParallelTheme {
                TransferScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.pickerOpen = false
        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !viewModel.pickerOpen) viewModel.stop()
    }
}
