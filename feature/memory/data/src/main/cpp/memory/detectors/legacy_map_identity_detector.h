#ifndef DUCKDETECTOR_MEMORY_LEGACY_MAP_IDENTITY_DETECTOR_H
#define DUCKDETECTOR_MEMORY_LEGACY_MAP_IDENTITY_DETECTOR_H

#include "memory/common/types.h"
#include <vector>

namespace duckdetector::memory {

// Migrated from NativeTest FindMemory, but with AOSP-dependent ambiguity retained
// as supporting evidence rather than treating inode churn as proof of injection.
std::vector<Finding> inspect_legacy_map_identities(const std::vector<MapEntry> &maps);

} // namespace duckdetector::memory
#endif
