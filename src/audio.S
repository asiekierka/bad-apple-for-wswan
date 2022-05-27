#include "wonderful-support.h"
#include "config.h"

	.arch	i186
	.code16
	.intel_syntax noprefix

	.section .text
	.global current_audio_bank
	.global current_audio_pos
	.global vblank_ticks
	.global vblank_int_handler
	.global audio_int_handler

#ifdef IS_ADPCM
#define ADPCM_PSEUDODITHER

	.global adpcm_predictor
	.global adpcm_sample
	.global adpcm_step_index
	.global adpcm_step
	.global adpcm_last_byte

	.align 2

ima_step_table:
	.hword 7, 8, 9, 10, 11, 12, 13, 14, 16, 17
	.hword 19, 21, 23, 25, 28, 31, 34, 37, 41, 45
	.hword 50, 55, 60, 66, 73, 80, 88, 97, 107, 118
	.hword 130, 143, 157, 173, 190, 209, 230, 253, 279, 307
	.hword 337, 371, 408, 449, 494, 544, 598, 658, 724, 796
	.hword 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066
	.hword 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358
	.hword 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899
	.hword 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767

step_index_wrap_table:
	.byte 0, 1, 2, 3, 4, 5, 6, 7
	.byte 8, 9, 10, 11, 12, 13, 14, 15
	.byte 16, 17, 18, 19, 20, 21, 22, 23
	.byte 24, 25, 26, 27, 28, 29, 30, 31
	.byte 32, 33, 34, 35, 36, 37, 38, 39
	.byte 40, 41, 42, 43, 44, 45, 46, 47
	.byte 48, 49, 50, 51, 52, 53, 54, 55
	.byte 56, 57, 58, 59, 60, 61, 62, 63
	.byte 64, 65, 66, 67, 68, 69, 70, 71
	.byte 72, 73, 74, 75, 76, 77, 78, 79
	.byte 80, 81, 82, 83, 84, 85, 86, 87
	.byte 88, 88, 88, 88, 88, 88, 88, 88
	.byte 88, 88, 88, 88, 88, 88, 88, 88 // 96
	.byte 88, 88, 88, 88, 88, 88, 88, 88 // 104
	.byte 88, 88, 88, 88, 88, 88, 88, 88 // 112
	.byte 88, 88, 88, 88, 88, 88, 88, 88 // 120

ima_index_table:
	.byte -1, -1, -1, -1, 2, 4, 6, 8
	.byte -1, -1, -1, -1, 2, 4, 6, 8

nibble_to_diff_table:
	.byte 1, 3, 5, 7, 9, 11, 13, 15
	.byte -1, -3, -5, -7, -9, -11, -13, -15

	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 160
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 168
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 176
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 184
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 192
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 200
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 208
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 216
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 224
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 232
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 240
	.byte 0, 0, 0, 0, 0, 0, 0, 0 // 248
#endif

vblank_int_handler:
	push ax
	inc word ptr ss:[vblank_ticks]
	mov al, 0x40
	out 0xB6, al
	pop ax
	iret

#ifdef IS_ADPCM
.macro adpcm_handler shift=0, next_label
	// read next audio byte
	push ax
	push bx
	push cx
	push dx
	push ds

#ifdef ADPCM_PSEUDODITHER
	// mov bx, word ptr ss:[adpcm_sample]
	// mov al, bh
	mov al, byte ptr ss:[adpcm_sample + 1]
#else
	mov al, byte ptr ss:[adpcm_predictor + 1]
#endif
	xor al, 0x80
	out 0x89, al

.if \shift<=0
	mov bx, 0x3000
	mov ds, bx
	mov bx, word ptr ss:[current_audio_pos]
	mov al, byte ptr [bx]

	xor dx, dx
	mov ds, dx

	mov byte ptr [adpcm_last_byte], al
.else
	xor dx, dx
	mov ds, dx

	mov al, byte ptr [adpcm_last_byte]
.endif
#ifdef ADPCM_PSEUDODITHER
	mov byte ptr [adpcm_sample + 1], dl
#endif

.if \shift>0
	shr al, \shift
.else
	and al, 0xF
.endif
	mov dl, al
	// al = nibble
	mov bx, offset ima_index_table
	cs xlat // al = ima_index_table[nibble]
	mov bl, byte ptr [adpcm_step_index]
	add bl, al // (bl) step_index = step_index + ima_index_table[nibble]
	xor bh, bh
	cs mov bl, byte ptr [bx + step_index_wrap_table]
	mov byte ptr [adpcm_step_index], bl // (bl) step_index

	shl bx, 1
	cs mov ax, word ptr [bx+ima_step_table]
	mov word ptr [adpcm_step], ax

	mov bx, dx // bx = nibble
	cs mov ax, byte ptr [bx+nibble_to_diff_table]
	cbw
	imul word ptr [adpcm_step] // (dx:ax) diff = (2 * nibble + 1) * step (signed)
	mov cx, dx
	shl dl, 5
	shr ax, 3
	or ah, dl
	sar cx, 3 // cx:ax = (cx:ax >> 3)
	mov bx, ax // cx:bx = cx:ax

	mov ax, word ptr [adpcm_predictor]
	cwd
	add bx, ax
	adc cx, dx // cx:bx = predictor + diff

	// test cx, cx // TODO: Remove me?
	js .audio_int_handler\@_clampbelow0
	// >= 0x0000????
	jnz .audio_int_handler\@_clampup
	// == 0x0000????
	test bx, bx
	jns .audio_int_handler\@_ok

.audio_int_handler\@_clampup:
	mov bx, 32767
	jmp .audio_int_handler\@_ok
.audio_int_handler\@_clampdown:
	mov bx, -32768
	jmp .audio_int_handler\@_ok

.audio_int_handler\@_clampbelow0:
	// <= 0xFFFF????
	cmp cx, 0xFFFF
	jne .audio_int_handler\@_clampdown
	// == 0xFFFF????
	test bx, bx
	jns .audio_int_handler\@_clampdown

.audio_int_handler\@_ok:
	mov word ptr [adpcm_predictor], bx // predictor += diff
#ifdef ADPCM_PSEUDODITHER
	sar bx, 1
	add word ptr [adpcm_sample], bx
#endif

.if \shift>0
	inc word ptr [current_audio_pos]
	jnz .audio_int_handler\@_done

	mov al, byte ptr [current_audio_bank]
	inc al
	out 0xC3, al
	mov byte ptr [current_audio_bank], al
.endif
.audio_int_handler\@_done:
	mov al, 0x80
	out 0xB6, al

	mov ax, offset \next_label
	mov [0x3C], ax

	pop ds
	pop dx
	pop cx
	pop bx
	pop ax
	iret
.endm

audio_int_handler:
	adpcm_handler 0, audio_int_handler4

audio_int_handler4:
	adpcm_handler 4, audio_int_handler

#else
audio_int_handler:
	// read next audio byte
	push ax
	push bx
	push ds

	mov bx, 0x3000
	mov ds, bx
	mov bx, word ptr ss:[current_audio_pos]
	mov al, byte ptr [bx]
	out 0x89, al

	inc bx
	mov word ptr ss:[current_audio_pos], bx
	jnz .audio_int_handler_done
.audio_int_handler_bank:
	mov al, byte ptr ss:[current_audio_bank]
	inc al
	out 0xC3, al
	mov byte ptr ss:[current_audio_bank], al
.audio_int_handler_done:
	mov al, 0x80
	out 0xB6, al

	pop ds
	pop bx
	pop ax
	iret
#endif
