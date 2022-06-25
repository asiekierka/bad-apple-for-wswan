/* Copyright (c) 2022 Adrian "asie" Siekierka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <wonderful-asm.h>
#include "config.h"

	.arch	i186
	.code16
	.intel_syntax noprefix

	.global tilecpy
tilecpy:
	push	si
	push	di
	push	ds
	push	es
	mov	di, ax
	mov	cx, 0x2000
	mov	ds, cx
	mov	si, dx
	xor	cx, cx
	mov	es, cx
#ifdef IS_2BPP
	mov	cl, 0x8
	cld
	rep	movsw
#else
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
	lodsb
	mov	ah, al
	stosw
#endif
	pop	es
	pop	ds
	pop	di
	pop	si
	ASM_PLATFORM_RET
