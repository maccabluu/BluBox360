#include <mach-o/dyld.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static bool starts_with(const char *value, const char *prefix) {
  return strncmp(value, prefix, strlen(prefix)) == 0;
}

static bool is_safe_option(const char *arg) {
  return starts_with(arg, "--gpu=") ||
         starts_with(arg, "--apu=") ||
         starts_with(arg, "--hid=") ||
         starts_with(arg, "--storage_root=") ||
         starts_with(arg, "--content_root=") ||
         starts_with(arg, "--cache_root=") ||
         starts_with(arg, "--framerate_limit=") ||
         starts_with(arg, "--vsync=") ||
         strcmp(arg, "--help") == 0 ||
         strcmp(arg, "-h") == 0;
}

static int executable_directory(char *buffer, size_t size) {
  uint32_t path_size = (uint32_t)size;
  if (_NSGetExecutablePath(buffer, &path_size) != 0) {
    return -1;
  }
  char *slash = strrchr(buffer, '/');
  if (!slash) {
    return -1;
  }
  *slash = '\0';
  return 0;
}

int main(int argc, char **argv) {
  if (argc == 2 && strcmp(argv[1], "--blubox-self-test") == 0) {
    puts("BluBox Xenia launch sanitizer ready");
    return 0;
  }

  char directory[PATH_MAX];
  if (executable_directory(directory, sizeof(directory)) != 0) {
    fprintf(stderr, "BluBox: unable to resolve Xenia engine directory.\n");
    return 126;
  }

  char real_executable[PATH_MAX];
  if (snprintf(real_executable, sizeof(real_executable), "%s/%s", directory,
               "xenia_edge.real") >= (int)sizeof(real_executable)) {
    fprintf(stderr, "BluBox: Xenia engine path is too long.\n");
    return 126;
  }

  char **forwarded = calloc((size_t)argc + 1, sizeof(char *));
  if (!forwarded) {
    fprintf(stderr, "BluBox: unable to allocate launch argument list.\n");
    return 126;
  }

  int out = 0;
  forwarded[out++] = real_executable;
  for (int i = 1; i < argc; ++i) {
    const char *arg = argv[i];
    if (arg[0] != '-' || is_safe_option(arg)) {
      forwarded[out++] = argv[i];
    } else {
      fprintf(stderr, "BluBox: ignored unsupported Xenia option: %s\n", arg);
    }
  }
  forwarded[out] = NULL;

  execv(real_executable, forwarded);
  perror("BluBox: unable to start Xenia engine");
  free(forwarded);
  return 126;
}
