/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

    .text
    .p2align 2
    .global hyperceiler_title_size_hook
    .hidden hyperceiler_title_size_hook
    .type hyperceiler_title_size_hook, %function

/*
 * Dart AOT does not use the platform C ABI at this entry point. Keep the
 * incoming Dart register state intact, invoke the trampoline with the exact
 * original arguments, then scale only the double return value in d0.
 */
hyperceiler_title_size_hook:
    sub sp, sp, #32
    stp x29, x30, [sp, #16]
    str q1, [sp]

    adrp x0, g_title_size_original
    ldr x0, [x0, :lo12:g_title_size_original]
    blr x0

    adrp x0, g_title_size_scale
    ldr d1, [x0, :lo12:g_title_size_scale]
    fmul d0, d0, d1

    ldr q1, [sp]
    ldp x29, x30, [sp, #16]
    add sp, sp, #32
    ret

    .size hyperceiler_title_size_hook, .-hyperceiler_title_size_hook
