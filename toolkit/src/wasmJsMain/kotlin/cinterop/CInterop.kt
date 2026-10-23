// Just enough of kotlinx.cinterop for platform.posix (Posix.kt), so app code
// that reads the wall clock the native way compiles unchanged:
//   memScoped { val ts = alloc<timespec>(); clock_gettime(CLOCK_REALTIME, ts.ptr) }
package kotlinx.cinterop

import platform.posix.time_tVar
import platform.posix.timespec
import platform.posix.tm

abstract class CPointed

class CPointer<T : CPointed>(val pointed: T)

val <T : CPointed> T.ptr: CPointer<T> get() = CPointer(this)

class MemScope

inline fun <R> memScoped(block: MemScope.() -> R): R = MemScope().block()

@Suppress("UNCHECKED_CAST")
inline fun <reified T : CPointed> MemScope.alloc(): T = when (T::class) {
    time_tVar::class -> time_tVar()
    timespec::class -> timespec()
    tm::class -> tm()
    else -> error("alloc<${T::class.simpleName}>() isn't in the browser's cinterop")
} as T
