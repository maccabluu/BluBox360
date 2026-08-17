#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#ifndef MAP_JIT
#define MAP_JIT 0x800
#endif

static int jit_smoke_test(void) {
    const long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) {
        fprintf(stderr, "[core] Could not read macOS page size.\n");
        return 3;
    }

    int flags = MAP_PRIVATE | MAP_ANON;
#if defined(__aarch64__)
    flags |= MAP_JIT;
#endif

    void *page = mmap(NULL, (size_t)page_size,
                      PROT_READ | PROT_WRITE | PROT_EXEC,
                      flags, -1, 0);
    if (page == MAP_FAILED) {
        fprintf(stderr, "[core] JIT allocation failed: %s\n", strerror(errno));
        return 4;
    }

#if defined(__aarch64__)
    const uint32_t code[] = {0x52800540u, 0xD65F03C0u};
    memcpy(page, code, sizeof(code));
    __builtin___clear_cache((char *)page, (char *)page + sizeof(code));
#elif defined(__x86_64__)
    const unsigned char code[] = {0xB8, 0x2A, 0x00, 0x00, 0x00, 0xC3};
    memcpy(page, code, sizeof(code));
#else
    munmap(page, (size_t)page_size);
    fprintf(stderr, "[core] Unsupported host architecture.\n");
    return 5;
#endif

    typedef int (*jit_fn)(void);
    const int result = ((jit_fn)page)();
    munmap(page, (size_t)page_size);
    if (result != 42) {
        fprintf(stderr, "[core] JIT smoke test returned %d instead of 42.\n", result);
        return 6;
    }
    return 0;
}

static const char *env_or(const char *name, const char *fallback) {
    const char *value = getenv(name);
    return value && value[0] ? value : fallback;
}

static int supported_extension(const char *path) {
    const char *dot = strrchr(path, '.');
    if (!dot) return 0;
    return strcasecmp(dot, ".iso") == 0 ||
           strcasecmp(dot, ".xex") == 0 ||
           strcasecmp(dot, ".zar") == 0;
}

static void print_capabilities(void) {
#if defined(__aarch64__)
    const char *arch = "arm64";
#elif defined(__x86_64__)
    const char *arch = "x86_64";
#else
    const char *arch = "unknown";
#endif
    printf("{\"name\":\"BluBox 360 Native Core Bootstrap\",\"version\":\"2.3.0-preview\",\"host_arch\":\"%s\",\"jit_smoke_test\":true,\"xbox360_execution\":false}\n", arch);
}

int main(int argc, char **argv) {
    if (argc >= 2 && strcmp(argv[1], "--capabilities") == 0) {
        print_capabilities();
        return 0;
    }

    const int jit_result = jit_smoke_test();
    if (jit_result != 0) return jit_result;

    if (argc >= 2 && strcmp(argv[1], "--self-test") == 0) {
        printf("BluBox 360 native core bootstrap self-test passed.\n");
        return 0;
    }

    if (argc < 2) {
        fprintf(stderr, "Usage: blubox360-core <game.iso|game.xex|game.zar>\n");
        return 2;
    }

    const char *game_path = argv[1];
    struct stat st;
    if (stat(game_path, &st) != 0 || !S_ISREG(st.st_mode)) {
        fprintf(stderr, "[core] Game file was not found: %s\n", game_path);
        return 20;
    }
    if (!supported_extension(game_path)) {
        fprintf(stderr, "[core] Unsupported game file type. Use ISO, XEX or ZAR.\n");
        return 21;
    }

    printf("BluBox 360 macOS 2.3 native core bootstrap\n");
    printf("[core] Game: %s\n", game_path);
    printf("[core] Size: %lld bytes\n", (long long)st.st_size);
    printf("[core] Profile: %s\n", env_or("BLUBOX360_PROFILE", "Player 1"));
    printf("[core] FPS target: %s\n", env_or("BLUBOX360_TARGET_FPS", "60"));
    printf("[core] Graphics preset: %s\n", env_or("BLUBOX360_GRAPHICS_PRESET", "Balanced"));
    printf("[core] Render scale: %s\n", env_or("BLUBOX360_RENDER_SCALE", "Native"));
    printf("[core] Save path: %s\n", env_or("BLUBOX360_SAVE_PATH", "unset"));
    printf("[core] Shader cache: %s\n", env_or("BLUBOX360_SHADER_CACHE", "unset"));
    printf("[core] Host JIT smoke test passed.\n");
    printf("[core] Xbox 360 PowerPC execution and GPU rendering are not connected yet.\n");
    fflush(stdout);

    // Exit 64 tells the Swift frontend that the native bootstrap completed
    // successfully but full Xbox 360 execution is still the next milestone.
    return 64;
}
