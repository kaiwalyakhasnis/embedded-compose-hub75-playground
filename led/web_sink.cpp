// A matrix.h sink for the browser: the panel is the page's (site/panel.js).
// With backends/matrix/matrix.cpp this is the display half of ec_hal.h,
// exported to the page, which calls it for the Kotlin toolkit.
#include "matrix.h"

#define SIM_IMPORT(name) __attribute__((import_module("sim"), import_name(#name)))

extern "C" SIM_IMPORT(panel_size) int sim_panel_size(int* w, int* h);
extern "C" SIM_IMPORT(show) void sim_show(const uint8_t* rgb);

bool matrix_sink_init(int* width, int* height) { return sim_panel_size(width, height); }
void matrix_sink_show(const uint8_t* rgb) { sim_show(rgb); }
void matrix_sink_exit(void) {}

// Strings from the page (text, image names) are written here first.
static char g_str[4096];
extern "C" __attribute__((export_name("sim_str"))) char* sim_str(void) { return g_str; }
extern "C" __attribute__((export_name("sim_str_size"))) int sim_str_size(void) { return sizeof(g_str); }
