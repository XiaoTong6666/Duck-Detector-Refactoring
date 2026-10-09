#ifndef DUCKDETECTOR_PRELOAD_MNT_STRINGS_PROBE_H
#define DUCKDETECTOR_PRELOAD_MNT_STRINGS_PROBE_H

#include <cstddef>
#include <string>
#include <string_view>

namespace duckdetector::preload::mnt_strings {

struct Record {
    std::string source;
    std::string target;
    std::string filesystem;
    std::string options;
};

// Bionic getmntent_r() writes four NUL-separated strings into the TLS buffer.
// This parser is deliberately independent of libc/private TLS offsets.
bool parse_bionic_buffer(const char *bytes, std::size_t size, Record &out);
bool suspicious(const Record &record);
bool is_sensitive_partition(std::string_view path);
bool present_in_mountinfo(const Record &record, std::string_view mountinfo);

struct HistoricalSample {
    std::string status = "not_attempted";
    int error = 0;
    Record record;
};

// One bounded fork determines the same-thread TLS buffer address without reading
// the parent's TLS; process_vm_readv performs a checked, non-faulting self-read.
HistoricalSample sample_parent_tls();

}  // namespace duckdetector::preload::mnt_strings
#endif
