package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.ui.theme.ExpressiveFeedbackEngine

/**
 * Native system window overlay barrier (TYPE_APPLICATION_OVERLAY / TYPE_ACCESSIBILITY_OVERLAY).
 * Guarantees instant full-screen blocking over YouTube or unauthorized apps even when OEMs
 * (such as Xiaomi HyperOS, MIUI, OneUI, or Android 14+ BAL) suppress background startActivity().
 */
object LockWindowOverlayManager {

    private const val TAG = "LockWindowOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeOverlayView: View? = null

    @Volatile
    private var activeWindowManager: WindowManager? = null

    @Volatile
    private var currentBlockedPackage: String? = null

    fun isShowing(): Boolean = activeOverlayView != null

    fun isShowingFor(packageName: String): Boolean =
        activeOverlayView != null && currentBlockedPackage == packageName

    fun showBarrier(
        context: Context,
        decision: InterceptDecision,
        isFromAccessibilityService: Boolean
    ) {
        mainHandler.post {
            try {
                if (MainActivity.isInForeground) {
                    // MainActivity is already visible and displaying the Compose FrictionShieldOverlay
                    return@post
                }

                if (activeOverlayView != null && currentBlockedPackage == decision.packageName) {
                    return@post
                }

                dismissInternal()

                val canDrawAppOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }

                val windowType = when {
                    canDrawAppOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    isFromAccessibilityService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 ->
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    !canDrawAppOverlay -> return@post
                    else -> {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                }

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@post

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                val view = buildExpressiveBarrierView(context, decision)
                wm.addView(view, layoutParams)
                activeOverlayView = view
                activeWindowManager = wm
                currentBlockedPackage = decision.packageName
                ExpressiveFeedbackEngine.playInterceptionRejected(context)
                Log.i(TAG, "Displayed system overlay barrier for ${decision.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display system overlay barrier", e)
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            dismissInternal()
        }
    }

    private fun dismissInternal() {
        val view = activeOverlayView ?: return
        val wm = activeWindowManager
        try {
            wm?.removeViewImmediate(view)
        } catch (_: Exception) {
            try {
                wm?.removeView(view)
            } catch (_: Exception) {
            }
        } finally {
            activeOverlayView = null
            activeWindowManager = null
            currentBlockedPackage = null
        }
    }

    private fun buildExpressiveBarrierView(
        context: Context,
        decision: InterceptDecision
    ): View {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#120F17"))
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(36).toFloat()
                setColor(Color.parseColor("#1E1C26"))
            }
            setPadding(dp(28), dp(32), dp(28), dp(28))
        }

        val badgeText = if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "FOCUS LOCK ACTIVE"
        } else {
            "DAILY LIMIT REACHED"
        }

        val badge = TextView(context).apply {
            text = badgeText
            setTextColor(Color.parseColor("#FFDAD6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(Color.parseColor("#5C1919"))
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        card.addView(
            badge,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20)
            }
        )

        val pinnedName = ChapterLockEnforcer.getPinnedAppName().ifBlank { "your pinned app" }
        val titleStr = if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "${decision.appName} is locked during Focus"
        } else {
            "${decision.appName} time limit exhausted"
        }

        val titleView = TextView(context).apply {
            text = titleStr
            setTextColor(Color.parseColor("#F5EFF7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )

        val subtitleStr = if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "You are currently locked into $pinnedName (${ChapterLockEnforcer.getFocusRemainingSeconds() / 60 + 1}m remaining). Switching to other apps is blocked."
        } else {
            "You have reached your daily screen time target for ${decision.appName}. Solve the hard cognitive puzzle only if you need a 10-minute override."
        }

        val subtitleView = TextView(context).apply {
            text = subtitleStr
            setTextColor(Color.parseColor("#CAC4D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        card.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(28)
            }
        )

        // Primary Action Button: Return to Pinned App (or Go Home)
        val primaryLabel = if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "Return to $pinnedName"
        } else {
            "Keep ${decision.appName} Locked"
        }

        val primaryBtn = TextView(context).apply {
            text = primaryLabel
            setTextColor(Color.parseColor("#22154A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(Color.parseColor("#CFBDFE"))
            }
            setPadding(dp(24), dp(18), dp(24), dp(18))
            setOnClickListener {
                dismissInternal()
                val pinnedPkg = ChapterLockEnforcer.getPinnedAppPackage()
                if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH && !pinnedPkg.isNullOrBlank()) {
                    val launchPinned = context.packageManager.getLaunchIntentForPackage(pinnedPkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchPinned != null) {
                        try {
                            context.startActivity(launchPinned)
                            return@setOnClickListener
                        } catch (_: Exception) {
                        }
                    }
                }
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(homeIntent)
                } catch (_: Exception) {
                }
            }
        }
        card.addView(
            primaryBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )

        // Secondary Action Button: Open Hard Puzzle in LockIN
        val puzzleBtn = TextView(context).apply {
            text = if (decision.reason == InterceptReason.FOCUS_LOCK_BREACH) {
                "Quit Focus Early • Hard Puzzle"
            } else {
                "Unlock 10m Pass • Hard Puzzle"
            }
            setTextColor(Color.parseColor("#E6E0E9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(Color.parseColor("#332F3D"))
            }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setOnClickListener {
                val puzzleIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE, decision.packageName)
                    putExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON, decision.reason.name)
                }
                try {
                    context.startActivity(puzzleIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open puzzle activity from overlay click", e)
                }
                dismissInternal()
            }
        }
        card.addView(
            puzzleBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val cardParams = FrameLayout.LayoutParams(
            dp(460).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(36)),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(card, cardParams)
        return root
    }
}
