// png.h for the browser: the page has decoded the app's PNGs already
// (site/hal.js) and copies the pixels in by path.
#include <png.h>

#include <stdlib.h>
#include <string.h>

#define SIM_IMPORT(name) __attribute__((import_module("sim"), import_name(#name)))

// Size of the image at path, or 0 if the page has none.
extern "C" SIM_IMPORT(image_size) int sim_image_size(const char* path, int* w, int* h);
// Copies its RGBA pixels (rows packed) into out.
extern "C" SIM_IMPORT(image_read) void sim_image_read(const char* path, void* out);

int png_image_begin_read_from_file(png_imagep image, const char* file_name) {
  int w, h;
  if (!sim_image_size(file_name, &w, &h)) return 0;
  image->width = w;
  image->height = h;
  image->opaque = strdup(file_name);
  return 1;
}

int png_image_finish_read(png_imagep image, const void*, void* buffer, int32_t, void*) {
  if (!image->opaque || image->format != PNG_FORMAT_RGBA) return 0;
  sim_image_read(static_cast<const char*>(image->opaque), buffer);
  png_image_free(image);
  return 1;
}

void png_image_free(png_imagep image) {
  free(image->opaque);
  image->opaque = nullptr;
}
