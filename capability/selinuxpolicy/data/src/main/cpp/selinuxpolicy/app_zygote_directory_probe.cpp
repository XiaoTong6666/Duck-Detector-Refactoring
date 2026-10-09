#include <jni.h>
#include <cerrno>
#include <dirent.h>

// Probe only permission/first-entry semantics, never enumerate an entire directory
// while holding up the app zygote's preload lifecycle.
extern "C" JNIEXPORT jint JNICALL
Java_com_eltavine_duckdetector_capability_selinuxpolicy_data_AppZygoteMountPermissionProbe_nativeFirstDirectoryEntry(
        JNIEnv *env, jobject, jstring path) {
    if (path == nullptr) return -EINVAL;
    const char *name = env->GetStringUTFChars(path, nullptr);
    if (name == nullptr) return -ENOMEM;
    errno = 0;
    DIR *dir = opendir(name);
    const int openError = errno;
    env->ReleaseStringUTFChars(path, name);
    if (dir == nullptr) return -openError;
    errno = 0;
    const dirent *entry = readdir(dir);
    const int readError = errno;
    closedir(dir);
    if (entry != nullptr) return 1;
    return readError == 0 ? 0 : -readError;
}
