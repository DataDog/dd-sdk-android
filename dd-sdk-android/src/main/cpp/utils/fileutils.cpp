#include <dirent.h>
#include <cerrno>
#include <sys/stat.h>
#include "fileutils.h"


void createDirIfNotExists(const char *dirPath) {
    opendir(dirPath);
    if (ENOENT == errno) {
        // directory does not exist. We will create it.
        mkdir(dirPath, S_IRWXU);
    }
}
