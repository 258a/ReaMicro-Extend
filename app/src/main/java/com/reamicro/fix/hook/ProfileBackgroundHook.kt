package com.reamicro.fix.hook

import android.app.Activity
import com.reamicro.fix.settings.ModuleSettingsSnapshot
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Stage 1: take over the Profile screen toolbar (个人中心顶栏) and paint a solid
 * background strip behind it. The gear icon is not redrawn yet; instead the whole
 * strip stays clickable and reuses the host's [onSettings] callback so tapping it
 * still opens settings. The background image variant is a later stage.
 */
class ProfileBackgroundHook(
    private val classLoader: ClassLoader,
    private val activityProvider: () -> Activity?,
    private val settingsProvider: () -> ModuleSettingsSnapshot = { ModuleSettingsSnapshot() },
) {
    private val methodCache = HashMap<String, Method>()

    fun install() {
        runCatching {
            val target = method(PROFILE_TOOLBAR_CLASS, PROFILE_TOOLBAR_METHOD, 3)
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!settingsProvider().canShowProfileBackground) return
                        val onSettings = param.args?.getOrNull(0) ?: return
                        val composer = param.args?.getOrNull(1) ?: return
                        runCatching {
                            renderProfileToolbar(onSettings, composer)
                            param.result = null
                        }.onFailure {
                            XposedBridge.log(
                                "$LOG_PREFIX render failed, falling back to original: " +
                                    it.stackTraceToString(),
                            )
                        }
                    }
                },
            )
            XposedBridge.log("$LOG_PREFIX ProfileToolbar hook installed")
        }.onFailure {
            XposedBridge.log("$LOG_PREFIX failed to hook ProfileToolbar: ${it.stackTraceToString()}")
        }
    }

    private fun renderProfileToolbar(onSettings: Any, composer: Any) {
        val background = backgroundModifier()
        val content = composableLambda(PROFILE_BG_CONTENT_KEY, FUNCTION3_CLASS) { args ->
            val innerComposer = args?.getOrNull(1) ?: return@composableLambda targetUnit()
            renderClickableArea(onSettings, innerComposer)
            targetUnit()
        }
        method(COLUMN_KT_CLASS, COLUMN_METHOD, 7).invoke(
            null,
            background,
            arrangementTop(),
            alignmentStart(),
            content,
            composer,
            0,
            0,
        )
    }

    private fun backgroundModifier(): Any {
        val filled = method(SIZE_KT_CLASS, FILL_MAX_WIDTH_DEFAULT_METHOD, 4)
            .invoke(null, modifierInstance(), 0f, 1, null)
        val sized = method(SIZE_KT_CLASS, HEIGHT_METHOD, 2)
            .invoke(null, filled, udp(TOOLBAR_HEIGHT_DP))
        return method(BACKGROUND_KT_CLASS, BACKGROUND_DEFAULT_METHOD, 5)
            .invoke(null, sized, colorFromArgb(BACKGROUND_ARGB), null, 2, null)
    }

    private fun renderClickableArea(onSettings: Any, composer: Any) {
        val base = method(SIZE_KT_CLASS, FILL_MAX_WIDTH_DEFAULT_METHOD, 4)
            .invoke(null, modifierInstance(), 0f, 1, null)
        val modifier = method(CLICKABLE_KT_CLASS, CLICKABLE_DEFAULT_METHOD, 9).invoke(
            null,
            base,
            null,
            null,
            false,
            null,
            null,
            functionProxy("ProfileBackgroundClick", FUNCTION0_CLASS) {
                runCatching { onSettings.method0("invoke") }
                targetUnit()
            },
            28,
            null,
        )
        val content = composableLambda(PROFILE_BG_AREA_KEY, FUNCTION3_CLASS) { args ->
            args?.getOrNull(1) ?: return@composableLambda targetUnit()
            targetUnit()
        }
        method(COLUMN_KT_CLASS, COLUMN_METHOD, 7).invoke(
            null,
            modifier,
            arrangementTop(),
            alignmentStart(),
            content,
            composer,
            0,
            0,
        )
    }

    private fun colorFromArgb(argb: Int): Long =
        cls(COLOR_KT_CLASS).declaredMethods.first {
            it.name == COLOR_METHOD &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }.apply { isAccessible = true }.invoke(null, argb) as Long

    private fun composableLambda(key: Int, functionClassName: String, block: (Array<Any?>?) -> Any?): Any =
        method(COMPOSABLE_LAMBDA_KT_CLASS, COMPOSABLE_LAMBDA_METHOD, 3).invoke(
            null,
            key,
            true,
            functionProxy("Composable$key", functionClassName, block),
        )

    private fun functionProxy(name: String, functionClassName: String, block: (Array<Any?>?) -> Any?): Any {
        val functionClass = cls(functionClassName)
        return Proxy.newProxyInstance(classLoader, arrayOf(functionClass)) { proxy, proxyMethod, args ->
            when (proxyMethod.name) {
                "invoke" -> runCatching {
                    block(args)
                }.onFailure {
                    XposedBridge.log("$LOG_PREFIX failed in $name callback: ${it.stackTraceToString()}")
                }.getOrElse { targetUnit() }
                "toString" -> "ReaMicro$name"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        }
    }

    private fun cls(className: String): Class<*> =
        XposedHelpers.findClass(className, classLoader)

    private fun method(className: String, methodName: String, parameterCount: Int): Method {
        val cacheKey = "$className#$methodName/$parameterCount"
        return synchronized(methodCache) {
            methodCache.getOrPut(cacheKey) {
                cls(className).declaredMethods.firstOrNull {
                    it.name == methodName && it.parameterTypes.size == parameterCount
                }?.apply { isAccessible = true }
                    ?: error("$className.$methodName/$parameterCount not found")
            }
        }
    }

    private fun staticObject(className: String, fieldName: String): Any {
        val clazz = cls(className)
        return runCatching {
            clazz.getDeclaredField(fieldName).apply { isAccessible = true }.get(null)
        }.recoverCatching {
            clazz.getField("Companion").apply { isAccessible = true }.get(null)
        }.recoverCatching {
            cls("$className\$Companion").getDeclaredField("\$\$INSTANCE")
                .apply { isAccessible = true }
                .get(null)
        }.getOrThrow()
    }

    private fun modifierInstance(): Any =
        staticObject(MODIFIER_CLASS, "INSTANCE")

    private fun arrangementTop(): Any =
        staticObject(ARRANGEMENT_CLASS, "INSTANCE").method0("getTop")

    private fun alignmentStart(): Any =
        staticObject(ALIGNMENT_CLASS, "INSTANCE").method0("getStart")

    private fun udp(value: Int): Float =
        cls(UNIT_EXT_KT_CLASS).declaredMethods.first {
            it.name == UDP_METHOD && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }.invoke(null, value) as Float

    private fun targetUnit(): Any? = runCatching {
        val unitClass = cls("kotlin.Unit")
        unitClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }.recoverCatching {
        cls("kotlin.Unit").getField("Companion").apply { isAccessible = true }.get(null)
    }.recoverCatching {
        cls("kotlin.Unit\$Companion").getDeclaredField("\$\$INSTANCE")
            .apply { isAccessible = true }
            .get(null)
    }.getOrNull()

    private fun Any.method0(name: String): Any =
        javaClass.methods.first {
            it.parameterTypes.isEmpty() && (it.name == name || it.name.startsWith("$name-"))
        }.invoke(this)

    private companion object {
        const val LOG_PREFIX = "ReaMicro LSP profile background"

        const val TOOLBAR_HEIGHT_DP = 48
        const val BACKGROUND_ARGB = 0x80000000.toInt()

        const val PROFILE_BG_CONTENT_KEY = 0x52_4D_42_47
        const val PROFILE_BG_AREA_KEY = 0x52_4D_42_41

        const val PROFILE_TOOLBAR_CLASS = "app.zhendong.reamicro.ui.profile.components.ProfileToolbarKt"
        const val PROFILE_TOOLBAR_METHOD = "ProfileToolbar"

        const val UNIT_EXT_KT_CLASS = "app.zhendong.reamicro.arch.extensions.UnitExtKt"
        const val UDP_METHOD = "getUdp"

        const val COMPOSABLE_LAMBDA_KT_CLASS = "androidx.compose.runtime.internal.ComposableLambdaKt"
        const val COMPOSABLE_LAMBDA_METHOD = "composableLambdaInstance"

        const val COLUMN_KT_CLASS = "androidx.compose.foundation.layout.ColumnKt"
        const val COLUMN_METHOD = "Column"
        const val SIZE_KT_CLASS = "androidx.compose.foundation.layout.SizeKt"
        const val FILL_MAX_WIDTH_DEFAULT_METHOD = "fillMaxWidth\$default"
        const val HEIGHT_METHOD = "height-3ABfNKs"
        const val BACKGROUND_KT_CLASS = "androidx.compose.foundation.BackgroundKt"
        const val BACKGROUND_DEFAULT_METHOD = "background-bw27NRU\$default"
        const val CLICKABLE_KT_CLASS = "androidx.compose.foundation.ClickableKt"
        const val CLICKABLE_DEFAULT_METHOD = "clickable-O2vRcR0\$default"
        const val COLOR_KT_CLASS = "androidx.compose.ui.graphics.ColorKt"
        const val COLOR_METHOD = "Color"

        const val MODIFIER_CLASS = "androidx.compose.ui.Modifier"
        const val ARRANGEMENT_CLASS = "androidx.compose.foundation.layout.Arrangement"
        const val ALIGNMENT_CLASS = "androidx.compose.ui.Alignment"

        const val FUNCTION0_CLASS = "kotlin.jvm.functions.Function0"
        const val FUNCTION3_CLASS = "kotlin.jvm.functions.Function3"
    }
}
