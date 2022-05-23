	.arch	i186
	.code16
	.intel_syntax noprefix

	.section .text
	.global current_audio_bank
	.global current_audio_pos
	.global vblank_ticks
	.global vblank_int_handler
	.global audio_int_handler

vblank_int_handler:
	push ax
	inc word ptr ss:[vblank_ticks]
	mov al, 0x40
	out 0xB6, al
	pop ax
	iret
	
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
	
