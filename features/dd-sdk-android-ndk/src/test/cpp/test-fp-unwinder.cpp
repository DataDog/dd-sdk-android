/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <link.h>
#include <sys/mman.h>
#include <ucontext.h>
#include <unistd.h>

#include "greatest/greatest.h"
#include "utils/fp-unwinder.h"

#if defined(__aarch64__)

// Code without unwind tables (no CFI directives), never executed: its addresses right after each
// call stand for return addresses into code generated without tables (Mono, Dart, JIT).
asm(R"(
    .text
    .p2align 2
    .globl fp_unwinder_test_no_tables
    .type fp_unwinder_test_no_tables, %function
fp_unwinder_test_no_tables:
    bl fp_unwinder_test_no_tables_target
    .globl fp_unwinder_test_no_tables_return_0
fp_unwinder_test_no_tables_return_0:
    bl fp_unwinder_test_no_tables_target
    .globl fp_unwinder_test_no_tables_return_1
fp_unwinder_test_no_tables_return_1:
    bl fp_unwinder_test_no_tables_target
    .globl fp_unwinder_test_no_tables_return_2
fp_unwinder_test_no_tables_return_2:
    bl fp_unwinder_test_no_tables_target
    .globl fp_unwinder_test_no_tables_return_3
fp_unwinder_test_no_tables_return_3:
    ret
    nop
    nop
    .globl fp_unwinder_test_no_tables_after_nops
fp_unwinder_test_no_tables_after_nops:
    ret
fp_unwinder_test_no_tables_target:
    ret
    .size fp_unwinder_test_no_tables, .-fp_unwinder_test_no_tables
)");

extern "C" char fp_unwinder_test_no_tables_return_0[];
extern "C" char fp_unwinder_test_no_tables_return_1[];
extern "C" char fp_unwinder_test_no_tables_return_2[];
extern "C" char fp_unwinder_test_no_tables_return_3[];
extern "C" char fp_unwinder_test_no_tables_after_nops[];

namespace {
    // The kernel's signal return trampoline instructions: mov x8, #139 ; svc #0.
    alignas(8) const uint32_t fake_trampoline[2] = {0xd2801168u, 0xd4000001u};

    // A fake thread stack, in writable memory, holding frame records {next frame pointer, return address}.
    alignas(16) uintptr_t fake_stack[64];

    // Returns its own return address: an address right after a call instruction (BL) in test code,
    // which has unwind tables.
    __attribute__((noinline)) uintptr_t return_address() {
        __asm__ volatile("");
        return reinterpret_cast<uintptr_t>(__builtin_return_address(0));
    }

    uintptr_t return_address_with_tables() {
        return return_address();
    }

    // Return addresses into code without unwind tables.
    const uintptr_t no_tables[4] = {
            reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_return_0),
            reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_return_1),
            reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_return_2),
            reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_return_3),
    };

    uintptr_t chain_start() {
        return reinterpret_cast<uintptr_t>(&fake_stack[0]);
    }

    uintptr_t record_address(size_t index) {
        return reinterpret_cast<uintptr_t>(&fake_stack[index * 8]);
    }

    // Builds a chain of frame records (each 8 words apart) holding the given return addresses,
    // followed by an outermost record {0, 0}, as at the start of a thread.
    void build_chain(const uintptr_t *return_addresses, size_t length) {
        memset(fake_stack, 0, sizeof(fake_stack));
        for (size_t i = 0; i < length; i++) {
            fake_stack[i * 8] = record_address(i + 1);
            fake_stack[i * 8 + 1] = return_addresses[i];
        }
    }

    uintptr_t code_with_tables() {
        return reinterpret_cast<uintptr_t>(&return_address);
    }

    // An instruction inside the block without tables (its first one can still be matched by the
    // unwind table of the function right before it).
    uintptr_t code_without_tables() {
        return reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_return_1);
    }

    uintptr_t trampoline_address() {
        return reinterpret_cast<uintptr_t>(&fake_trampoline[0]);
    }

    ucontext_t crash_context(uintptr_t pc, uintptr_t lr, uintptr_t fp) {
        ucontext_t context;
        memset(&context, 0, sizeof(context));
        context.uc_mcontext.pc = pc;
        context.uc_mcontext.regs[30] = lr;
        context.uc_mcontext.regs[29] = fp;
        context.uc_mcontext.sp = chain_start();
        return context;
    }

    // An address in libart right after a BL instruction, or 0 if libart is not loaded (it is in an
    // app process, not in a standalone test executable).
    uintptr_t libart_return_address() {
        uintptr_t found = 0;
        dl_iterate_phdr([](struct dl_phdr_info *info, size_t, void *data) -> int {
            const char *name = info->dlpi_name;
            const size_t length = name != nullptr ? strlen(name) : 0;
            if (length < 10 || strcmp(name + length - 10, "/libart.so") != 0) {
                return 0;
            }
            for (int i = 0; i < info->dlpi_phnum; i++) {
                const ElfW(Phdr) &header = info->dlpi_phdr[i];
                // readable too: execute-only code (e.g. on API 29) can't be read as data
                if (header.p_type != PT_LOAD || (header.p_flags & (PF_R | PF_X)) != (PF_R | PF_X)) {
                    continue;
                }
                const auto *code = reinterpret_cast<const uint32_t *>(info->dlpi_addr + header.p_vaddr);
                for (size_t j = 0; j + 1 < header.p_memsz / 4; j++) {
                    if ((code[j] & 0xfc000000u) == 0x94000000u) {
                        *static_cast<uintptr_t *>(data) = reinterpret_cast<uintptr_t>(&code[j + 1]);
                        return 1;
                    }
                }
            }
            return 0;
        }, &found);
        return found;
    }
}

TEST test_fp_unwinder_starts_from_the_crash_registers_when_the_unwinder_stops_at_the_crash(void) {
    // Given
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const uintptr_t pc = code_without_tables();
    const ucontext_t context = crash_context(pc, no_tables[0], chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(pc, frames[2]);
    ASSERT_EQ(no_tables[0], frames[3]);
    ASSERT_EQ(no_tables[1], frames[4]);
    ASSERT_EQ(no_tables[2], frames[5]);
    PASS();
}

TEST test_fp_unwinder_continues_from_the_last_unwinder_frame(void) {
    // Given
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, 0, 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    ASSERT_EQ(no_tables[1], frames[4]);
    ASSERT_EQ(no_tables[2], frames[5]);
    PASS();
}

TEST test_fp_unwinder_adds_nothing_when_the_first_recovered_frame_has_unwind_tables(void) {
    // Given: the unwinder would have reached it itself, so the chain skipped a frame or the stack ends.
    const uintptr_t with_tables[3] = {return_address_with_tables(), no_tables[1], no_tables[2]};
    build_chain(with_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, 0, 0);

    // When / Then
    ASSERT_EQ_FMT((size_t) 3, fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_adds_lr_when_the_crash_jumped_to_non_code(void) {
    // Given
    build_chain(no_tables, 2);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const uintptr_t bad_pc = 0x10;
    const ucontext_t context = crash_context(bad_pc, no_tables[3], chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(bad_pc, frames[2]);
    ASSERT_EQ(no_tables[3], frames[3]);
    ASSERT_EQ(no_tables[0], frames[4]);
    ASSERT_EQ(no_tables[1], frames[5]);
    PASS();
}

TEST test_fp_unwinder_leaves_out_lr_when_the_crashed_code_has_no_unwind_tables(void) {
    // Given: lr may be stale (pointing back into the crashed function after a call).
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), no_tables[3], chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    PASS();
}

TEST test_fp_unwinder_adds_lr_when_a_leaf_with_tables_was_called_from_code_without_tables(void) {
    // Given: e.g. memcpy called from code without tables: its caller is only in lr.
    build_chain(no_tables, 2);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, no_tables[3], 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(no_tables[3], frames[3]);
    ASSERT_EQ(no_tables[0], frames[4]);
    ASSERT_EQ(no_tables[1], frames[5]);
    PASS();
}

TEST test_fp_unwinder_leaves_out_lr_pointing_into_code_with_unwind_tables(void) {
    // Given: the unwinder would have reached such a caller, so lr is stale or reused.
    build_chain(no_tables, 2);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, return_address_with_tables(), 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context);

    // Then
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    PASS();
}

TEST test_fp_unwinder_adds_nothing_when_the_unwinder_filled_the_buffer(void) {
    // Given
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, 0, 0);

    // When / Then
    ASSERT_EQ_FMT((size_t) 3, fp_unwinder_append_frames(frames, 3, 16, true, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_adds_nothing_without_a_trampoline(void) {
    // Given
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), code_with_tables()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When / Then
    ASSERT_EQ_FMT((size_t) 2, fp_unwinder_append_frames(frames, 2, 16, false, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_adds_nothing_for_nested_signals(void) {
    // Given
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When / Then
    ASSERT_EQ_FMT((size_t) 4, fp_unwinder_append_frames(frames, 4, 16, false, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_stops_at_a_misaligned_frame_pointer(void) {
    // Given: the second record points to a valid-looking record at a misaligned address.
    build_chain(no_tables, 2);
    auto *misaligned = reinterpret_cast<char *>(&fake_stack[40]) + 4;
    const uintptr_t record[2] = {0, no_tables[2]};
    memcpy(misaligned, record, sizeof(record));
    fake_stack[8] = reinterpret_cast<uintptr_t>(misaligned);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then: the crashed pc, then the first two records only
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(no_tables[1], frames[4]);
    PASS();
}

TEST test_fp_unwinder_stops_when_the_chain_goes_backwards(void) {
    // Given
    build_chain(no_tables, 3);
    fake_stack[8] = chain_start();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When / Then
    ASSERT_EQ_FMT((size_t) 5, fp_unwinder_append_frames(frames, 2, 16, false, 0, &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_stops_at_a_return_address_not_after_a_call(void) {
    // Given
    build_chain(no_tables, 3);
    // executable code, but after NOPs rather than a call
    fake_stack[9] = reinterpret_cast<uintptr_t>(fp_unwinder_test_no_tables_after_nops);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 4, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    PASS();
}

TEST test_fp_unwinder_stops_at_a_return_address_outside_executable_memory(void) {
    // Given: a data address right after a copy of a real call instruction.
    build_chain(no_tables, 3);
    alignas(8) static uint32_t copied_call[2];
    memcpy(&copied_call[0], reinterpret_cast<const void *>(no_tables[1] - 4), sizeof(uint32_t));
    fake_stack[9] = reinterpret_cast<uintptr_t>(&copied_call[1]);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When / Then
    ASSERT_EQ_FMT((size_t) 4, fp_unwinder_append_frames(frames, 2, 16, false, 0, &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_adds_only_the_crashed_pc_when_the_stack_mapping_does_not_hold_sp(void) {
    // Given: sp in another mapping than the frame records.
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    ucontext_t context = crash_context(code_without_tables(), 0, chain_start());
    context.uc_mcontext.sp = reinterpret_cast<uintptr_t>(&fake_trampoline[0]);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 3, total, "%zu");
    ASSERT_EQ(code_without_tables(), frames[2]);
    PASS();
}

TEST test_fp_unwinder_leaves_out_lr_when_the_frame_after_the_trampoline_is_not_the_crash(void) {
    // Given
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), return_address_with_tables()};
    const ucontext_t context = crash_context(pc, no_tables[3], 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    PASS();
}

TEST test_fp_unwinder_does_not_repeat_lr_found_in_the_first_frame_record(void) {
    // Given
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, no_tables[0], 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context);

    // Then
    ASSERT_EQ_FMT((size_t) 6, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    ASSERT_EQ(no_tables[1], frames[4]);
    PASS();
}

TEST test_fp_unwinder_continues_when_the_unwinder_got_past_the_crashed_frame(void) {
    // Given
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc, return_address_with_tables()};
    const ucontext_t context = crash_context(pc, no_tables[3], 0);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 4, 16, false, chain_start(), &context);

    // Then: the chain, without lr (not the crashed frame's caller here)
    ASSERT_EQ_FMT((size_t) 7, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[4]);
    ASSERT_EQ(no_tables[2], frames[6]);
    PASS();
}

TEST test_fp_unwinder_follows_the_chain_when_sp_is_in_the_stack_guard(void) {
    // Given: after a stack overflow, sp is in the inaccessible page right below the stack.
    const size_t page = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    auto *memory = static_cast<char *>(mmap(nullptr, 2 * page, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    ASSERT(memory != MAP_FAILED);
    ASSERT_EQ(0, mprotect(memory + page, page, PROT_READ | PROT_WRITE));
    auto *stack = reinterpret_cast<uintptr_t *>(memory + page);
    stack[0] = reinterpret_cast<uintptr_t>(&stack[8]);
    stack[1] = no_tables[0];
    stack[8] = reinterpret_cast<uintptr_t>(&stack[16]);
    stack[9] = no_tables[1];
    stack[16] = 0;
    stack[17] = 0;
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    ucontext_t context = crash_context(code_without_tables(), 0, reinterpret_cast<uintptr_t>(&stack[0]));
    context.uc_mcontext.sp = reinterpret_cast<uintptr_t>(memory + page - 64);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);
    munmap(memory, 2 * page);

    // Then
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    ASSERT_EQ(no_tables[1], frames[4]);
    PASS();
}

TEST test_fp_unwinder_follows_the_chain_when_sp_is_far_below_the_stack(void) {
    // Given: after a stack overflow with a large frame, sp is in the unmapped gap below the stack.
    const size_t page = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    const size_t gap = 128 * 1024;
    auto *memory = static_cast<char *>(mmap(nullptr, gap + page, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    ASSERT(memory != MAP_FAILED);
    ASSERT_EQ(0, munmap(memory, gap));
    ASSERT_EQ(0, mprotect(memory + gap, page, PROT_READ | PROT_WRITE));
    auto *stack = reinterpret_cast<uintptr_t *>(memory + gap);
    stack[0] = reinterpret_cast<uintptr_t>(&stack[8]);
    stack[1] = no_tables[0];
    stack[8] = reinterpret_cast<uintptr_t>(&stack[16]);
    stack[9] = no_tables[1];
    stack[16] = 0;
    stack[17] = 0;
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    ucontext_t context = crash_context(code_without_tables(), 0, reinterpret_cast<uintptr_t>(&stack[0]));
    context.uc_mcontext.sp = reinterpret_cast<uintptr_t>(memory + gap - 64 * 1024);

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);
    munmap(memory + gap, page);

    // Then
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(no_tables[0], frames[3]);
    ASSERT_EQ(no_tables[1], frames[4]);
    PASS();
}

TEST test_fp_unwinder_ends_at_the_outermost_frame_record(void) {
    // Given: the last record's next frame pointer is 0 (it marks the start of the thread).
    build_chain(no_tables, 3);
    fake_stack[16] = 0;
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then: the crashed pc and the first two records only
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(no_tables[1], frames[4]);
    PASS();
}

TEST test_fp_unwinder_adds_lr_into_code_with_tables_when_the_crashed_code_has_none(void) {
    // Given: a leaf without tables called from code with tables: lr can't be stale.
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const uintptr_t caller = return_address_with_tables();
    const ucontext_t context = crash_context(code_without_tables(), caller, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then
    ASSERT_EQ_FMT((size_t) 7, total, "%zu");
    ASSERT_EQ(caller, frames[3]);
    ASSERT_EQ(no_tables[0], frames[4]);
    PASS();
}

TEST test_fp_unwinder_stops_when_the_crashed_function_was_called_from_java_runtime_code(void) {
    // Given
    const uintptr_t java_runtime = libart_return_address();
    if (java_runtime == 0) {
        SKIPm("libart is not loaded in this process");
    }
    build_chain(no_tables, 3);
    const uintptr_t pc = code_with_tables();
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), pc};
    const ucontext_t context = crash_context(pc, java_runtime, 0);

    // When / Then
    ASSERT_EQ_FMT((size_t) 3, fp_unwinder_append_frames(frames, 3, 16, false, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_stops_when_the_crashed_code_without_tables_was_called_from_java_runtime_code(void) {
    // Given
    const uintptr_t java_runtime = libart_return_address();
    if (java_runtime == 0) {
        SKIPm("libart is not loaded in this process");
    }
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), java_runtime, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then: only the crashed pc
    ASSERT_EQ_FMT((size_t) 3, total, "%zu");
    ASSERT_EQ(code_without_tables(), frames[2]);
    PASS();
}

TEST test_fp_unwinder_respects_the_frame_limit(void) {
    // Given
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When / Then
    ASSERT_EQ_FMT((size_t) 4, fp_unwinder_append_frames(frames, 2, 4, false, 0, &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_adds_nothing_when_the_unwinder_reached_java_runtime_code(void) {
    // Given
    const uintptr_t java_runtime = libart_return_address();
    if (java_runtime == 0) {
        SKIPm("libart is not loaded in this process");
    }
    build_chain(no_tables, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address(), code_with_tables(), java_runtime};
    const ucontext_t context = crash_context(code_with_tables(), 0, 0);

    // When / Then
    ASSERT_EQ_FMT((size_t) 4, fp_unwinder_append_frames(frames, 4, 16, false, chain_start(), &context), "%zu");
    PASS();
}

TEST test_fp_unwinder_stops_after_the_first_java_runtime_frame(void) {
    // Given
    const uintptr_t java_runtime = libart_return_address();
    if (java_runtime == 0) {
        SKIPm("libart is not loaded in this process");
    }
    const uintptr_t chain[3] = {no_tables[0], java_runtime, no_tables[2]};
    build_chain(chain, 3);
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};
    const ucontext_t context = crash_context(code_without_tables(), 0, chain_start());

    // When
    const size_t total = fp_unwinder_append_frames(frames, 2, 16, false, 0, &context);

    // Then: the crashed pc, the first record, then the Java runtime frame
    ASSERT_EQ_FMT((size_t) 5, total, "%zu");
    ASSERT_EQ(java_runtime, frames[4]);
    PASS();
}

TEST test_fp_unwinder_ignores_a_missing_context(void) {
    // Given
    uintptr_t frames[16] = {code_with_tables(), trampoline_address()};

    // When / Then
    ASSERT_EQ_FMT((size_t) 2, fp_unwinder_append_frames(frames, 2, 16, false, 0, nullptr), "%zu");
    PASS();
}

#else

TEST test_fp_unwinder_adds_nothing_on_other_architectures(void) {
    // Given
    uintptr_t frames[4] = {1, 2};
    ucontext_t context;
    memset(&context, 0, sizeof(context));

    // When / Then
    ASSERT_EQ_FMT((size_t) 2, fp_unwinder_append_frames(frames, 2, 4, false, 0, &context), "%zu");
    PASS();
}

#endif

SUITE (fp_unwinder) {
#if defined(__aarch64__)
    RUN_TEST(test_fp_unwinder_starts_from_the_crash_registers_when_the_unwinder_stops_at_the_crash);
    RUN_TEST(test_fp_unwinder_continues_from_the_last_unwinder_frame);
    RUN_TEST(test_fp_unwinder_adds_nothing_when_the_first_recovered_frame_has_unwind_tables);
    RUN_TEST(test_fp_unwinder_adds_lr_when_the_crash_jumped_to_non_code);
    RUN_TEST(test_fp_unwinder_leaves_out_lr_when_the_crashed_code_has_no_unwind_tables);
    RUN_TEST(test_fp_unwinder_adds_lr_when_a_leaf_with_tables_was_called_from_code_without_tables);
    RUN_TEST(test_fp_unwinder_leaves_out_lr_pointing_into_code_with_unwind_tables);
    RUN_TEST(test_fp_unwinder_adds_nothing_when_the_unwinder_filled_the_buffer);
    RUN_TEST(test_fp_unwinder_adds_nothing_without_a_trampoline);
    RUN_TEST(test_fp_unwinder_adds_nothing_for_nested_signals);
    RUN_TEST(test_fp_unwinder_stops_at_a_misaligned_frame_pointer);
    RUN_TEST(test_fp_unwinder_stops_when_the_chain_goes_backwards);
    RUN_TEST(test_fp_unwinder_stops_at_a_return_address_not_after_a_call);
    RUN_TEST(test_fp_unwinder_stops_at_a_return_address_outside_executable_memory);
    RUN_TEST(test_fp_unwinder_adds_only_the_crashed_pc_when_the_stack_mapping_does_not_hold_sp);
    RUN_TEST(test_fp_unwinder_leaves_out_lr_when_the_frame_after_the_trampoline_is_not_the_crash);
    RUN_TEST(test_fp_unwinder_does_not_repeat_lr_found_in_the_first_frame_record);
    RUN_TEST(test_fp_unwinder_continues_when_the_unwinder_got_past_the_crashed_frame);
    RUN_TEST(test_fp_unwinder_follows_the_chain_when_sp_is_in_the_stack_guard);
    RUN_TEST(test_fp_unwinder_follows_the_chain_when_sp_is_far_below_the_stack);
    RUN_TEST(test_fp_unwinder_ends_at_the_outermost_frame_record);
    RUN_TEST(test_fp_unwinder_adds_lr_into_code_with_tables_when_the_crashed_code_has_none);
    RUN_TEST(test_fp_unwinder_stops_when_the_crashed_function_was_called_from_java_runtime_code);
    RUN_TEST(test_fp_unwinder_stops_when_the_crashed_code_without_tables_was_called_from_java_runtime_code);
    RUN_TEST(test_fp_unwinder_respects_the_frame_limit);
    RUN_TEST(test_fp_unwinder_adds_nothing_when_the_unwinder_reached_java_runtime_code);
    RUN_TEST(test_fp_unwinder_stops_after_the_first_java_runtime_frame);
    RUN_TEST(test_fp_unwinder_ignores_a_missing_context);
#else
    RUN_TEST(test_fp_unwinder_adds_nothing_on_other_architectures);
#endif
}
