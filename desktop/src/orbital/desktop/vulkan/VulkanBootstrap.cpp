#include <jni.h>
#include <windows.h>

#include <cstdint>
#include <cstdio>

#define VKAPI_CALL __stdcall

namespace{
    using VkInstance = void*;
    using VkPhysicalDevice = void*;

    constexpr uint32_t VK_STRUCTURE_TYPE_APPLICATION_INFO = 0;
    constexpr uint32_t VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1;
    constexpr uint32_t VK_API_VERSION_1_0 = 1u << 22;
    constexpr uint32_t VK_QUEUE_GRAPHICS_BIT = 1u;
    constexpr uint32_t VK_SUCCESS = 0;
    constexpr uint32_t VK_ERROR_INCOMPATIBLE_DRIVER = static_cast<uint32_t>(-9);

#pragma pack(push, 8)
    struct VkApplicationInfo{
        uint32_t sType;
        const void* pNext;
        const char* pApplicationName;
        uint32_t applicationVersion;
        const char* pEngineName;
        uint32_t engineVersion;
        uint32_t apiVersion;
    };

    struct VkInstanceCreateInfo{
        uint32_t sType;
        const void* pNext;
        uint32_t flags;
        const VkApplicationInfo* pApplicationInfo;
        uint32_t enabledLayerCount;
        const char* const* ppEnabledLayerNames;
        uint32_t enabledExtensionCount;
        const char* const* ppEnabledExtensionNames;
    };
#pragma pack(pop)

    using PFN_vkCreateInstance = uint32_t (VKAPI_CALL*)(const VkInstanceCreateInfo*, const void*, VkInstance*);
    using PFN_vkDestroyInstance = void (VKAPI_CALL*)(VkInstance, const void*);
    using PFN_vkEnumeratePhysicalDevices = uint32_t (VKAPI_CALL*)(VkInstance, uint32_t*, VkPhysicalDevice*);
    using PFN_vkGetPhysicalDeviceProperties = void (VKAPI_CALL*)(VkPhysicalDevice, void*);
    using PFN_vkGetPhysicalDeviceQueueFamilyProperties = void (VKAPI_CALL*)(VkPhysicalDevice, uint32_t*, void*);

    struct VulkanLoader{
        HMODULE library = nullptr;
        PFN_vkCreateInstance createInstance = nullptr;
        PFN_vkDestroyInstance destroyInstance = nullptr;
        PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = nullptr;
        PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = nullptr;
        PFN_vkGetPhysicalDeviceQueueFamilyProperties getPhysicalDeviceQueueFamilyProperties = nullptr;

        VulkanLoader(){
            library = LoadLibraryA("vulkan-1.dll");
            if(library == nullptr) return;
            createInstance = reinterpret_cast<PFN_vkCreateInstance>(GetProcAddress(library, "vkCreateInstance"));
            destroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(GetProcAddress(library, "vkDestroyInstance"));
            enumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(GetProcAddress(library, "vkEnumeratePhysicalDevices"));
            getPhysicalDeviceProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(GetProcAddress(library, "vkGetPhysicalDeviceProperties"));
            getPhysicalDeviceQueueFamilyProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(GetProcAddress(library, "vkGetPhysicalDeviceQueueFamilyProperties"));
        }

        bool valid() const{
            return createInstance != nullptr && destroyInstance != nullptr
                && enumeratePhysicalDevices != nullptr && getPhysicalDeviceProperties != nullptr
                && getPhysicalDeviceQueueFamilyProperties != nullptr;
        }
    };

    uint32_t majorVersion(uint32_t version){ return version >> 22; }
    uint32_t minorVersion(uint32_t version){ return (version >> 12) & 0x3ffu; }
    uint32_t patchVersion(uint32_t version){ return version & 0xfffu; }
}

extern "C" JNIEXPORT jstring JNICALL Java_orbital_desktop_vulkan_VulkanSupport_nativeProbe(
    JNIEnv* env, jclass){
    VulkanLoader vk;
    if(!vk.valid()) return nullptr;

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Orbital";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "Arc Vulkan backend";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = nullptr;
    const uint32_t result = vk.createInstance(&createInfo, nullptr, &instance);
    if(result != VK_SUCCESS || instance == nullptr){
        return nullptr;
    }

    jstring probe = nullptr;
    uint32_t deviceCount = 0;
    if(vk.enumeratePhysicalDevices(instance, &deviceCount, nullptr) == VK_SUCCESS && deviceCount > 0){
        VkPhysicalDevice* devices = new VkPhysicalDevice[deviceCount]{};
        if(vk.enumeratePhysicalDevices(instance, &deviceCount, devices) == VK_SUCCESS){
            for(uint32_t i = 0; i < deviceCount; i++){
                // VkPhysicalDeviceProperties is ~800 bytes; use an oversized aligned buffer.
                alignas(8) unsigned char properties[1024]{};
                vk.getPhysicalDeviceProperties(devices[i], properties);

                uint32_t queueFamilyCount = 0;
                vk.getPhysicalDeviceQueueFamilyProperties(devices[i], &queueFamilyCount, nullptr);
                bool graphicsQueue = false;
                if(queueFamilyCount > 0){
                    uint32_t* families = new uint32_t[queueFamilyCount * 8]{};
                    vk.getPhysicalDeviceQueueFamilyProperties(devices[i], &queueFamilyCount, families);
                    for(uint32_t q = 0; q < queueFamilyCount; q++){
                        if((families[q * 8] & VK_QUEUE_GRAPHICS_BIT) != 0){
                            graphicsQueue = true;
                            break;
                        }
                    }
                    delete[] families;
                }

                if(graphicsQueue){
                    const uint32_t apiVersion = *reinterpret_cast<const uint32_t*>(properties);
                    const char* deviceName = reinterpret_cast<const char*>(properties + 20);
                    char text[512];
                    std::snprintf(text, sizeof(text), "%s (Vulkan %u.%u.%u)", deviceName,
                        majorVersion(apiVersion), minorVersion(apiVersion), patchVersion(apiVersion));
                    probe = env->NewStringUTF(text);
                    break;
                }
            }
        }
        delete[] devices;
    }

    vk.destroyInstance(instance, nullptr);
    return probe;
}
