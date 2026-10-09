#include "preload/mnt_strings_probe.h"
#include <cassert>
#include <cerrno>
#include <string>

using namespace duckdetector::preload::mnt_strings;

int main() {
    constexpr char rawBytes[] = "magisk\0 /data/adb/modules\0 tmpfs\0 rw,relatime\0";
    const std::string raw(rawBytes, sizeof(rawBytes));
    Record record;
    assert(parse_bionic_buffer(raw.data(), raw.size(), record));
    assert(record.source == "magisk");
    assert(record.target == "/data/adb/modules");
    assert(record.filesystem == "tmpfs");
    assert(suspicious(record));
    const std::string visible = "42 40 0:5 / /data/adb/modules rw - tmpfs magisk rw\n";
    assert(present_in_mountinfo(record, visible));
    assert(!present_in_mountinfo(record, "43 40 0:5 / /data/adb/modulesx rw - tmpfs magisk rw\n"));
    assert(!present_in_mountinfo(record, "44 40 0:5 / /data/adb/modules rw - ext4 magisk rw\n"));
    assert(!present_in_mountinfo(record, "45 40 0:5 / /data/adb/modules rw - tmpfs tmpfs rw\n"));
    constexpr char malformedBytes[] = "root\0bad_target\0ext4\0rw\0";
    const std::string malformed(malformedBytes, sizeof(malformedBytes));
    assert(!parse_bionic_buffer(malformed.data(), malformed.size(), record));
    assert(!parse_bionic_buffer(nullptr, 0, record));
    assert(!parse_bionic_buffer("\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0", 16, record));
    assert(is_sensitive_partition("/system/bin"));
    assert(!is_sensitive_partition("/data/user"));
    HistoricalSample empty;
    assert(empty.status == "not_attempted");
    assert(empty.error == 0);
    return 0;
}
