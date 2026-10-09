#ifndef DUCKDETECTOR_MEMORY_LEGACY_RUNTIME_DETECTOR_H
#define DUCKDETECTOR_MEMORY_LEGACY_RUNTIME_DETECTOR_H
#include "memory/common/types.h"
#include <vector>
namespace duckdetector::memory {
std::vector<Finding> inspect_legacy_runtime_state();
}
#endif
