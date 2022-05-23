#include "wonderful-support.h"

	.arch	i8086
	.code16
	.intel_syntax noprefix

	.global tilecpy
tilecpy:
	push	si
	push	di
	push	ds
	push	es
	push	bp
	mov	bp, sp
	mov	di, ax
	mov	ds, cx
	mov	si, dx
	xor	cx, cx
	mov	es, cx
	mov	cl, 0x8
	cld
	rep	movsw
	pop	bp
	pop	es
	pop	ds
	pop	di
	pop	si
	ASM_RET	0x0
