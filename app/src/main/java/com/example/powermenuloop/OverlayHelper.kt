package com.example.powermenuloop

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

object OverlayHelper {
    private const val TAG = "OverlayHelper"
    private val uiHandler = Handler(Looper.getMainLooper())

    fun showWarning(context: Context, message: String) {
        uiHandler.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                // ルートレイアウト（全画面の背景用）
                val rootLayout = FrameLayout(context).apply {
                    // 背景色：黒の50%透明 (#80000000)
                    setBackgroundColor(Color.parseColor("#80000000"))
                    alpha = 0f // フェードインのために最初は透明にする
                }

                // メッセージテキスト
                val textView = TextView(context).apply {
                    text = message // ここで引数のmessageをセット
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }

                // テキストを中央に配置
                val textParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                rootLayout.addView(textView, textParams)

                // WindowManagerのパラメータ（全画面設定）
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    // FLAG_LAYOUT_IN_SCREEN / FLAG_LAYOUT_NO_LIMITS でステータスバー領域まで広げる
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )

                wm.addView(rootLayout, params)

                // フェードインアニメーション (0.5秒)
                rootLayout.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()

                // 5秒後にフェードアウトして削除
                uiHandler.postDelayed({
                    try {
                        rootLayout.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .withEndAction {
                                try {
                                    wm.removeView(rootLayout)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error removing overlay view", e)
                                }
                            }
                            .start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting fade out", e)
                    }
                }, 5000)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay view", e)
            }
        }
    }
}
