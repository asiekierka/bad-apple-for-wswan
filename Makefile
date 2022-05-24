ifndef WONDERFUL_TOOLCHAIN
$(error Please define WONDERFUL_TOOLCHAIN to point to the location of the Wonderful toolchain.)
endif
include $(WONDERFUL_TOOLCHAIN)/i8086/wswan.mk

TARGET := $(notdir $(shell pwd)).ws
OBJDIR := obj
RESDIR := res
SRCDIRS := src
MKDIRS := $(OBJDIR)
LIBS := -lws -lc -lgcc
CFLAGS += -O2

CSOURCES := $(foreach dir,$(SRCDIRS),$(notdir $(wildcard $(dir)/*.c)))
ASMSOURCES := $(foreach dir,$(SRCDIRS),$(notdir $(wildcard $(dir)/*.S)))
OBJECTS := $(CSOURCES:%.c=$(OBJDIR)/%.o) $(ASMSOURCES:%.S=$(OBJDIR)/%.o)
CFLAGS += -I$(OBJDIR)

DEPS := $(OBJECTS:.o=.d)
CFLAGS += -MMD -MP

vpath %.c $(SRCDIRS)
vpath %.S $(SRCDIRS)

.PHONY: all clean install

all: $(TARGET)

$(TARGET): $(OBJECTS) $(OBJDIR)/fs.bin
	$(SWANLINK) -v -o $@ -a $(OBJDIR)/fs.bin --output-elf $@.elf --linker-args $(LDFLAGS) $(WF_CRT0) $(OBJECTS) $(LIBS)

$(OBJDIR)/fs.bin: $(RESDIR)/tiles.bin $(RESDIR)/audio.bin $(RESDIR)/commands.bin $(RESDIR)/end_card.png fs.txt | $(OBJDIR)
	$(SUPERFAMICONV) tiles -i $(RESDIR)/end_card.png -M ws -p $(RESDIR)/palette.bin -d $(OBJDIR)/end_card.tiles.bin
	$(SUPERFAMICONV) map -i $(RESDIR)/end_card.png -M ws -p $(RESDIR)/palette.bin -t $(OBJDIR)/end_card.tiles.bin -d $(OBJDIR)/end_card.map.bin
	$(FSBANKPACK) -v -o $@ -d fs.txt

$(OBJDIR)/%.o: %.c $(OBJDIR)/fs.bin | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/%.o: %.S $(OBJDIR)/fs.bin | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR):
	$(info $(shell mkdir -p $(MKDIRS)))

clean:
	rm -r $(OBJDIR)/*
	rm $(TARGET) $(TARGET).elf

-include $(DEPS)
