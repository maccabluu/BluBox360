#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#ifndef MAP_JIT
#define MAP_JIT 0x800
#endif

int main(void) {
#if !defined(__aarch64__)
  fprintf(stderr, "BluBox JIT probe requires Apple Silicon arm64.\n");
  return 2;
#else
  const long page_size = sysconf(_SC_PAGESIZE);
  if (page_size <= 0) {
    fprintf(stderr, "Could not read the macOS page size.\n");
    return 3;
  }

  void *page = mmap(NULL, (size_t)page_size,
                    PROT_READ | PROT_WRITE | PROT_EXEC,
                    MAP_PRIVATE | MAP_ANON | MAP_JIT,
                    -1, 0);
  if (page == MAP_FAILED) {
    fprintf(stderr, "MAP_JIT allocation failed: %s\n", strerror(errno));
    return 4;
  }

  // Apple AArch64 machine code:
  //   movz w0, #42
  //   ret
  const uint32_t code[] = {0x52800540u, 0xD65F03C0u};
  memcpy(page, code, sizeof(code));
  __builtin___clear_cache((char *)page, (char *)page + sizeof(code));

  typedef int (*jit_fn)(void);
  const int result = ((jit_fn)page)();
  munmap(page, (size_t)page_size);

  if (result != 42) {
    fprintf(stderr, "JIT code returned %d instead of 42.\n", result);
    return 5;
  }

  printf("BluBox Apple Silicon JIT host probe passed: generated arm64 code returned %d.\n",
         result);
  return 0;
#endif
}
