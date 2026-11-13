package com.github.russoturissto.buttonapp.app

import java.util.concurrent.atomic.AtomicInteger

/** One notification per scheduled occurrence, without collision across types. */
internal object ReminderNoticeIds {
    private val nextTest = AtomicInteger((System.nanoTime() and 0x7fffffff).toInt())

    fun payment(id: String, dueAt: Long, day: Int = 0): Int =
        ("payment|" + id + "|" + dueAt + "|" + day).hashCode()

    fun event(id: String, dueAt: Long, day: Int = 0): Int =
        ("event|" + id + "|" + dueAt + "|" + day).hashCode()

    fun diagnostic(kind: String, expected: Long): Int =
        ("diagnostic|" + kind + "|" + expected).hashCode()

    fun test(): Int = nextTest.incrementAndGet()
}
