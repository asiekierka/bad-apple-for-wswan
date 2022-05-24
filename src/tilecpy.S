#include "wonderful-support.h"
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
	ASM_RET	0x0
