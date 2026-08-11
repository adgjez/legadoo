// Minimal stubs for CI compilation (no Android SDK / no coroutines lib / no kotlin-test lib)
// Goal: provide enough type resolution that kotlinc can check all of VideoAssembly.kt +
// ArcReelPipelineDryRunTest.kt + the io.legado.app.video.* sources for type/syntax errors.

// ==================================================
// android.*
// ==================================================
package android.content {
    abstract class Context
    open class Intent constructor(
        packageName: String? = null,
        cls: Class<*>? = null
    ) {
        constructor() : this(null, null)
        constructor(action: String) : this(action, null)
    }
    interface SharedPreferences {
        interface Editor {
            fun putString(key: String?, value: String?): Editor
            fun putInt(key: String?, value: Int): Editor
            fun putLong(key: String?, value: Long): Editor
            fun putBoolean(key: String?, value: Boolean): Editor
            fun remove(key: String?): Editor
            fun clear(): Editor
            fun apply()
            fun commit(): Boolean
        }
        fun getString(key: String?, defValue: String?): String?
        fun getInt(key: String?, defValue: Int): Int
        fun getLong(key: String?, defValue: Long): Long
        fun getBoolean(key: String?, defValue: Boolean): Boolean
        fun contains(key: String?): Boolean
        fun edit(): Editor
    }
    open class ContentResolver constructor(context: Context? = null)
    class ClipboardManager constructor()
    open class Uri private constructor() {
        companion object {
            fun parse(uriString: String): Uri = Uri()
        }
    }
}
package android.database {
    interface Cursor {
        fun moveToFirst(): Boolean
        fun moveToNext(): Boolean
        fun isAfterLast(): Boolean
        fun getColumnIndexOrThrow(columnName: String): Int
        fun getString(columnIndex: Int): String?
        fun getInt(columnIndex: Int): Int
        fun getLong(columnIndex: Int): Long
        fun getFloat(columnIndex: Int): Float
        fun close()
    }
}
package android.graphics {
    open class Bitmap private constructor() {
        val width: Int get() = 0
        val height: Int get() = 0
        companion object {
            enum class Config { ALPHA_8, RGB_565, ARGB_8888, RGBA_F16 }
        }
    }
    open class ColorFilter()
    open class PorterDuffColorFilter(color: Int, mode: PorterDuffMode) : ColorFilter()
    enum class PorterDuffMode { SRC_OVER, DST_OVER, MULTIPLY, SCREEN, ADD, CLEAR }
}
package android.net {
    open class Uri private constructor() {
        companion object {
            fun parse(uriString: String): Uri = Uri()
            val EMPTY: Uri = Uri()
        }
    }
}
package android.os {
    open class Bundle() {
        fun putString(key: String?, value: String?)
        fun getString(key: String?): String?
    }
    open class ParcelFileDescriptor constructor()
    class StrictMode private constructor() {
        companion object {
            class ThreadPolicy private constructor() {
                class Builder() {
                    fun detectAll(): Builder = this
                    fun penaltyLog(): Builder = this
                    fun build(): ThreadPolicy = ThreadPolicy()
                }
            }
            class VmPolicy private constructor() {
                class Builder() {
                    fun detectLeakedSqlLiteObjects(): VmPolicy.Builder = this
                    fun detectActivityLeaks(): VmPolicy.Builder = this
                    fun penaltyLog(): VmPolicy.Builder = this
                    fun build(): VmPolicy = VmPolicy()
                }
            }
            fun setThreadPolicy(policy: ThreadPolicy) {}
            fun setVmPolicy(policy: VmPolicy) {}
        }
    }
}
package android.util {
    object Log {
        @JvmStatic fun v(tag: String?, msg: String): Int = 0
        @JvmStatic fun v(tag: String?, msg: String, tr: Throwable?): Int = 0
        @JvmStatic fun d(tag: String?, msg: String): Int = 0
        @JvmStatic fun i(tag: String?, msg: String): Int = 0
        @JvmStatic fun w(tag: String?, msg: String): Int = 0
        @JvmStatic fun e(tag: String?, msg: String): Int = 0
        @JvmStatic fun e(tag: String?, msg: String, tr: Throwable?): Int = 0
        @JvmStatic fun wtf(tag: String?, msg: String): Int = 0
    }
    open class SizeF(val width: Float, val height: Float)
}
package android.view {
    open class View(context: android.content.Context?)
    class ViewGroup(context: android.content.Context?) : View(context)
}
package android.widget {
    import android.view.ViewGroup
    open class TextView(context: android.content.Context?) : android.view.View(context)
    open class ImageView(context: android.content.Context?) : android.view.View(context)
}

// ==================================================
// androidx.annotation / Room
// ==================================================
package androidx.annotation {
    annotation class Keep
    annotation class StringDef(val vararg value: String)
    annotation class IntDef(val vararg value: Int)
    annotation class ColorInt
    annotation class Px
    annotation class DrawableRes
    annotation class StringRes
    annotation class RequiresPermission(val value: String = "")
}
package androidx.room {
    annotation class Dao
    annotation class Database(val entities: Array<kotlin.reflect.KClass<*>>, val version: Int)
    annotation class Entity(
        val tableName: String = "",
        val primaryKeys: Array<String> = [],
        val indices: Array<Index> = []
    )
    annotation class ColumnInfo(val name: String = "")
    annotation class PrimaryKey(val autoGenerate: Boolean = false)
    annotation class Index(val value: Array<String> = [], val unique: Boolean = false)
    annotation class Query(val value: String)
    annotation class Insert
    annotation class Update
    annotation class Delete
    open class RoomSQLiteQuery private constructor()
    interface RoomDatabase
}
package androidx.sqlite.db {
    interface SupportSQLiteDatabase {
        fun execSQL(sql: String)
        fun query(sql: String): android.database.Cursor
    }
}

// ==================================================
// kotlinx-coroutines core + test minimal stubs
// ==================================================
package kotlinx.coroutines {
    object Dispatchers {
        val Main: CoroutineDispatcher get() = CoroutineDispatcher()
        val Default: CoroutineDispatcher get() = CoroutineDispatcher()
        val IO: CoroutineDispatcher get() = CoroutineDispatcher()
        val Unconfined: CoroutineDispatcher get() = CoroutineDispatcher()
    }
    open class CoroutineDispatcher()
    annotation class ExperimentalCoroutinesApi
    annotation class DelicateCoroutinesApi
    suspend inline fun <T> withContext(
        context: CoroutineDispatcher,
        crossinline block: suspend () -> T
    ): T = throw NotImplementedError("CI stub only")

    interface Job {
        fun cancel()
        val isActive: Boolean get() = true
    }
    interface Deferred<out T> : Job {
        suspend fun await(): T
    }
    open class CancellationException(message: String? = null) : Exception(message)
    class TimeoutCancellationException(message: String) : CancellationException(message)

    object GlobalScope {
        fun launch(context: CoroutineDispatcher = Dispatchers.Default, block: suspend () -> Unit): Job =
            throw NotImplementedError("CI stub")
    }
    suspend fun <T> coroutineScope(block: suspend () -> T): T = throw NotImplementedError("CI stub")
    suspend fun delay(timeMillis: Long) {}
    suspend fun withTimeout(timeMillis: Long, block: suspend () -> Unit) {}
}
package kotlinx.coroutines.flow {
    interface Flow<out T>
    fun <T> flow(block: suspend () -> T): Flow<T> = throw NotImplementedError("CI stub")
}
package kotlinx.coroutines.test {
    fun runTest(block: suspend () -> Unit): TestResult = runTest("stub", block)
    @Suppress("UNUSED_PARAMETER")
    fun runTest(name: String?, block: suspend () -> Unit): TestResult = TestResult()
    class TestResult internal constructor()
}

// ==================================================
// kotlin.test (kotlin-test stubs)
// ==================================================
package kotlin.test {
    annotation class BeforeTest
    annotation class AfterTest
    annotation class Test

    fun assertTrue(message: String? = null, block: () -> Boolean) {
        if (!block()) throw AssertionError(message ?: "assertTrue failed")
    }
    fun assertTrue(actual: Boolean, message: String? = null) {
        if (!actual) throw AssertionError(message ?: "assertTrue failed")
    }
    fun assertFalse(actual: Boolean, message: String? = null) {
        if (actual) throw AssertionError(message ?: "assertFalse failed")
    }
    fun <T> assertEquals(expected: T, actual: T, message: String? = null) {
        if (expected != actual) {
            throw AssertionError((message?.let { "$it; " } ?: "") + "expected=$expected actual=$actual")
        }
    }
    fun assertEquals(expected: Long, actual: Long, message: String? = null) {
        if (expected != actual) {
            throw AssertionError((message?.let { "$it; " } ?: "") + "expected=$expected actual=$actual")
        }
    }
    fun assertEquals(expected: Int, actual: Int, message: String? = null) {
        if (expected != actual) {
            throw AssertionError((message?.let { "$it; " } ?: "") + "expected=$expected actual=$actual")
        }
    }
    fun assertEquals(expected: Double, actual: Double, tolerance: Double, message: String? = null) {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            throw AssertionError((message?.let { "$it; " } ?: "") + "expected=$expected actual=$actual tol=$tolerance")
        }
    }
    fun <T> assertNull(actual: T?, message: String? = null) {
        if (actual != null) throw AssertionError(message ?: "expected null actual=$actual")
    }
    fun <T : Any> assertNotNull(actual: T?, message: String? = null): T =
        actual ?: throw AssertionError(message ?: "expected non-null")
    inline fun <reified T : Any> assertIs(value: Any?, message: String? = null): T =
        value as? T ?: throw AssertionError(
            (message?.let { "$it; " } ?: "") + "expected ${T::class.java}, got ${value?.javaClass}"
        )
    fun fail(message: String? = null): Nothing = throw AssertionError(message ?: "fail")
}

// ==================================================
// OkHttp / Retrofit / JavaNet + IO (used broadly, skip compile errors if any)
// ==================================================
package okhttp3 {
    class OkHttpClient private constructor() {
        class Builder() {
            fun build(): OkHttpClient = OkHttpClient()
        }
    }
    class Request private constructor() {
        class Builder() {
            fun url(url: String): Builder = this
            fun build(): Request = Request()
        }
    }
    class Response : AutoCloseable {
        override fun close() {}
        val isSuccessful: Boolean get() = true
        val body: ResponseBody? = null
    }
    class ResponseBody() {
        fun string(): String = ""
        val bytes: ByteArray get() = ByteArray(0)
    }
    class HttpUrl private constructor()
    class MediaType private constructor()
    class MultipartBody private constructor()
    interface Call {
        fun execute(): Response
        fun enqueue(callback: Callback)
        fun cancel()
    }
    interface Callback {
        fun onFailure(call: Call, e: java.io.IOException)
        fun onResponse(call: Call, response: Response)
    }
    class Interceptor {
        fun intercept(chain: Any): Response = Response()
    }
}
package retrofit2 {
    interface Call<T> {
        fun execute(): Response<T>
    }
    class Response<T> private constructor() {
        val isSuccessful: Boolean get() = true
        val body: T? = null
        val errorBody: okhttp3.ResponseBody? = null
        fun <T> success(body: T?): Response<T> = Response()
    }
    class Retrofit private constructor() {
        class Builder() {
            fun baseUrl(url: String): Builder = this
            fun build(): Retrofit = Retrofit()
        }
    }
}

// ==================================================
// AndroidX Compose / Lifecycle (dummies — used in ui/* only)
// ==================================================
package androidx.compose.runtime {
    annotation class Composable
    fun remember(calc: () -> Any): Any = calc()
}
package androidx.compose.ui {
    object Modifier {
        fun then(other: Any?) = this
    }
}
package androidx.lifecycle {
    open class ViewModel
    open class ViewModelProvider(private val factory: Factory) {
        interface Factory
        fun <T : ViewModel> get(modelClass: Class<T>): T = throw NotImplementedError()
    }
}
