/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// for process_vm_readv
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include "fp-unwinder.h"

#if defined(__aarch64__)

#include <errno.h>
#include <fcntl.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <ucontext.h>
#include <unistd.h>
#include <unwind.h>

/*
 * How it works
 *
 * The unwinder (_Unwind_Backtrace) stops at the first function without unwind tables. On arm64,
 * functions that keep a frame pointer save the caller's frame pointer (x29) and return address
 * (x30) as a pair on the stack, and point x29 at that pair, so the pairs form a chain that can be
 * followed without tables. Mono's AOT and JIT code and Dart's AOT code keep that chain.
 *
 * Where to start:
 * - If the unwinder could not get past the crashed code itself (its last frame is the kernel's
 *   signal return trampoline), start from the crash registers: the crashed pc, then x29's chain.
 * - Otherwise, continue from the x29 of the last frame the unwinder reached. The first frame added
 *   must then be in code without unwind tables: otherwise the unwinder would have reached it, so
 *   the chain skipped a frame or the stack ends there, and nothing is added.
 * The link register (x30) is added when it is known to be a live caller, right after a call: when
 * the crash jumped to non-code (a bad function pointer); when the crashed function has unwind
 * tables and lr points into code without them (a leaf, such as memcpy, called from code without
 * tables); or when the crashed function has no tables and lr points into code with them. When both
 * have no tables, a leaf's caller can't be told apart from a stale lr, so lr is left out. If lr is
 * Java runtime code, the walk stops there.
 *
 * Every step is checked, and the walk stops at the first doubtful one:
 * - the frame pointer is 8-byte aligned, at or above the crash sp, increases from frame to frame by
 *   at most 1 MiB, and lies in the writable mapping that holds the first frame pointer and the
 *   crash sp (or that is at most 1 MiB above sp with only inaccessible or unmapped memory in
 *   between, after a stack overflow);
 * - a record whose next frame pointer is 0 ends the chain (it marks the start of the thread);
 * - memory is only read with process_vm_readv, which fails instead of crashing on bad addresses;
 * - a return address (pointer authentication bits stripped) is 4-byte aligned, in executable memory,
 *   and right after a call instruction (or after the ADR that Dart's FFI calls use to set it);
 * - the walk stops at Java runtime code (libart, oat files, JIT caches), where x29 is not a frame
 *   chain any more; a Java runtime frame is only kept if something before it was recovered (the
 *   crashed pc is always kept).
 * Nothing is appended when the unwinder filled the buffer, when its last frame is already Java
 * runtime code, or for nested signals; only the crashed pc is when /proc/self/maps can't be read.
 *
 * To keep the cost low, the chain is first followed without /proc/self/maps; the maps are only
 * read (once, streamed through a small buffer) when there are candidate frames to check.
 */

#define TAG_MASK ((uintptr_t)0x00ffffffffffffffULL)
#define MAX_FRAME_SIZE ((uintptr_t)1024 * 1024)
#define MAX_CANDIDATES 128
#define MAPS_CHUNK_SIZE 4096
#define PATH_HEAD_SIZE 48
#define PATH_TAIL_SIZE 64 /* a power of two */

/* The walk uses static memory (the signal stack is small); one walk at a time. */
static atomic_flag walk_in_progress = ATOMIC_FLAG_INIT;

struct candidate {
    uintptr_t address;
    uintptr_t frame_pointer; /* the frame record it was read from */
};
static struct candidate chain[MAX_CANDIDATES];

/* Addresses to classify with /proc/self/maps, and the result for each. */
enum { ADDRESS_PC, ADDRESS_LR, ADDRESS_LAST_UNWINDER_FRAME, ADDRESS_CHAIN_START };
static uintptr_t addresses[ADDRESS_CHAIN_START + MAX_CANDIDATES];
static bool is_executable[ADDRESS_CHAIN_START + MAX_CANDIDATES];
static bool is_java_runtime[ADDRESS_CHAIN_START + MAX_CANDIDATES];

static char maps_chunk[MAPS_CHUNK_SIZE];

static pid_t own_pid;

static uintptr_t strip_pointer_authentication(uintptr_t address) {
    /* XPACLRI is in the HINT space: a no-op on CPUs without pointer authentication. */
    register uintptr_t x30 __asm__("x30") = address;
    __asm__ volatile("hint #7" : "+r"(x30));
    return x30;
}

static bool safe_read(uintptr_t address, void *value, size_t size) {
    struct iovec local = {value, size};
    struct iovec remote = {(void *) address, size};
    return process_vm_readv(own_pid, &local, 1, &remote, 1, 0) == (ssize_t) size;
}

/* The arm64 Linux signal return trampoline: mov x8, #139 (rt_sigreturn); svc #0. */
static bool is_sigreturn_trampoline(uintptr_t address) {
    uint64_t instructions;
    if (!safe_read(address, &instructions, sizeof(instructions))) {
        return false;
    }
    return (uint32_t) instructions == 0xd2801168u && (uint32_t) (instructions >> 32) == 0xd4000001u;
}

/* Whether the instruction before a return address is a call that returns there. */
static bool follows_call(uintptr_t return_address) {
    uint32_t instruction;
    if (!safe_read(return_address - 4, &instruction, sizeof(instruction))) {
        return false;
    }
    if ((instruction & 0xfc000000u) == 0x94000000u /* BL */ ||
        (instruction & 0xfffffc1fu) == 0xd63f0000u /* BLR */ ||
        (instruction & 0xfefff800u) == 0xd63f0800u /* BLRAA, BLRAB, BLRAAZ, BLRABZ */) {
        return true;
    }
    if ((instruction & 0x9f000000u) == 0x10000000u /* ADR */) {
        int64_t offset = (int64_t) ((((instruction >> 5) & 0x7ffffu) << 2) | ((instruction >> 29) & 0x3u));
        if (offset & (1 << 20)) {
            offset -= (1 << 21);
        }
        return (uintptr_t) ((int64_t) (return_address - 4) + offset) == return_address;
    }
    return false;
}

/* ---- Streaming /proc/self/maps parser ---- */

/* Fields of a line: "low-high permissions offset device inode   path" */
enum maps_field {
    FIELD_LOW,
    FIELD_HIGH,
    FIELD_PERMISSIONS,
    FIELD_OFFSET,
    FIELD_DEVICE,
    FIELD_INODE,
    FIELD_BEFORE_PATH,
    FIELD_PATH
};

struct maps_line {
    enum maps_field field;
    uintptr_t low;
    uintptr_t high;
    char permissions[4];
    size_t permissions_length;
    size_t path_length;
    char path_head[PATH_HEAD_SIZE]; /* the first characters of the path */
    char path_tail[PATH_TAIL_SIZE]; /* the last characters of the path, as a ring */
    bool in_dalvik_cache;
};

/* Whether the path's characters ending at `end` are `suffix` (false if they are not kept). */
static bool path_ends_with(const struct maps_line *line, size_t end, const char *suffix) {
    const size_t length = strlen(suffix);
    if (length > end || end > line->path_length || line->path_length - (end - length) > PATH_TAIL_SIZE) {
        return false;
    }
    for (size_t i = 0; i < length; i++) {
        if (line->path_tail[(end - length + i) & (PATH_TAIL_SIZE - 1)] != suffix[i]) {
            return false;
        }
    }
    return true;
}

static void add_path_character(struct maps_line *line, char c) {
    if (line->path_length < PATH_HEAD_SIZE) {
        line->path_head[line->path_length] = c;
    }
    line->path_tail[line->path_length & (PATH_TAIL_SIZE - 1)] = c;
    line->path_length++;
    if (c == '/' && path_ends_with(line, line->path_length, "/dalvik-cache/")) {
        line->in_dalvik_cache = true;
    }
}

/* Names of the Java runtime's JIT code caches, depending on the API level. */
static const char *const jit_cache_names[] = {
        "/memfd:jit-cache",
        "/memfd:jit-zygote-cache",
        "[anon:dalvik-jit-code-cache]",
        "[anon:dalvik-zygote-jit-code-cache]",
        "/dev/ashmem/dalvik-jit-code-cache",
        "/dev/ashmem/dalvik-zygote-jit-code-cache",
};

static bool is_java_runtime_path(const struct maps_line *line) {
    if (line->path_length == 0) {
        return false;
    }
    size_t end = line->path_length;
    if (path_ends_with(line, end, " (deleted)")) {
        end -= strlen(" (deleted)");
    }
    if (line->in_dalvik_cache || path_ends_with(line, end, "/libart.so") ||
        path_ends_with(line, end, ".oat") || path_ends_with(line, end, ".odex")) {
        return true;
    }
    for (size_t i = 0; i < sizeof(jit_cache_names) / sizeof(jit_cache_names[0]); i++) {
        const size_t length = strlen(jit_cache_names[i]);
        if (end == length && length <= PATH_HEAD_SIZE && memcmp(line->path_head, jit_cache_names[i], length) == 0) {
            return true;
        }
    }
    return false;
}

static int hex_digit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

static void add_character(struct maps_line *line, char c) {
    switch (line->field) {
        case FIELD_LOW:
        case FIELD_HIGH: {
            if (c == '-' && line->field == FIELD_LOW) {
                line->field = FIELD_HIGH;
            } else if (c == ' ' && line->field == FIELD_HIGH) {
                line->field = FIELD_PERMISSIONS;
            } else {
                const int digit = hex_digit(c);
                uintptr_t *value = line->field == FIELD_LOW ? &line->low : &line->high;
                if (digit >= 0) {
                    *value = (*value << 4) | (uintptr_t) digit;
                }
            }
            break;
        }
        case FIELD_PERMISSIONS:
            if (c == ' ') {
                line->field = FIELD_OFFSET;
            } else if (line->permissions_length < sizeof(line->permissions)) {
                line->permissions[line->permissions_length++] = c;
            }
            break;
        case FIELD_OFFSET:
        case FIELD_DEVICE:
        case FIELD_INODE:
            if (c == ' ') {
                line->field = (enum maps_field) (line->field + 1);
            }
            break;
        case FIELD_BEFORE_PATH:
            if (c != ' ') {
                line->field = FIELD_PATH;
                add_path_character(line, c);
            }
            break;
        case FIELD_PATH:
            add_path_character(line, c);
            break;
    }
}

struct maps_query {
    size_t address_count;
    uintptr_t stack_address; /* the first frame pointer */
    uintptr_t sp;
    uintptr_t stack_low;
    uintptr_t stack_high;
    uintptr_t accessible_end; /* the end of the highest accessible mapping seen so far */
};

static bool in_line(const struct maps_line *line, uintptr_t address) {
    return address >= line->low && address < line->high;
}

static void finish_line(struct maps_line *line, struct maps_query *query) {
    if (line->field < FIELD_OFFSET || line->permissions_length < 3) {
        return;
    }
    const bool executable = line->permissions[2] == 'x';
    const bool java_runtime = executable && is_java_runtime_path(line);
    for (size_t i = 0; i < query->address_count; i++) {
        if (in_line(line, addresses[i])) {
            is_executable[i] = executable;
            is_java_runtime[i] = java_runtime;
        }
    }
    /*
     * After a stack overflow, sp can be below the stack: in its guard page or in unmapped space,
     * with no accessible mapping between sp and the stack (lines come in address order).
     */
    const bool sp_just_below = query->sp < line->low && line->low - query->sp <= MAX_FRAME_SIZE &&
                               query->accessible_end <= query->sp;
    if (in_line(line, query->stack_address) && (in_line(line, query->sp) || sp_just_below) &&
        line->permissions[0] == 'r' && line->permissions[1] == 'w') {
        query->stack_low = line->low;
        query->stack_high = line->high;
    }
    if (line->permissions[0] != '-' || line->permissions[1] != '-' || line->permissions[2] != '-') {
        query->accessible_end = line->high;
    }
}

/* Reads /proc/self/maps once. Returns false if it can't be read completely. */
static bool query_maps(struct maps_query *query) {
    for (size_t i = 0; i < query->address_count; i++) {
        is_executable[i] = false;
        is_java_runtime[i] = false;
    }
    query->stack_low = 0;
    query->stack_high = 0;
    query->accessible_end = 0;
    const int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return false;
    }
    struct maps_line line;
    memset(&line, 0, sizeof(line));
    bool complete = true;
    for (;;) {
        const ssize_t length = read(fd, maps_chunk, sizeof(maps_chunk));
        if (length < 0) {
            if (errno == EINTR) {
                continue;
            }
            complete = false;
            break;
        }
        if (length == 0) {
            break;
        }
        for (ssize_t i = 0; i < length; i++) {
            if (maps_chunk[i] == '\n') {
                finish_line(&line, query);
                memset(&line, 0, sizeof(line));
            } else {
                add_character(&line, maps_chunk[i]);
            }
        }
    }
    close(fd);
    if (complete) {
        finish_line(&line, query);
    }
    return complete;
}

/* ---- The walk ---- */

static bool contains(const uintptr_t *values, size_t count, uintptr_t value) {
    for (size_t i = 0; i < count; i++) {
        if (values[i] == value) {
            return true;
        }
    }
    return false;
}

/* Follows the frame pointer chain with the checks that need no memory map. */
static size_t follow_chain(uintptr_t frame_pointer, uintptr_t sp, size_t max) {
    size_t length = 0;
    uintptr_t previous = 0;
    while (length < max) {
        if (frame_pointer == 0 || (frame_pointer & 0x7) != 0 || frame_pointer < sp) {
            break;
        }
        if (previous != 0 && (frame_pointer <= previous || frame_pointer - previous > MAX_FRAME_SIZE)) {
            break;
        }
        uintptr_t next;
        uintptr_t return_address;
        if (!safe_read(frame_pointer, &next, sizeof(next)) ||
            !safe_read(frame_pointer + 8, &return_address, sizeof(return_address))) {
            break;
        }
        return_address = strip_pointer_authentication(return_address);
        if (next == 0) {
            /* the outermost record: it only marks the end of the stack (e.g. thread or process start) */
            break;
        }
        if (return_address == 0 || (return_address & 0x3) != 0 || !follows_call(return_address)) {
            break;
        }
        chain[length].address = return_address;
        chain[length].frame_pointer = frame_pointer;
        length++;
        previous = frame_pointer;
        frame_pointer = next & TAG_MASK;
    }
    return length;
}

/* Whether code at address has unwind tables, i.e. the unwinder could have unwound through it. */
static bool has_unwind_tables(uintptr_t address) {
    return _Unwind_FindEnclosingFunction((void *) address) != NULL;
}

/* Whether the crash-time lr is a live caller of the crashed function (see "How it works"). */
static bool is_live_caller(uintptr_t lr,
                           uintptr_t pc,
                           size_t chain_length,
                           const uintptr_t *frames,
                           size_t total) {
    if (lr == 0 || (lr & 0x3) != 0 || !is_executable[ADDRESS_LR] || is_java_runtime[ADDRESS_LR]) {
        return false;
    }
    if ((chain_length > 0 && lr == chain[0].address) || contains(frames, total, lr)) {
        return false;
    }
    if (!is_executable[ADDRESS_PC]) {
        /* the crash jumped to non-code: lr is where the call came from */
        return follows_call(lr);
    }
    if (!has_unwind_tables(pc)) {
        /* crashed in code without tables: lr is live only if it is outside it, in code with tables */
        return has_unwind_tables(lr - 1) && follows_call(lr);
    }
    /* crashed in code with tables: a caller with tables would have been reached by the unwinder */
    return !has_unwind_tables(lr - 1) && follows_call(lr);
}

static size_t walk(uintptr_t *frames,
                   size_t count,
                   size_t max,
                   bool unwinder_filled_buffer,
                   uintptr_t last_frame_fp,
                   const ucontext_t *context) {
    if (unwinder_filled_buffer || count == 0 || count >= max) {
        return count;
    }
    /* The handler's own frames end at the signal return trampoline. */
    size_t trampoline = count;
    size_t trampolines = 0;
    for (size_t i = 0; i < count; i++) {
        if (is_sigreturn_trampoline(frames[i])) {
            if (trampolines++ == 0) {
                trampoline = i;
            }
        }
    }
    if (trampolines != 1) {
        /* not found, or a signal raised while handling another one: not handled */
        return count;
    }

    const uintptr_t pc = context->uc_mcontext.pc;
    const uintptr_t lr = strip_pointer_authentication(context->uc_mcontext.regs[30]);
    const uintptr_t sp = context->uc_mcontext.sp & TAG_MASK;
    const bool unwinder_stopped_at_crash = trampoline == count - 1;
    /* the unwinder reported the crashed frame, then stopped */
    const bool unwinder_stopped_after_crash = trampoline == count - 2 && frames[count - 1] == pc;
    const uintptr_t start_fp = (unwinder_stopped_at_crash ? context->uc_mcontext.regs[29] : last_frame_fp) & TAG_MASK;

    /* 1. Follow the chain without the memory map (appending below stops at max anyway). */
    const size_t room = max - count;
    const size_t chain_length = follow_chain(start_fp, sp, room < MAX_CANDIDATES ? room : MAX_CANDIDATES);
    if (!unwinder_stopped_at_crash && !unwinder_stopped_after_crash &&
        (chain_length == 0 || has_unwind_tables(chain[0].address - 1))) {
        /* continuing from the unwinder: nothing to add (see "Where to start") */
        return count;
    }

    /* 2. Check the candidates against the memory map. */
    addresses[ADDRESS_PC] = pc;
    addresses[ADDRESS_LR] = lr;
    addresses[ADDRESS_LAST_UNWINDER_FRAME] = frames[count - 1];
    for (size_t i = 0; i < chain_length; i++) {
        addresses[ADDRESS_CHAIN_START + i] = chain[i].address;
    }
    struct maps_query query = {ADDRESS_CHAIN_START + chain_length, start_fp, sp, 0, 0, 0};
    if (!query_maps(&query)) {
        if (unwinder_stopped_at_crash) {
            /* the crashed pc needs no check */
            frames[count] = pc;
            return count + 1;
        }
        return count;
    }
    if (!unwinder_stopped_at_crash && is_java_runtime[ADDRESS_LAST_UNWINDER_FRAME]) {
        /* the unwinder already reached Java code: nothing to add */
        return count;
    }

    /* 3. Append, stopping at the first doubtful frame. */
    size_t total = count;
    size_t recovered = 0;
    if (unwinder_stopped_at_crash) {
        frames[total++] = pc;
        if (is_java_runtime[ADDRESS_PC]) {
            return total;
        }
        recovered++;
    }
    if ((unwinder_stopped_at_crash || unwinder_stopped_after_crash) && total < max &&
        is_live_caller(lr, pc, chain_length, frames, total)) {
        frames[total++] = lr;
        recovered++;
    }
    if (unwinder_stopped_after_crash && is_java_runtime[ADDRESS_LR] && follows_call(lr)) {
        /* the crashed function was called from Java code: x29 is not a frame chain there */
        return total;
    }
    if (query.stack_high == 0) {
        return total;
    }
    for (size_t i = 0; i < chain_length && total < max; i++) {
        const uintptr_t frame_pointer = chain[i].frame_pointer;
        const uintptr_t address = chain[i].address;
        if (frame_pointer < query.stack_low || frame_pointer + 16 > query.stack_high) {
            break;
        }
        if (!is_executable[ADDRESS_CHAIN_START + i]) {
            break;
        }
        if (recovered == 0 && has_unwind_tables(address - 1)) {
            /* continuing from the unwinder: it would have reached this frame itself */
            break;
        }
        if (is_java_runtime[ADDRESS_CHAIN_START + i]) {
            if (recovered > 0) {
                frames[total++] = address;
            }
            break;
        }
        frames[total++] = address;
        recovered++;
    }
    return total;
}

size_t fp_unwinder_append_frames(uintptr_t *frames,
                                 size_t count,
                                 size_t max,
                                 bool unwinder_filled_buffer,
                                 uintptr_t last_frame_fp,
                                 const void *user_context) {
    if (frames == NULL || user_context == NULL) {
        return count;
    }
    if (atomic_flag_test_and_set(&walk_in_progress)) {
        /* another thread is crashing at the same time: keep the unwinder's frames */
        return count;
    }
    /* not getpid(): bionic caches it, which is wrong in a child created with a raw clone */
    own_pid = (pid_t) syscall(SYS_getpid);
    const size_t total = walk(frames, count, max, unwinder_filled_buffer, last_frame_fp,
                              (const ucontext_t *) user_context);
    atomic_flag_clear(&walk_in_progress);
    return total;
}

#else

size_t fp_unwinder_append_frames(uintptr_t *frames,
                                 size_t count,
                                 size_t max,
                                 bool unwinder_filled_buffer,
                                 uintptr_t last_frame_fp,
                                 const void *user_context) {
    (void) frames;
    (void) max;
    (void) unwinder_filled_buffer;
    (void) last_frame_fp;
    (void) user_context;
    return count;
}

#endif
