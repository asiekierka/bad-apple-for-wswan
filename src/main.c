#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <ws.h>
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

extern void tilecpy(void * restrict s1, const void __far* restrict s2);

static void increment_next_tile_pos(void) {
	if ((tile_pos & 0x1F) == 23) {
		tile_pos += 9;
	} else {
		tile_pos++;
	}
}

static void unpack_and_set_tile_pos(uint16_t value) {
	tile_pos = ((value / 24) << 5) | (value % 24); 
}

static void copy_global_tile(uint8_t bank, uint16_t ofs, uint16_t pos) {
	set_rom_bank0(ASSET_TILES_BIN_BANK + bank);
	tilecpy(MEM_TILE(pos), MEM_ROM_BANK0 + ofs);
	set_rom_bank0(current_command_bank);
}

bool parse_until_next_frame(void) {
	set_rom_bank0(current_command_bank);
	tile_pos = 0;

	while (true) {
		// fetch next command
		uint8_t cmd = MEM_ROM_BANK0[current_command_pos++];
		if (cmd <= 0x7F) {
			// copy global_tile + place tile_data
			uint8_t cmd2 = MEM_ROM_BANK0[current_command_pos++];
			uint8_t cmd3 = MEM_ROM_BANK0[current_command_pos++];
			uint8_t cmd4 = MEM_ROM_BANK0[current_command_pos++];
			uint8_t cmd5 = MEM_ROM_BANK0[current_command_pos++];
			uint8_t global_tile_bank = (cmd4 >> 4) | ((cmd3 & 0x03) << 4);
			uint16_t global_tile_ofs = (cmd5 << 4) | (cmd4 << 12);
			uint16_t tile_data = (cmd3 >> 2) | ((cmd2 & 0x0F) << 6) | ((cmd2 & 0x30) << 10);
			uint16_t new_tpos_packed = (cmd << 2) | (cmd2 >> 6);
			copy_global_tile(global_tile_bank, global_tile_ofs, tile_data & 0x1FF);
			unpack_and_set_tile_pos(new_tpos_packed);
			next_screen[tile_pos] = tile_data;
			increment_next_tile_pos();
		} else if (cmd <= 0xDF) {
			// place tile_data + set tpos
			uint8_t cmd2 = MEM_ROM_BANK0[current_command_pos++];
			uint16_t tile_data = MEM_ROM_BANK0[current_command_pos++] | ((cmd2 & 0x03) << 8) | ((cmd2 & 0x0C) << 12);
			int16_t new_tpos_packed = (cmd2 >> 4) | ((cmd & 0x1F) << 4);
			unpack_and_set_tile_pos(new_tpos_packed);
			next_screen[tile_pos] = tile_data;
			increment_next_tile_pos();
		} else if (cmd <= 0xEF) {
			// place tile_data
			uint16_t tile_data = MEM_ROM_BANK0[current_command_pos++] | ((cmd & 0x03) << 8) | ((cmd & 0x0C) << 12);
			next_screen[tile_pos] = tile_data;
			increment_next_tile_pos();
		} else if (cmd >= 0xF8) {
			// end frame
			next_vblank_ticks = cmd & 0x7;
			break;
		} else if (cmd == 0xF1) {
			// switch bank
			current_command_bank++;
			current_command_pos = 0;
			set_rom_bank0(current_command_bank);
		} else if (cmd == 0xF7) {
			// set black border
			next_display_control = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(7);
		} else if (cmd == 0xF6) {
			// set white border
			next_display_control = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(0);
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

	video_set_gray_lut(GRAY_LUT_DEFAULT);
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

	next_dispctrl = DISPLAY_SCR2_ENABLE | DISPLAY_SCR2_WIN_INSIDE | DISPLAY_BORDER(7);

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
		outportw(IO_DISPLAY_CTRL, next_dispctrl);
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
	video_put_screen_map(curr_screen, asset_map(ASSET_END_CARD_MAP_BIN), 0, 0, 28, 18);

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