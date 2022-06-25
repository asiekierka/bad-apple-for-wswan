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

#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <ws.h>
#include "config.h"
#include "fs.h"

uint8_t current_command_bank;
uint16_t current_command_pos = 0x0000;

// controlled by audio_int_handler
volatile uint8_t current_audio_bank;
volatile uint16_t current_audio_pos = 0x0000;

#define screen_a ((uint16_t*) 0x800);
#define screen_b ((uint16_t*) 0x1000);
#define SCREEN_COPY_BYTES (sizeof(uint16_t) * (32 * 17 + 24))

uint16_t *curr_screen = screen_a;
uint16_t *next_screen = screen_b;
uint16_t tile_pos;
uint16_t next_display_control;
volatile uint16_t vblank_ticks;
volatile uint16_t next_vblank_ticks;

#ifdef IS_ADPCM
int16_t adpcm_sample = 0;
int16_t adpcm_predictor = 0;
int16_t adpcm_step_index = 0;
int16_t adpcm_step = 7;
uint8_t adpcm_last_byte;
#endif

extern void tilecpy(void * restrict s1, uint16_t s2);

static void copy_global_tile(uint8_t bank, uint16_t ofs, uint16_t pos) {
	set_rom_bank0(ASSET_TILES_BIN_BANK + bank);
	tilecpy(MEM_TILE(pos), ofs);
	set_rom_bank0(current_command_bank);
}

bool parse_until_next_frame(void) {
	set_rom_bank0(current_command_bank);
	tile_pos = 0;

	while (true) {
		// fetch next command
		uint8_t cmd = MEM_ROM_BANK0[current_command_pos++];
		if (cmd <= 0x7F) {
			// increment tile_pos
			tile_pos += cmd;
		} else if (cmd <= 0xBF) {
			// copy global_tile + place tile_data
			uint8_t cmd2 = MEM_ROM_BANK0[current_command_pos++];
                        uint8_t cmd3 = MEM_ROM_BANK0[current_command_pos++];
                        uint8_t cmd4 = MEM_ROM_BANK0[current_command_pos++];
#ifdef IS_2BPP
                        uint8_t global_tile_bank = (cmd3 >> 4) | ((cmd2 & 0x03) << 4);
                        uint16_t global_tile_ofs = (cmd4 << 4) | (cmd3 << 12);
#else
                        uint8_t global_tile_bank = (cmd3 >> 5) | ((cmd2 & 0x03) << 3);
                        uint16_t global_tile_ofs = (cmd4 << 3) | (cmd3 << 11);
#endif
			uint16_t tile_data = (cmd2 >> 2) | ((cmd & 0x0F) << 6) | ((cmd & 0x30) << 10);
			copy_global_tile(global_tile_bank, global_tile_ofs, tile_data & 0x1FF);
			next_screen[tile_pos++] = tile_data;
		} else if (cmd <= 0xEF) {
			// place tile_data
			uint16_t tile_data = MEM_ROM_BANK0[current_command_pos++] | ((cmd & 0x03) << 8) | ((cmd & 0x0C) << 12);
			next_screen[tile_pos++] = tile_data;
		} else if (cmd == 0xF4) {
			// place empty tile
			next_screen[tile_pos++] = 0;
		} else if (cmd == 0xF5) {
			// place empty inv. tile
			next_screen[tile_pos++] = (1 << 9);
		} else if (cmd == 0xF1) {
			// switch bank
			current_command_bank++;
			current_command_pos = 0;
			set_rom_bank0(current_command_bank);
		} else if (cmd == 0xF6) {
			// set white border
			next_display_control = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(0);
		} else if (cmd == 0xF7) {
			// set black border
			next_display_control = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(7);
		} else if (cmd >= 0xF8) {
			// end frame
			next_vblank_ticks = cmd & 0x7;
			break;
		} else if (cmd == 0xF0) {
			// done!
			outportb(IO_INT_ENABLE, INTR_ENABLE_VBLANK);
			return true;
		}
	}

	return false;
}

extern void vblank_int_handler(void);
extern void audio_int_handler(void);

static const uint16_t __far fade_from_black[] = {
	0x7777, 0x7776, 0x7765, 0x7654, 0x7543, 0x7542, 0x7431, 0x7420
};
#define fade_from_black_len 8

int main(void) {
	// configure display
	outportw(IO_DISPLAY_CTRL, 0);

	video_shade_lut_set(GRAY_LUT(0, 2, 5, 6, 8, 10, 13, 15));
	outportw(IO_SCR_PAL_0, 0x7520);
	outportw(IO_SCR_PAL_1, 0x0257);

	memset(next_screen, 0, SCREEN_COPY_BYTES);
	memset(MEM_TILE(0), 0xFF, 16);

	outportb(IO_SCR_BASE, SCR2_BASE(next_screen));

	outportb(IO_SCR2_WIN_X1, 16);
	outportb(IO_SCR2_WIN_Y1, 0);
	outportb(IO_SCR2_WIN_X2, 207);
	outportb(IO_SCR2_WIN_Y2, 143);

	outportb(IO_SCR2_SCRL_X, -16);
	outportb(IO_SCR2_SCRL_Y, 0);

	next_display_control = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(7);

	// configure interrupts/etc.

	current_command_bank = ASSET_COMMANDS_BIN_BANK;
	current_audio_bank = ASSET_AUDIO_BIN_BANK;

	set_rom_bank1(current_audio_bank);

	outportb(IO_INT_VECTOR, 0x08);
	outportb(IO_INT_ENABLE, INTR_ENABLE_HBLANK_TIMER | INTR_ENABLE_VBLANK);
	outportw(IO_HBLANK_TIMER, 1);
	outportb(IO_TIMER_CTRL, HBLANK_TIMER_ENABLE | HBLANK_TIMER_REPEAT);

	*((uint16_t*) 0x0038) = FP_OFF(vblank_int_handler);
	*((uint16_t*) 0x003A) = FP_SEG(vblank_int_handler);

	*((uint16_t*) 0x003C) = FP_OFF(audio_int_handler);
	*((uint16_t*) 0x003E) = FP_SEG(audio_int_handler);

	outportb(IO_SND_VOL_CH2, 0);
	outportb(IO_SND_VOL_CH2_VOICE, IO_SND_VOL_CH2_FULL);
	outportb(IO_SND_CH_CTRL, SND_CH2_ENABLE | SND_CH2_VOICE);
	outportb(IO_SND_OUT_CTRL, SND_OUT_HEADPHONES_ENABLE | SND_OUT_SPEAKER_ENABLE | SND_OUT_VOLUME_50);

	while (inportb(IO_LCD_LINE) == 144);
	while (inportb(IO_LCD_LINE) != 144);

	__asm volatile ( "sti" );

	vblank_ticks = 0;

	while (1) {
		// flip buffers
		outportb(IO_SCR_BASE, SCR2_BASE(next_screen));
		outportw(IO_DISPLAY_CTRL, next_display_control);
		uint16_t *tmp = curr_screen;
		curr_screen = next_screen;
		next_screen = tmp;

		// copy curr to next
		memcpy(next_screen, curr_screen, SCREEN_COPY_BYTES);

		// draw next frame
		if (parse_until_next_frame()) break;

		while (vblank_ticks < next_vblank_ticks) {
			__asm volatile("hlt");
		}
		__asm volatile ( "cli" ::: "memory" );
		vblank_ticks -= next_vblank_ticks;
		__asm volatile ( "sti" ::: "memory" );
	}

	vblank_ticks = 0;
	next_vblank_ticks = 1;
	while (vblank_ticks < next_vblank_ticks) {
		__asm volatile("hlt");
	}

	// disable display
	outportw(IO_DISPLAY_CTRL, DISPLAY_BORDER(7));
	outportb(IO_SCR2_SCRL_X, 0);

	// copy tiles and map
	memcpy(MEM_TILE(0), asset_map(ASSET_END_CARD_TILES_BIN), ASSET_END_CARD_TILES_BIN_SIZE);
	video_screen_put_map(curr_screen, asset_map(ASSET_END_CARD_MAP_BIN), 0, 0, 28, 18);

	for (int i = 0; i < fade_from_black_len; i++) {
		vblank_ticks = 0;
		next_vblank_ticks = 6;
		while (vblank_ticks < next_vblank_ticks) {
			__asm volatile("hlt");
		}

		// re-enable display
		outportw(IO_DISPLAY_CTRL, DISPLAY_SCR2_ENABLE);
		outportw(IO_SCR_PAL_0, fade_from_black[i]);
	}

	outportb(IO_INT_ENABLE, 0);

	while (true) {
		__asm volatile("hlt");
	}
}
