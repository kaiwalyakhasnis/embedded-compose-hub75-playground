// The part of libpng's simplified API that backends/matrix/matrix.cpp uses,
// for the browser: the page decodes the PNGs (led/png_web.cpp asks it), so
// no libpng or file system is needed in the wasm module.
#ifndef SIM_PNG_H
#define SIM_PNG_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define PNG_IMAGE_VERSION 1
#define PNG_FORMAT_RGBA 3
#define PNG_IMAGE_SIZE(image) ((size_t)(image).width * (image).height * 4)

typedef struct {
  void* opaque;  // the path, between begin and finish
  uint32_t version;
  uint32_t width;
  uint32_t height;
  uint32_t format;
  uint32_t flags;
  uint32_t colormap_entries;
  uint32_t warning_or_error;
  char message[64];
} png_image, *png_imagep;

int png_image_begin_read_from_file(png_imagep image, const char* file_name);
int png_image_finish_read(png_imagep image, const void* background, void* buffer, int32_t row_stride, void* colormap);
void png_image_free(png_imagep image);

#ifdef __cplusplus
}
#endif

#endif
