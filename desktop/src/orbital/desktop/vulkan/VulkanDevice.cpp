/*
 * Native Vulkan 1.0 rendering core for the desktop Arc backend port.
 *
 * The Vulkan loader (vulkan-1.dll) is resolved dynamically so no Vulkan SDK or
 * import library is required at build time. All device-level entry points are
 * resolved with vkGetDeviceProcAddr, which keeps this usable on loaders that
 * expose only the core 1.0 surface.
 *
 * The default JNI path creates an offscreen color image with pixel readback.
 * An opt-in windowed smoke path accepts explicit Win32 handles and presents clears.
 */
#define VK_USE_PLATFORM_WIN32_KHR
#define VK_NO_PROTOTYPES
#define NOMINMAX

#include <windows.h>
#include <vulkan/vulkan_core.h>
#include <vulkan/vulkan_win32.h>
#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <new>
#include <vector>

#define VK_LOAD_GLOBAL(name) api.name = reinterpret_cast<PFN_##name>(GetProcAddress(api.library, #name))
#define VK_LOAD_INSTANCE(name) api.name = reinterpret_cast<PFN_##name>(api.vkGetInstanceProcAddr(device.instance, #name))
#define VK_LOAD_DEVICE(name) api.name = reinterpret_cast<PFN_##name>(api.vkGetDeviceProcAddr(device.device, #name))

namespace{
    struct Api{
        HMODULE library = nullptr;
        PFN_vkGetInstanceProcAddr vkGetInstanceProcAddr = nullptr;
        PFN_vkGetDeviceProcAddr vkGetDeviceProcAddr = nullptr;

        PFN_vkCreateInstance vkCreateInstance = nullptr;
        PFN_vkDestroyInstance vkDestroyInstance = nullptr;
        PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = nullptr;
        PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties = nullptr;
        PFN_vkGetPhysicalDeviceQueueFamilyProperties vkGetPhysicalDeviceQueueFamilyProperties = nullptr;
        PFN_vkGetPhysicalDeviceMemoryProperties vkGetPhysicalDeviceMemoryProperties = nullptr;
        PFN_vkGetPhysicalDeviceFeatures vkGetPhysicalDeviceFeatures = nullptr;
        PFN_vkGetPhysicalDeviceFormatProperties vkGetPhysicalDeviceFormatProperties = nullptr;
        PFN_vkGetPhysicalDeviceImageFormatProperties vkGetPhysicalDeviceImageFormatProperties = nullptr;
        PFN_vkEnumerateDeviceExtensionProperties vkEnumerateDeviceExtensionProperties = nullptr;
        PFN_vkCreateDevice vkCreateDevice = nullptr;

        PFN_vkDestroyDevice vkDestroyDevice = nullptr;
        PFN_vkGetDeviceQueue vkGetDeviceQueue = nullptr;
        PFN_vkCreateCommandPool vkCreateCommandPool = nullptr;
        PFN_vkDestroyCommandPool vkDestroyCommandPool = nullptr;
        PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers = nullptr;
        PFN_vkFreeCommandBuffers vkFreeCommandBuffers = nullptr;
        PFN_vkBeginCommandBuffer vkBeginCommandBuffer = nullptr;
        PFN_vkEndCommandBuffer vkEndCommandBuffer = nullptr;
        PFN_vkCreateFence vkCreateFence = nullptr;
        PFN_vkDestroyFence vkDestroyFence = nullptr;
        PFN_vkWaitForFences vkWaitForFences = nullptr;
        PFN_vkResetFences vkResetFences = nullptr;
        // The vendored headers only ship the single-buffer reset, which is what
        // one re-recorded command buffer needs.
        PFN_vkResetCommandBuffer vkResetCommandBuffer = nullptr;
        PFN_vkQueueSubmit vkQueueSubmit = nullptr;
        PFN_vkQueueWaitIdle vkQueueWaitIdle = nullptr;
        PFN_vkCreateSemaphore vkCreateSemaphore = nullptr;
        PFN_vkDestroySemaphore vkDestroySemaphore = nullptr;

        PFN_vkCreateBuffer vkCreateBuffer = nullptr;
        PFN_vkDestroyBuffer vkDestroyBuffer = nullptr;
        PFN_vkGetBufferMemoryRequirements vkGetBufferMemoryRequirements = nullptr;
        PFN_vkAllocateMemory vkAllocateMemory = nullptr;
        PFN_vkFreeMemory vkFreeMemory = nullptr;
        PFN_vkBindBufferMemory vkBindBufferMemory = nullptr;
        PFN_vkMapMemory vkMapMemory = nullptr;
        PFN_vkUnmapMemory vkUnmapMemory = nullptr;
        PFN_vkCreateImage vkCreateImage = nullptr;
        PFN_vkDestroyImage vkDestroyImage = nullptr;
        PFN_vkGetImageMemoryRequirements vkGetImageMemoryRequirements = nullptr;
        PFN_vkBindImageMemory vkBindImageMemory = nullptr;
        PFN_vkCreateImageView vkCreateImageView = nullptr;
        PFN_vkDestroyImageView vkDestroyImageView = nullptr;
        PFN_vkCreateSampler vkCreateSampler = nullptr;
        PFN_vkDestroySampler vkDestroySampler = nullptr;
        PFN_vkCreateRenderPass vkCreateRenderPass = nullptr;
        PFN_vkDestroyRenderPass vkDestroyRenderPass = nullptr;
        PFN_vkCreateFramebuffer vkCreateFramebuffer = nullptr;
        PFN_vkDestroyFramebuffer vkDestroyFramebuffer = nullptr;
        PFN_vkCreateShaderModule vkCreateShaderModule = nullptr;
        PFN_vkDestroyShaderModule vkDestroyShaderModule = nullptr;
        PFN_vkCreateDescriptorSetLayout vkCreateDescriptorSetLayout = nullptr;
        PFN_vkDestroyDescriptorSetLayout vkDestroyDescriptorSetLayout = nullptr;
        PFN_vkCreatePipelineLayout vkCreatePipelineLayout = nullptr;
        PFN_vkDestroyPipelineLayout vkDestroyPipelineLayout = nullptr;
        PFN_vkCreateDescriptorPool vkCreateDescriptorPool = nullptr;
        PFN_vkDestroyDescriptorPool vkDestroyDescriptorPool = nullptr;
        PFN_vkAllocateDescriptorSets vkAllocateDescriptorSets = nullptr;
        PFN_vkUpdateDescriptorSets vkUpdateDescriptorSets = nullptr;
        PFN_vkCreateGraphicsPipelines vkCreateGraphicsPipelines = nullptr;
        PFN_vkDestroyPipeline vkDestroyPipeline = nullptr;
        PFN_vkCmdBeginRenderPass vkCmdBeginRenderPass = nullptr;
        PFN_vkCmdEndRenderPass vkCmdEndRenderPass = nullptr;
        PFN_vkCmdClearAttachments vkCmdClearAttachments = nullptr;
        PFN_vkCmdBindPipeline vkCmdBindPipeline = nullptr;
        PFN_vkCmdBindDescriptorSets vkCmdBindDescriptorSets = nullptr;
        PFN_vkCmdBindVertexBuffers vkCmdBindVertexBuffers = nullptr;
        PFN_vkCmdBindIndexBuffer vkCmdBindIndexBuffer = nullptr;
        PFN_vkCmdSetViewport vkCmdSetViewport = nullptr;
        PFN_vkCmdSetScissor vkCmdSetScissor = nullptr;
        PFN_vkCmdDraw vkCmdDraw = nullptr;
        PFN_vkCmdDrawIndexed vkCmdDrawIndexed = nullptr;
        PFN_vkCmdPipelineBarrier vkCmdPipelineBarrier = nullptr;
        PFN_vkCmdCopyImageToBuffer vkCmdCopyImageToBuffer = nullptr;
    PFN_vkCmdBlitImage vkCmdBlitImage = nullptr;
        PFN_vkCmdCopyBufferToImage vkCmdCopyBufferToImage = nullptr;

        // KHR surface + swapchain: probed, used only when a window is attached.
        PFN_vkCreateWin32SurfaceKHR vkCreateWin32SurfaceKHR = nullptr;
        PFN_vkDestroySurfaceKHR vkDestroySurfaceKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceSupportKHR vkGetPhysicalDeviceSurfaceSupportKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR vkGetPhysicalDeviceSurfaceCapabilitiesKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceFormatsKHR vkGetPhysicalDeviceSurfaceFormatsKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfacePresentModesKHR vkGetPhysicalDeviceSurfacePresentModesKHR = nullptr;
        PFN_vkCreateSwapchainKHR vkCreateSwapchainKHR = nullptr;
        PFN_vkDestroySwapchainKHR vkDestroySwapchainKHR = nullptr;
        PFN_vkGetSwapchainImagesKHR vkGetSwapchainImagesKHR = nullptr;
        PFN_vkAcquireNextImageKHR vkAcquireNextImageKHR = nullptr;
        PFN_vkQueuePresentKHR vkQueuePresentKHR = nullptr;
    } api;

    /** One vertex attribute inside a pipeline's vertex input. */
    struct Attribute{
        uint32_t location;
        VkFormat format;
        uint32_t offset;
    };

    /**
     * One user-visible framebuffer: a color image used as a color attachment and, on demand, as a
     * sampled image, plus an optional depth(-stencil) attachment.
     *
     * <p>Color images of all targets share {@link Device::colorFormat} so the two target render
     * passes (with and without depth) stay compatible for pipelines no matter which target is
     * bound. {@code colorLayout} tracks the last transition, so sampling and re-rendering move the
     * image between {@code COLOR_ATTACHMENT_OPTIMAL} and {@code SHADER_READ_ONLY_OPTIMAL}
     * correctly.</p>
     */
    struct RenderTarget{
        bool inUse = false;
        uint32_t width = 0, height = 0;
        bool hasDepth = false;
        bool hasStencil = false;
        VkImage colorImage = VK_NULL_HANDLE;
        VkDeviceMemory colorMemory = VK_NULL_HANDLE;
        VkImageView colorView = VK_NULL_HANDLE;
        VkImage depthImage = VK_NULL_HANDLE;
        VkDeviceMemory depthMemory = VK_NULL_HANDLE;
        VkImageView depthView = VK_NULL_HANDLE;
        VkRenderPass pass = VK_NULL_HANDLE;
        VkFramebuffer framebuffer = VK_NULL_HANDLE;
        bool rendered = false;
        VkImageLayout colorLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    };

    struct Device{
        bool initialized = false;
        char lastError[512]{};
        VkInstance instance = VK_NULL_HANDLE;
        VkPhysicalDevice physical = VK_NULL_HANDLE;
        VkPhysicalDeviceMemoryProperties memoryProperties{};
        VkDevice device = VK_NULL_HANDLE;
        VkQueue queue = VK_NULL_HANDLE;
        uint32_t queueFamily = 0;
        bool hasSwapchainExtension = false;
        bool hasWin32Surface = false;
        VkCommandPool commandPool = VK_NULL_HANDLE;
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
        VkSemaphore acquireSemaphore = VK_NULL_HANDLE;
        VkSemaphore renderSemaphore = VK_NULL_HANDLE;

        uint32_t width = 0, height = 0;
        char deviceName[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE]{};
        bool colorInitialized = false;
        VkFormat colorFormat = VK_FORMAT_R8G8B8A8_UNORM;
        VkFormat windowFormat = VK_FORMAT_B8G8R8A8_UNORM;
        VkFormat depthFormat = VK_FORMAT_D24_UNORM_S8_UINT;
        bool hasDepth = false;

        // Offscreen target.
        VkImage colorImage = VK_NULL_HANDLE;
        VkDeviceMemory colorMemory = VK_NULL_HANDLE;
        VkImageView colorView = VK_NULL_HANDLE;
        VkImage depthImage = VK_NULL_HANDLE;
        VkDeviceMemory depthMemory = VK_NULL_HANDLE;
        VkImageView depthView = VK_NULL_HANDLE;
        VkRenderPass offscreenPass = VK_NULL_HANDLE;
        VkFramebuffer framebuffer = VK_NULL_HANDLE;
        bool offscreenPassHasDepth = false;

        // Window target.
        bool windowed = false;
        VkSurfaceKHR surface = VK_NULL_HANDLE;
        VkSwapchainKHR swapchain = VK_NULL_HANDLE;
        std::vector<VkImage> swapImages;
        std::vector<VkImageView> swapViews;
        std::vector<VkFramebuffer> swapFramebuffers;
        VkRenderPass windowedPass = VK_NULL_HANDLE;
        bool windowedPassHasDepth = false;
        VkFormat windowedPassFormat = VK_FORMAT_UNDEFINED;
        uint32_t imageIndex = 0;
        bool frameOpen = false;
        bool vSync = true;

        VkBuffer vertexBuffer = VK_NULL_HANDLE;
        VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
        VkDeviceSize vertexCapacity = 0;
        VkDeviceSize vertexBytes = 0;
        void* vertexMapped = nullptr;
        // Bytes per vertex of the active pipeline; 6 floats is the historical
        // position+colour layout used by the self-check and bridge tests.
        uint32_t vertexStride = 6 * sizeof(float);

        VkBuffer indexBuffer = VK_NULL_HANDLE;
        VkDeviceMemory indexMemory = VK_NULL_HANDLE;
        VkDeviceSize indexCapacity = 0;
        VkDeviceSize indexBytes = 0;
        void* indexMapped = nullptr;

        VkBuffer stagingBuffer = VK_NULL_HANDLE;
        VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
        void* stagingMapped = nullptr;
        static constexpr VkDeviceSize stagingCapacity = 1 << 20;

        VkBuffer uniformBuffer = VK_NULL_HANDLE;
        VkDeviceMemory uniformMemory = VK_NULL_HANDLE;
        void* uniformMapped = nullptr;
        VkDeviceSize uniformRange = 0;
        static constexpr VkDeviceSize uniformCapacity = 65536;

        VkBuffer readbackBuffer = VK_NULL_HANDLE;
        VkDeviceMemory readbackMemory = VK_NULL_HANDLE;
        VkDeviceSize readbackCapacity = 0;
        void* readbackMapped = nullptr;

        VkImage textureImage = VK_NULL_HANDLE;
        VkDeviceMemory textureMemory = VK_NULL_HANDLE;
        VkImageView textureView = VK_NULL_HANDLE;
        VkSampler sampler = VK_NULL_HANDLE;
        bool samplerLinear = false;

        VkDescriptorSetLayout descriptorLayout = VK_NULL_HANDLE;
        VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
        VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
        VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
        VkPipeline pipeline = VK_NULL_HANDLE;

        VkClearColorValue clearColor{{0.0f, 0.0f, 0.0f, 1.0f}};
        VkViewport viewport{};
        VkRect2D scissor{};

        // User-visible framebuffer objects, plus the default offscreen target. activeTarget == -1
        // means "the default offscreen target", which is what create()/render() have always used.
        std::vector<RenderTarget> targets;
        int activeTarget = -1;
        VkRenderPass targetPassNoDepth = VK_NULL_HANDLE;
        VkRenderPass targetPassWithDepth = VK_NULL_HANDLE;
        // Which image the combined-image-sampler descriptor points at: 0 = the uploaded texture,
        // 1 = the color image of a render target (see sampleFramebuffer).
        int sampledKind = 0;
        int sampledTarget = 0;
        VkFormat targetDepthFormat = VK_FORMAT_UNDEFINED;
        float clearDepth = 1.0f;
        int clearStencil = 0;
        VkPhysicalDeviceFeatures features{};
        bool supportsWideLines = false;
    } device;

    /*
     * Forward declarations for the render-target helpers. They are defined further down and are
     * used by several call sites, so the order of definitions must not matter.
     */
    void destroyDefaultDepthTarget();
    bool createDefaultDepthTarget(uint32_t width, uint32_t height);
    VkFormat chooseDepthFormat(bool stencil, bool& stencilOut);
    VkImageAspectFlags depthAspect(VkFormat format);
    bool writeDescriptorImage();
    bool sampleUploadedTexture();
    bool sampleFramebuffer(int id);
    bool bindFramebuffer(int id);
    bool allocateRenderTarget(RenderTarget& target, uint32_t width, uint32_t height, bool depth, bool stencil);
    VkRenderPass targetPass(bool withDepth);
    void destroyRenderTargets();
    RenderTarget* activeRenderTarget();
    VkRenderPass activeRenderPass();
    bool beginCommand();
    bool submitCommand(VkSemaphore wait, VkSemaphore signal);
    bool requireInitialized();
    void invalidatePipeline();

    void setError(const char* what, VkResult result){
        std::snprintf(device.lastError, sizeof(device.lastError), "%s (VkResult %d)", what, static_cast<int>(result));
    }

    /** The pipeline is bound to one render pass: changing the pass drops it. */
    void invalidatePipeline();

    bool failed(VkResult result, const char* what){
        if(result == VK_SUCCESS) return false;
        setError(what, result);
        return true;
    }

    /**
     * Maps a GL primitive enum (arc.graphics.GL20) to a Vulkan topology. GL_LINE_LOOP has no
     * Vulkan equivalent, so it is rejected rather than silently drawn as a line strip.
     */
    bool topologyFromGL(int primitive, VkPrimitiveTopology& topology){
        switch(primitive){
            case 0: topology = VK_PRIMITIVE_TOPOLOGY_POINT_LIST; return true; // GL_POINTS
            case 1: topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST; return true; // GL_LINES
            case 3: topology = VK_PRIMITIVE_TOPOLOGY_LINE_STRIP; return true; // GL_LINE_STRIP
            case 4: topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST; return true; // GL_TRIANGLES
            case 5: topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP; return true; // GL_TRIANGLE_STRIP
            case 6: topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN; return true; // GL_TRIANGLE_FAN
            default: return false;
        }
    }

    /** Vertex attribute type codes used by the Java layout encoding. */
    VkFormat attributeFormat(int code, bool& ok){
        ok = true;
        switch(code){
            case 1: return VK_FORMAT_R32_SFLOAT;
            case 2: return VK_FORMAT_R32G32_SFLOAT;
            case 3: return VK_FORMAT_R32G32B32_SFLOAT;
            case 4: return VK_FORMAT_R32G32B32A32_SFLOAT;
            default: ok = false; return VK_FORMAT_UNDEFINED;
        }
    }

    uint32_t findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags properties){
        for(uint32_t i = 0; i < device.memoryProperties.memoryTypeCount; i++){
            if((typeBits & (1u << i)) && (device.memoryProperties.memoryTypes[i].propertyFlags & properties) == properties) return i;
        }
        return UINT32_MAX;
    }

    bool createBuffer(VkDeviceSize size, VkBufferUsageFlags usage, VkBuffer& buffer,
                      VkDeviceMemory& memory, void** mapped){
        VkBufferCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        info.size = size;
        info.usage = usage;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if(failed(api.vkCreateBuffer(device.device, &info, nullptr, &buffer), "vkCreateBuffer")) return false;

        VkMemoryRequirements requirements{};
        api.vkGetBufferMemoryRequirements(device.device, buffer, &requirements);
        uint32_t type = findMemoryType(requirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if(type == UINT32_MAX){
            std::snprintf(device.lastError, sizeof(device.lastError), "no host-visible coherent memory type");
            return false;
        }

        VkMemoryAllocateInfo allocation{};
        allocation.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = type;
        if(failed(api.vkAllocateMemory(device.device, &allocation, nullptr, &memory), "vkAllocateMemory")) return false;
        if(failed(api.vkBindBufferMemory(device.device, buffer, memory, 0), "vkBindBufferMemory")) return false;
        if(mapped && failed(api.vkMapMemory(device.device, memory, 0, size, 0, mapped), "vkMapMemory")) return false;
        return true;
    }

    bool createImage(uint32_t width, uint32_t height, VkFormat format, VkImageUsageFlags usage,
                     VkImage& image, VkDeviceMemory& memory){
        VkImageCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        info.imageType = VK_IMAGE_TYPE_2D;
        info.format = format;
        info.extent = {width, height, 1};
        info.mipLevels = 1;
        info.arrayLayers = 1;
        info.samples = VK_SAMPLE_COUNT_1_BIT;
        info.tiling = VK_IMAGE_TILING_OPTIMAL;
        info.usage = usage;
        info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        if(failed(api.vkCreateImage(device.device, &info, nullptr, &image), "vkCreateImage")) return false;

        VkMemoryRequirements requirements{};
        api.vkGetImageMemoryRequirements(device.device, image, &requirements);
        uint32_t type = findMemoryType(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if(type == UINT32_MAX) type = findMemoryType(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        if(type == UINT32_MAX){
            std::snprintf(device.lastError, sizeof(device.lastError), "no suitable image memory type");
            return false;
        }
        VkMemoryAllocateInfo allocation{};
        allocation.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = type;
        if(failed(api.vkAllocateMemory(device.device, &allocation, nullptr, &memory), "vkAllocateMemory")) return false;
        return !failed(api.vkBindImageMemory(device.device, image, memory, 0), "vkBindImageMemory");
    }

    VkImageView createImageView(VkImage image, VkFormat format, VkImageAspectFlags aspect){
        VkImageViewCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        info.image = image;
        info.viewType = VK_IMAGE_VIEW_TYPE_2D;
        info.format = format;
        info.subresourceRange = {aspect, 0, 1, 0, 1};
        VkImageView view = VK_NULL_HANDLE;
        if(failed(api.vkCreateImageView(device.device, &info, nullptr, &view), "vkCreateImageView")) return VK_NULL_HANDLE;
        return view;
    }

    bool createSampler(bool linear){
        if(device.sampler != VK_NULL_HANDLE){
            api.vkDestroySampler(device.device, device.sampler, nullptr);
            device.sampler = VK_NULL_HANDLE;
        }
        VkFilter filter = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        VkSamplerCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        info.magFilter = filter;
        info.minFilter = filter;
        info.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        info.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        info.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        info.borderColor = VK_BORDER_COLOR_INT_OPAQUE_WHITE;
        if(failed(api.vkCreateSampler(device.device, &info, nullptr, &device.sampler), "vkCreateSampler")) return false;
        device.samplerLinear = linear;
        return true;
    }

    void destroyDepthTarget();

    /** Depth aspect bits for a depth or depth-stencil format. */
    VkImageAspectFlags depthAspect(VkFormat format){
        if(format == VK_FORMAT_D32_SFLOAT || format == VK_FORMAT_D16_UNORM) return VK_IMAGE_ASPECT_DEPTH_BIT;
        return VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT;
    }

    /**
     * Picks a depth (or depth-stencil) format this device can render to. Returns VK_FORMAT_UNDEFINED
     * when no candidate is usable, which callers treat as "no depth attachment".
     */
    VkFormat chooseDepthFormat(bool stencil, bool& stencilOut){
        stencilOut = false;
        const VkFormat withStencil[] = {VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT};
        const VkFormat depthOnly[] = {VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM};

        for(const VkFormat format : (stencil ? withStencil : depthOnly)){
            if(api.vkGetPhysicalDeviceFormatProperties == nullptr) break;
            VkFormatProperties properties{};
            api.vkGetPhysicalDeviceFormatProperties(device.physical, format, &properties);
            if((properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0){
                stencilOut = format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
                return format;
            }
        }
        return VK_FORMAT_UNDEFINED;
    }

    /**
     * GL numbers window coordinates upwards and Vulkan numbers framebuffer coordinates downwards,
     * so an unflipped viewport mirrors the image. A negative viewport height is the standard,
     * extension-free way to emulate GL on Vulkan: NDC y = +1 then lands on framebuffer row 0,
     * which is the row a readback copies first.
     */
    VkViewport glViewport(uint32_t width, uint32_t height){
        VkViewport viewport{};
        viewport.x = 0.0f;
        viewport.y = static_cast<float>(height);
        viewport.width = static_cast<float>(width);
        viewport.height = -static_cast<float>(height);
        viewport.minDepth = 0.0f;
        viewport.maxDepth = 1.0f;
        return viewport;
    }

    bool createDefaultDepthTarget(uint32_t width, uint32_t height){
        destroyDefaultDepthTarget();
        bool stencilOut = false;
        // Depth-stencil is preferred for the default target: the GL layer may ask for stencil.
        VkFormat format = chooseDepthFormat(true, stencilOut);
        if(format == VK_FORMAT_UNDEFINED) format = chooseDepthFormat(false, stencilOut);
        if(format == VK_FORMAT_UNDEFINED){
            std::snprintf(device.lastError, sizeof(device.lastError), "no usable depth format");
            device.hasDepth = false;
            return false;
        }
        device.depthFormat = format;
        if(!createImage(width, height, format, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                device.depthImage, device.depthMemory)) return false;
        device.depthView = createImageView(device.depthImage, format, depthAspect(format));
        device.hasDepth = device.depthView != VK_NULL_HANDLE;
        return device.hasDepth;
    }

    void destroyDepthTarget(){
        VkDevice d = device.device;
        if(device.depthView != VK_NULL_HANDLE) api.vkDestroyImageView(d, device.depthView, nullptr);
        if(device.depthImage != VK_NULL_HANDLE) api.vkDestroyImage(d, device.depthImage, nullptr);
        if(device.depthMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.depthMemory, nullptr);
        device.depthView = VK_NULL_HANDLE;
        device.depthImage = VK_NULL_HANDLE;
        device.depthMemory = VK_NULL_HANDLE;
        device.hasDepth = false;
    }

    /**
     * Render pass for one color format, ending in the layout the consumer expects.
     *
     * @param depth VK_FORMAT_UNDEFINED omits the depth-stencil attachment
     */
    bool createRenderPass(VkFormat color, VkFormat depth, VkImageLayout finalLayout, VkRenderPass& pass, bool preserveColor = false){
        VkAttachmentDescription attachments[2]{};
        attachments[0].format = color;
        attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
        attachments[0].loadOp = preserveColor ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[0].initialLayout = preserveColor ? VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED;
        attachments[0].finalLayout = finalLayout;

        uint32_t attachmentCount = 1;
        VkAttachmentReference colorReference{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkAttachmentReference depthReference{1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL};

        if(depth != VK_FORMAT_UNDEFINED){
            attachments[1].format = depth;
            attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
            attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
            attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
            attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
            attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            attachmentCount = 2;
        }

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorReference;
        if(depth != VK_FORMAT_UNDEFINED) subpass.pDepthStencilAttachment = &depthReference;

        VkSubpassDependency dependencies[2]{};
        dependencies[0].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependencies[0].dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependencies[0].srcAccessMask = 0;
        dependencies[0].dstAccessMask = preserveColor
            ? VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        dependencies[0].dependencyFlags = VK_DEPENDENCY_BY_REGION_BIT;
        dependencies[1].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependencies[1].dstStageMask = finalLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
            ? VK_PIPELINE_STAGE_TRANSFER_BIT : VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        dependencies[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        dependencies[1].dstAccessMask = finalLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
            ? VK_ACCESS_TRANSFER_READ_BIT : VK_ACCESS_SHADER_READ_BIT;
        dependencies[1].dependencyFlags = finalLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
            ? 0 : VK_DEPENDENCY_BY_REGION_BIT;

        VkRenderPassCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        info.attachmentCount = attachmentCount;
        info.pAttachments = attachments;
        info.subpassCount = 1;
        info.pSubpasses = &subpass;
        info.dependencyCount = 2;
        info.pDependencies = dependencies;
        return !failed(api.vkCreateRenderPass(device.device, &info, nullptr, &pass), "vkCreateRenderPass");
    }

    bool createFramebuffer(VkRenderPass pass, VkImageView color, VkImageView depth, uint32_t width,
                           uint32_t height, VkFramebuffer& out){
        VkImageView attachments[2] = {color, depth};
        VkFramebufferCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        info.renderPass = pass;
        info.attachmentCount = depth != VK_NULL_HANDLE ? 2u : 1u;
        info.pAttachments = attachments;
        info.width = width;
        info.height = height;
        info.layers = 1;
        return !failed(api.vkCreateFramebuffer(device.device, &info, nullptr, &out), "vkCreateFramebuffer");
    }

    void destroyDefaultDepthTarget(){
        VkDevice d = device.device;
        if(device.depthView != VK_NULL_HANDLE) api.vkDestroyImageView(d, device.depthView, nullptr);
        if(device.depthImage != VK_NULL_HANDLE) api.vkDestroyImage(d, device.depthImage, nullptr);
        if(device.depthMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.depthMemory, nullptr);
        device.depthView = VK_NULL_HANDLE;
        device.depthImage = VK_NULL_HANDLE;
        device.depthMemory = VK_NULL_HANDLE;
        device.hasDepth = false;
    }

    /** Aspect/transition helper for one color image, used by both default and user targets. */
    void transitionColor(VkImage image, VkImageLayout oldLayout, VkImageLayout newLayout,
                         VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage){
        VkImageMemoryBarrier barrier{};
        barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        barrier.oldLayout = oldLayout;
        barrier.newLayout = newLayout;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = image;
        barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            ? VK_ACCESS_SHADER_READ_BIT
            : VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        api.vkCmdPipelineBarrier(device.commandBuffer, srcStage, dstStage, 0, 0, nullptr, 0, nullptr, 1, &barrier);
    }

    /** Releases every user framebuffer object; the default offscreen target is untouched. */
    void destroyRenderTargets(){
        for(RenderTarget& target : device.targets){
            if(!target.inUse && target.colorImage == VK_NULL_HANDLE) continue;
            VkDevice d = device.device;
            if(d == VK_NULL_HANDLE) continue;
            if(target.framebuffer != VK_NULL_HANDLE) api.vkDestroyFramebuffer(d, target.framebuffer, nullptr);
            if(target.colorView != VK_NULL_HANDLE) api.vkDestroyImageView(d, target.colorView, nullptr);
            if(target.colorImage != VK_NULL_HANDLE) api.vkDestroyImage(d, target.colorImage, nullptr);
            if(target.colorMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, target.colorMemory, nullptr);
            if(target.depthView != VK_NULL_HANDLE) api.vkDestroyImageView(d, target.depthView, nullptr);
            if(target.depthImage != VK_NULL_HANDLE) api.vkDestroyImage(d, target.depthImage, nullptr);
            if(target.depthMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, target.depthMemory, nullptr);
            target = RenderTarget{};
        }
        if(device.targetPassNoDepth != VK_NULL_HANDLE && api.vkDestroyRenderPass != nullptr){
            api.vkDestroyRenderPass(device.device, device.targetPassNoDepth, nullptr);
        }
        if(device.targetPassWithDepth != VK_NULL_HANDLE && api.vkDestroyRenderPass != nullptr){
            api.vkDestroyRenderPass(device.device, device.targetPassWithDepth, nullptr);
        }
        device.targetPassNoDepth = VK_NULL_HANDLE;
        device.targetPassWithDepth = VK_NULL_HANDLE;
        device.activeTarget = -1;
        device.sampledKind = 0;
        device.sampledTarget = 0;
    }

    /** Render pass shared by every target with the same depth-ness. */
    VkRenderPass targetPass(bool withDepth){
        if(withDepth){
            if(device.targetDepthFormat == VK_FORMAT_UNDEFINED){
                bool stencilOut = false;
                device.targetDepthFormat = chooseDepthFormat(true, stencilOut);
                if(device.targetDepthFormat == VK_FORMAT_UNDEFINED) return VK_NULL_HANDLE;
            }
            if(device.targetPassWithDepth == VK_NULL_HANDLE && !createRenderPass(device.colorFormat,
                    device.targetDepthFormat, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    device.targetPassWithDepth, true)){
                return VK_NULL_HANDLE;
            }
            return device.targetPassWithDepth;
        }
        if(device.targetPassNoDepth == VK_NULL_HANDLE && !createRenderPass(device.colorFormat,
                VK_FORMAT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                device.targetPassNoDepth, true)){
            return VK_NULL_HANDLE;
        }
        return device.targetPassNoDepth;
    }

    /**
     * Allocates one user framebuffer object. Color always exists; depth (and stencil) only when
     * asked for and only in a format the device supports, so a device without usable depth formats
     * still gets a color-only framebuffer instead of a failure.
     */
    bool allocateRenderTarget(RenderTarget& target, uint32_t width, uint32_t height, bool depth, bool stencil){
        target = RenderTarget{};
        target.inUse = true;
        target.width = width;
        target.height = height;

        if(!createImage(width, height, device.colorFormat,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                    | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                target.colorImage, target.colorMemory)) return false;
        target.colorView = createImageView(target.colorImage, device.colorFormat, VK_IMAGE_ASPECT_COLOR_BIT);
        if(target.colorView == VK_NULL_HANDLE) return false;

        VkFormat depthFormat = VK_FORMAT_UNDEFINED;
        if(depth){
            bool stencilOut = false;
            depthFormat = chooseDepthFormat(stencil, stencilOut);
            if(depthFormat == VK_FORMAT_UNDEFINED) depthFormat = chooseDepthFormat(false, stencilOut);
            if(depthFormat == VK_FORMAT_UNDEFINED){
                //Depth is a quality feature here: the framebuffer stays usable without it.
                std::snprintf(device.lastError, sizeof(device.lastError),
                    "no usable depth format; framebuffer created without depth");
                target.hasDepth = false;
            }else{
                target.hasDepth = true;
                target.hasStencil = stencilOut;
                if(!createImage(width, height, depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                        target.depthImage, target.depthMemory)) return false;
                target.depthView = createImageView(target.depthImage, depthFormat, depthAspect(depthFormat));
                if(target.depthView == VK_NULL_HANDLE) return false;
            }
        }

        target.pass = targetPass(target.hasDepth);
        if(target.pass == VK_NULL_HANDLE) return false;
        return createFramebuffer(target.pass, target.colorView, target.depthView, width, height, target.framebuffer);
    }

    /** Render pass of the currently bound target. */
    VkRenderPass activeRenderPass(){
        if(device.activeTarget < 0 || static_cast<size_t>(device.activeTarget) >= device.targets.size()){
            return device.windowed ? device.windowedPass : device.offscreenPass;
        }
        return device.targets[device.activeTarget].pass;
    }

    RenderTarget* activeRenderTarget(){
        if(device.activeTarget <= 0 || static_cast<size_t>(device.activeTarget) > device.targets.size()) return nullptr;
        return &device.targets[device.activeTarget - 1];
    }

    /** Binds a user framebuffer, or the default offscreen target when {@code id <= 0}. */
    bool bindFramebuffer(int id){
        if(!requireInitialized()) return false;
        if(id <= 0){
            if(device.activeTarget != -1){
                device.activeTarget = -1;
                //The default offscreen target uses its own render pass object.
                invalidatePipeline();
            }
            device.sampledKind = 0;
            device.sampledTarget = 0;
            writeDescriptorImage();
            return true;
        }
        if(static_cast<size_t>(id) > device.targets.size() || !device.targets[id - 1].inUse){
            std::snprintf(device.lastError, sizeof(device.lastError), "framebuffer %d does not exist", id);
            return false;
        }
        RenderTarget& target = device.targets[id - 1];
        VkRenderPass previousPass = activeRenderPass();
        device.activeTarget = id;
        if(previousPass != target.pass) invalidatePipeline();
        if(device.sampledKind == 1 && device.sampledTarget != id){
            device.sampledKind = 0;
            device.sampledTarget = 0;
            writeDescriptorImage();
        }
        return true;
    }

    /** Points the sampler descriptor back at the uploaded texture image. */
    bool sampleUploadedTexture(){
        if(device.sampledKind == 0) return true;
        device.sampledKind = 0;
        device.sampledTarget = 0;
        return writeDescriptorImage();
    }

    /**
     * Points the sampler descriptor at the color image of a rendered framebuffer, or back at the
     * uploaded texture when {@code id <= 0}. Sampling a framebuffer that was never rendered is
     * rejected instead of returning undefined contents.
     */
    bool sampleFramebuffer(int id){
        if(!requireInitialized()) return false;
        if(id <= 0) return sampleUploadedTexture();

        if(static_cast<size_t>(id) > device.targets.size() || !device.targets[id - 1].inUse){
            std::snprintf(device.lastError, sizeof(device.lastError), "framebuffer %d does not exist", id);
            return false;
        }
        RenderTarget& target = device.targets[id - 1];
        if(!target.rendered){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "framebuffer %d has no rendered contents to sample (render to it first)", id);
            return false;
        }

        if(!beginCommand()) return false;
        transitionColor(target.colorImage, target.colorLayout, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            target.colorLayout == VK_IMAGE_LAYOUT_UNDEFINED ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                                                            : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        target.colorLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return false;

        device.sampledKind = 1;
        device.sampledTarget = id;
        return writeDescriptorImage();
    }

    void transition(VkImage image, VkImageLayout oldLayout, VkImageLayout newLayout,
                    VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                    VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage){
        VkImageMemoryBarrier barrier{};
        barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        barrier.oldLayout = oldLayout;
        barrier.newLayout = newLayout;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = image;
        barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        barrier.srcAccessMask = srcAccess;
        barrier.dstAccessMask = dstAccess;
        api.vkCmdPipelineBarrier(device.commandBuffer, srcStage, dstStage, 0, 0, nullptr, 0, nullptr, 1, &barrier);
    }

    bool beginCommand(){
        if(failed(api.vkResetFences(device.device, 1, &device.fence), "vkResetFences")) return false;
        // The command buffer is re-recorded every frame; recording a submitted buffer without
        // resetting it first is a spec violation (flagged by the validation layers).
        if(failed(api.vkResetCommandBuffer(device.commandBuffer, 0), "vkResetCommandBuffer")) return false;
        VkCommandBufferBeginInfo info{};
        info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        info.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        return !failed(api.vkBeginCommandBuffer(device.commandBuffer, &info), "vkBeginCommandBuffer");
    }

    bool submitCommand(VkSemaphore wait, VkSemaphore signal){
        if(failed(api.vkEndCommandBuffer(device.commandBuffer), "vkEndCommandBuffer")) return false;
        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{};
        submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &device.commandBuffer;
        if(wait != VK_NULL_HANDLE){
            submit.waitSemaphoreCount = 1;
            submit.pWaitSemaphores = &wait;
            submit.pWaitDstStageMask = &waitStage;
        }
        if(signal != VK_NULL_HANDLE){
            submit.signalSemaphoreCount = 1;
            submit.pSignalSemaphores = &signal;
        }
        if(failed(api.vkQueueSubmit(device.queue, 1, &submit, device.fence), "vkQueueSubmit")) return false;
        return !failed(api.vkWaitForFences(device.device, 1, &device.fence, VK_TRUE, UINT64_MAX), "vkWaitForFences");
    }

    /**
     * Re-points the combined-image-sampler descriptor. Default target: the uploaded texture. User
     * framebuffer: its color image once it has been rendered, in SHADER_READ_ONLY_OPTIMAL.
     */
    bool writeDescriptorImage(){
        if(device.descriptorSet == VK_NULL_HANDLE) return true;
        VkDescriptorImageInfo imageInfo{};

        if(device.sampledKind == 1 && device.sampledTarget > 0
                && static_cast<size_t>(device.sampledTarget) <= device.targets.size()){
            const RenderTarget& target = device.targets[device.sampledTarget - 1];
            imageInfo.sampler = device.sampler;
            imageInfo.imageView = target.colorView;
            imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }else{
            imageInfo.sampler = device.sampler;
            imageInfo.imageView = device.textureView;
            imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }

        VkWriteDescriptorSet write{};
        write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        write.dstSet = device.descriptorSet;
        write.dstBinding = 1;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.pImageInfo = &imageInfo;
        api.vkUpdateDescriptorSets(device.device, 1, &write, 0, nullptr);
        return true;
    }

    void writeDescriptors(){
        VkDescriptorBufferInfo uniformInfo{device.uniformBuffer, 0, device.uniformRange};
        VkWriteDescriptorSet writes[2]{};
        writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[0].dstSet = device.descriptorSet;
        writes[0].dstBinding = 0;
        writes[0].descriptorCount = 1;
        writes[0].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        writes[0].pBufferInfo = &uniformInfo;
        api.vkUpdateDescriptorSets(device.device, 1, &writes[0], 0, nullptr);
        writeDescriptorImage();
    }

    void destroySwapchainTarget(){
        VkDevice d = device.device;
        for(auto framebuffer : device.swapFramebuffers) api.vkDestroyFramebuffer(d, framebuffer, nullptr);
        for(auto view : device.swapViews) api.vkDestroyImageView(d, view, nullptr);
        device.swapViews.clear();
        device.swapFramebuffers.clear();
        if(device.swapchain != VK_NULL_HANDLE && api.vkDestroySwapchainKHR) api.vkDestroySwapchainKHR(d, device.swapchain, nullptr);
        device.swapchain = VK_NULL_HANDLE;
        device.swapImages.clear();
        device.frameOpen = false;
    }

    void destroyOffscreenTarget(){
        VkDevice d = device.device;
        if(device.framebuffer != VK_NULL_HANDLE) api.vkDestroyFramebuffer(d, device.framebuffer, nullptr);
        if(device.colorView != VK_NULL_HANDLE) api.vkDestroyImageView(d, device.colorView, nullptr);
        if(device.colorImage != VK_NULL_HANDLE) api.vkDestroyImage(d, device.colorImage, nullptr);
        if(device.colorMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.colorMemory, nullptr);
        device.framebuffer = VK_NULL_HANDLE;
        device.colorView = VK_NULL_HANDLE;
        device.colorImage = VK_NULL_HANDLE;
        device.colorMemory = VK_NULL_HANDLE;
        device.colorInitialized = false;
    }

    bool createOffscreenTarget(uint32_t width, uint32_t height){
        destroyOffscreenTarget();
        destroyDepthTarget();
        device.colorInitialized = false;

        if(!createImage(width, height, device.colorFormat,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                    | VK_IMAGE_USAGE_SAMPLED_BIT,
                device.colorImage, device.colorMemory)) return false;
        device.colorView = createImageView(device.colorImage, device.colorFormat, VK_IMAGE_ASPECT_COLOR_BIT);
        if(device.colorView == VK_NULL_HANDLE) return false;

        // Depth is optional: a device without a usable depth format still renders color.
        createDefaultDepthTarget(width, height);

        if(!device.hasDepth) device.lastError[0] = '\0';
        if(device.offscreenPass == VK_NULL_HANDLE || device.offscreenPassHasDepth != device.hasDepth){
            if(device.offscreenPass != VK_NULL_HANDLE) api.vkDestroyRenderPass(device.device, device.offscreenPass, nullptr);
            device.offscreenPass = VK_NULL_HANDLE;
            if(!createRenderPass(device.colorFormat, device.hasDepth ? device.depthFormat : VK_FORMAT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, device.offscreenPass, true)) return false;
            device.offscreenPassHasDepth = device.hasDepth;
            invalidatePipeline();
        }

        return createFramebuffer(device.offscreenPass, device.colorView, device.depthView, width, height,
            device.framebuffer);
    }

    bool createSwapchainTarget(uint32_t width, uint32_t height){
        destroySwapchainTarget();
        if(device.surface == VK_NULL_HANDLE){
            std::snprintf(device.lastError, sizeof(device.lastError), "no window surface attached");
            return false;
        }

        VkSurfaceCapabilitiesKHR capabilities{};
        if(failed(api.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physical, device.surface, &capabilities),
                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR")) return false;
        if((capabilities.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0){
            std::snprintf(device.lastError, sizeof(device.lastError), "surface does not support color-attachment swapchain images");
            return false;
        }

        uint32_t formatCount = 0;
        if(failed(api.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical, device.surface, &formatCount, nullptr),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(count)")) return false;
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        if(formatCount > 0 && failed(api.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical, device.surface,
                &formatCount, formats.data()), "vkGetPhysicalDeviceSurfaceFormatsKHR")) return false;
        if(formatCount == 0){
            std::snprintf(device.lastError, sizeof(device.lastError), "surface has no formats");
            return false;
        }
        formats.resize(formatCount);
        VkSurfaceFormatKHR chosen = formats[0];
        for(const auto& format : formats){
            if(format.format == VK_FORMAT_B8G8R8A8_UNORM && format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR){
                chosen = format;
                break;
            }
        }
        if(chosen.format == VK_FORMAT_UNDEFINED) chosen.format = VK_FORMAT_B8G8R8A8_UNORM;
        device.windowFormat = chosen.format;

        uint32_t presentCount = 0;
        if(failed(api.vkGetPhysicalDeviceSurfacePresentModesKHR(device.physical, device.surface, &presentCount, nullptr),
                "vkGetPhysicalDeviceSurfacePresentModesKHR(count)")) return false;
        std::vector<VkPresentModeKHR> presentModes(presentCount);
        if(presentCount > 0 && failed(api.vkGetPhysicalDeviceSurfacePresentModesKHR(device.physical, device.surface,
                &presentCount, presentModes.data()), "vkGetPhysicalDeviceSurfacePresentModesKHR")) return false;
        presentModes.resize(presentCount);
        VkPresentModeKHR presentMode = VK_PRESENT_MODE_FIFO_KHR;
        if(!device.vSync){
            for(const auto& mode : presentModes){
                if(mode == VK_PRESENT_MODE_MAILBOX_KHR){
                    presentMode = mode;
                    break;
                }
            }
            if(presentMode == VK_PRESENT_MODE_FIFO_KHR){
                for(const auto& mode : presentModes){
                    if(mode == VK_PRESENT_MODE_IMMEDIATE_KHR){
                        presentMode = mode;
                        break;
                    }
                }
            }
        }

        VkExtent2D extent{width, height};
        if(capabilities.currentExtent.width != UINT32_MAX){
            extent = capabilities.currentExtent;
        }else{
            if(extent.width < capabilities.minImageExtent.width) extent.width = capabilities.minImageExtent.width;
            if(extent.height < capabilities.minImageExtent.height) extent.height = capabilities.minImageExtent.height;
            if(extent.width > capabilities.maxImageExtent.width) extent.width = capabilities.maxImageExtent.width;
            if(extent.height > capabilities.maxImageExtent.height) extent.height = capabilities.maxImageExtent.height;
        }
        if(extent.width == 0 || extent.height == 0){
            std::snprintf(device.lastError, sizeof(device.lastError), "window surface has a zero-sized extent");
            return false;
        }

        VkCompositeAlphaFlagBitsKHR compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        if((capabilities.supportedCompositeAlpha & compositeAlpha) == 0){
            const VkCompositeAlphaFlagBitsKHR choices[] = {
                VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
                VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
                VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
            };
            bool foundAlpha = false;
            for(auto choice : choices){
                if(capabilities.supportedCompositeAlpha & choice){
                    compositeAlpha = choice;
                    foundAlpha = true;
                    break;
                }
            }
            if(!foundAlpha){
                std::snprintf(device.lastError, sizeof(device.lastError), "surface has no supported composite alpha mode");
                return false;
            }
        }

        uint32_t imageCount = capabilities.minImageCount + 1;
        if(capabilities.maxImageCount > 0 && imageCount > capabilities.maxImageCount) imageCount = capabilities.maxImageCount;

        // A rendered target is blitted into a swapchain image, so it must be a transfer
        // destination as well as a colour attachment.
        VkImageUsageFlags usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        if((capabilities.supportedUsageFlags & usage) != usage){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "surface does not support transfer-destination swapchain images, needed to present a rendered target");
            return false;
        }

        VkSwapchainCreateInfoKHR info{};
        info.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        info.surface = device.surface;
        info.minImageCount = imageCount;
        info.imageFormat = chosen.format;
        info.imageColorSpace = chosen.colorSpace;
        info.imageExtent = extent;
        info.imageArrayLayers = 1;
        info.imageUsage = usage;
        info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.preTransform = capabilities.currentTransform;
        info.compositeAlpha = compositeAlpha;
        info.presentMode = presentMode;
        info.clipped = VK_TRUE;
        if(failed(api.vkCreateSwapchainKHR(device.device, &info, nullptr, &device.swapchain), "vkCreateSwapchainKHR")) return false;

        uint32_t count = 0;
        if(failed(api.vkGetSwapchainImagesKHR(device.device, device.swapchain, &count, nullptr), "vkGetSwapchainImagesKHR")) return false;
        device.swapImages.resize(count);
        if(failed(api.vkGetSwapchainImagesKHR(device.device, device.swapchain, &count, device.swapImages.data()),
                "vkGetSwapchainImagesKHR(2)")) return false;

        device.swapViews.reserve(count);
        device.swapFramebuffers.reserve(count);
        for(auto image : device.swapImages){
            VkImageView view = createImageView(image, chosen.format, VK_IMAGE_ASPECT_COLOR_BIT);
            if(view == VK_NULL_HANDLE) return false;
            device.swapViews.push_back(view);
        }

        // Depth is optional: presenting without it beats failing the whole window.
        if(!createDefaultDepthTarget(extent.width, extent.height)) device.lastError[0] = '\0';

        bool samePass = device.windowedPass != VK_NULL_HANDLE
            && device.windowedPassHasDepth == device.hasDepth
            && device.windowedPassFormat == chosen.format;
        if(!samePass){
            if(device.windowedPass != VK_NULL_HANDLE) api.vkDestroyRenderPass(device.device, device.windowedPass, nullptr);
            device.windowedPass = VK_NULL_HANDLE;
            if(!createRenderPass(chosen.format, device.hasDepth ? device.depthFormat : VK_FORMAT_UNDEFINED,
                    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, device.windowedPass)) return false;
            device.windowedPassHasDepth = device.hasDepth;
            device.windowedPassFormat = chosen.format;
            invalidatePipeline();
        }

        for(auto view : device.swapViews){
            VkFramebuffer framebuffer = VK_NULL_HANDLE;
            if(!createFramebuffer(device.windowedPass, view, device.depthView, extent.width, extent.height,
                    framebuffer)) return false;
            device.swapFramebuffers.push_back(framebuffer);
        }

        device.width = extent.width;
        device.height = extent.height;
        device.viewport = {0.0f, 0.0f, static_cast<float>(extent.width), static_cast<float>(extent.height), 0.0f, 1.0f};
        device.scissor = {{0, 0}, extent.width, extent.height};
        return true;
    }

    void destroyOffscreenRenderPass(){
        if(device.offscreenPass != VK_NULL_HANDLE){
            api.vkDestroyRenderPass(device.device, device.offscreenPass, nullptr);
            device.offscreenPass = VK_NULL_HANDLE;
        }
        if(device.windowedPass != VK_NULL_HANDLE){
            api.vkDestroyRenderPass(device.device, device.windowedPass, nullptr);
            device.windowedPass = VK_NULL_HANDLE;
        }
    }

    void destroyResources(){
        if(api.vkDestroyDevice == nullptr) return;
        VkDevice d = device.device;
        if(device.pipeline != VK_NULL_HANDLE) api.vkDestroyPipeline(d, device.pipeline, nullptr);
        if(device.descriptorPool != VK_NULL_HANDLE) api.vkDestroyDescriptorPool(d, device.descriptorPool, nullptr);
        if(device.pipelineLayout != VK_NULL_HANDLE) api.vkDestroyPipelineLayout(d, device.pipelineLayout, nullptr);
        if(device.descriptorLayout != VK_NULL_HANDLE) api.vkDestroyDescriptorSetLayout(d, device.descriptorLayout, nullptr);
        if(device.sampler != VK_NULL_HANDLE) api.vkDestroySampler(d, device.sampler, nullptr);
        if(device.textureView != VK_NULL_HANDLE) api.vkDestroyImageView(d, device.textureView, nullptr);
        if(device.textureImage != VK_NULL_HANDLE) api.vkDestroyImage(d, device.textureImage, nullptr);
        if(device.textureMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.textureMemory, nullptr);
        destroyRenderTargets();
        destroySwapchainTarget();
        destroyOffscreenTarget();
        destroyDepthTarget();
        destroyOffscreenRenderPass();
        if(device.vertexMapped) api.vkUnmapMemory(d, device.vertexMemory);
        if(device.indexMapped) api.vkUnmapMemory(d, device.indexMemory);
        if(device.uniformMapped) api.vkUnmapMemory(d, device.uniformMemory);
        if(device.stagingMapped) api.vkUnmapMemory(d, device.stagingMemory);
        if(device.readbackMapped) api.vkUnmapMemory(d, device.readbackMemory);
        if(device.vertexBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(d, device.vertexBuffer, nullptr);
        if(device.indexBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(d, device.indexBuffer, nullptr);
        if(device.uniformBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(d, device.uniformBuffer, nullptr);
        if(device.stagingBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(d, device.stagingBuffer, nullptr);
        if(device.readbackBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(d, device.readbackBuffer, nullptr);
        if(device.vertexMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.vertexMemory, nullptr);
        if(device.indexMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.indexMemory, nullptr);
        if(device.uniformMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.uniformMemory, nullptr);
        if(device.stagingMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.stagingMemory, nullptr);
        if(device.readbackMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.readbackMemory, nullptr);
        if(device.fence != VK_NULL_HANDLE) api.vkDestroyFence(d, device.fence, nullptr);
        if(device.acquireSemaphore != VK_NULL_HANDLE) api.vkDestroySemaphore(d, device.acquireSemaphore, nullptr);
        if(device.renderSemaphore != VK_NULL_HANDLE) api.vkDestroySemaphore(d, device.renderSemaphore, nullptr);
        if(device.commandPool != VK_NULL_HANDLE){
            if(device.commandBuffer != VK_NULL_HANDLE) api.vkFreeCommandBuffers(d, device.commandPool, 1, &device.commandBuffer);
            api.vkDestroyCommandPool(d, device.commandPool, nullptr);
        }
        if(d != VK_NULL_HANDLE) api.vkDestroyDevice(d, nullptr);
    }

    void shutdown(bool keepError = false){
        char error[sizeof(device.lastError)]{};
        if(keepError) std::snprintf(error, sizeof(error), "%s", device.lastError);

        if(api.vkQueueWaitIdle && device.queue != VK_NULL_HANDLE) api.vkQueueWaitIdle(device.queue);
        destroyResources();
        if(api.vkDestroySurfaceKHR && device.surface != VK_NULL_HANDLE && device.instance != VK_NULL_HANDLE){
            api.vkDestroySurfaceKHR(device.instance, device.surface, nullptr);
        }
        if(api.vkDestroyInstance && device.instance != VK_NULL_HANDLE) api.vkDestroyInstance(device.instance, nullptr);
        device = Device{};
        if(api.library){ FreeLibrary(api.library); api.library = nullptr; }
        api = Api{};

        // A failed create() must still explain why it failed after the cleanup.
        if(keepError) std::snprintf(device.lastError, sizeof(device.lastError), "%s", error);
    }

    bool missingEntryPoint(const char* name){
        std::snprintf(device.lastError, sizeof(device.lastError), "missing Vulkan entry point %s", name);
        return false;
    }

    bool loadGlobalFunctions(){
        api.library = LoadLibraryA("vulkan-1.dll");
        if(api.library == nullptr){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "LoadLibrary(vulkan-1.dll) failed (Windows error %lu)", GetLastError());
            return false;
        }
        VK_LOAD_GLOBAL(vkGetInstanceProcAddr);
        if(api.vkGetInstanceProcAddr == nullptr) return missingEntryPoint("vkGetInstanceProcAddr");
        VK_LOAD_GLOBAL(vkCreateInstance);
        if(api.vkCreateInstance == nullptr){
            api.vkCreateInstance = reinterpret_cast<PFN_vkCreateInstance>(
                api.vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkCreateInstance"));
        }
        return api.vkCreateInstance != nullptr || missingEntryPoint("vkCreateInstance");
    }

    bool loadInstanceFunctions(){
#define LOAD_INSTANCE_REQUIRED(name) do { VK_LOAD_INSTANCE(name); if(api.name == nullptr) return missingEntryPoint(#name); } while(0)
        LOAD_INSTANCE_REQUIRED(vkDestroyInstance);
        LOAD_INSTANCE_REQUIRED(vkEnumeratePhysicalDevices);
        LOAD_INSTANCE_REQUIRED(vkGetPhysicalDeviceProperties);
        LOAD_INSTANCE_REQUIRED(vkGetPhysicalDeviceQueueFamilyProperties);
        LOAD_INSTANCE_REQUIRED(vkEnumerateDeviceExtensionProperties);
        LOAD_INSTANCE_REQUIRED(vkGetPhysicalDeviceMemoryProperties);
        LOAD_INSTANCE_REQUIRED(vkGetPhysicalDeviceFeatures);
        LOAD_INSTANCE_REQUIRED(vkGetPhysicalDeviceImageFormatProperties);
        LOAD_INSTANCE_REQUIRED(vkCreateDevice);
        LOAD_INSTANCE_REQUIRED(vkGetDeviceProcAddr);
#undef LOAD_INSTANCE_REQUIRED
        return true;
    }

    bool loadSurfaceInstanceFunctions(){
#define LOAD_SURFACE_INSTANCE(name) do { VK_LOAD_INSTANCE(name); if(api.name == nullptr) return missingEntryPoint(#name); } while(0)
        LOAD_SURFACE_INSTANCE(vkCreateWin32SurfaceKHR);
        LOAD_SURFACE_INSTANCE(vkDestroySurfaceKHR);
        LOAD_SURFACE_INSTANCE(vkGetPhysicalDeviceSurfaceSupportKHR);
        LOAD_SURFACE_INSTANCE(vkGetPhysicalDeviceSurfaceCapabilitiesKHR);
        LOAD_SURFACE_INSTANCE(vkGetPhysicalDeviceSurfaceFormatsKHR);
        LOAD_SURFACE_INSTANCE(vkGetPhysicalDeviceSurfacePresentModesKHR);
#undef LOAD_SURFACE_INSTANCE
        return true;
    }

    bool loadSwapchainDeviceFunctions(){
#define LOAD_SWAPCHAIN_DEVICE(name) do { VK_LOAD_DEVICE(name); if(api.name == nullptr) return missingEntryPoint(#name); } while(0)
        LOAD_SWAPCHAIN_DEVICE(vkCreateSwapchainKHR);
        LOAD_SWAPCHAIN_DEVICE(vkDestroySwapchainKHR);
        LOAD_SWAPCHAIN_DEVICE(vkGetSwapchainImagesKHR);
        LOAD_SWAPCHAIN_DEVICE(vkAcquireNextImageKHR);
        LOAD_SWAPCHAIN_DEVICE(vkQueuePresentKHR);
#undef LOAD_SWAPCHAIN_DEVICE
        return true;
    }

    bool supportsDeviceExtension(VkPhysicalDevice physical, const char* extension){
        uint32_t count = 0;
        VkResult result = api.vkEnumerateDeviceExtensionProperties(physical, nullptr, &count, nullptr);
        if(failed(result, "vkEnumerateDeviceExtensionProperties")) return false;
        std::vector<VkExtensionProperties> extensions(count);
        if(count > 0){
            result = api.vkEnumerateDeviceExtensionProperties(physical, nullptr, &count, extensions.data());
            if(failed(result, "vkEnumerateDeviceExtensionProperties(2)")) return false;
        }
        for(const auto& supported : extensions){
            if(std::strcmp(supported.extensionName, extension) == 0) return true;
        }
        return false;
    }

    bool createWindowSemaphores(){
        VkSemaphoreCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
        if(failed(api.vkCreateSemaphore(device.device, &info, nullptr, &device.acquireSemaphore),
                "vkCreateSemaphore(acquire)")) return false;
        if(failed(api.vkCreateSemaphore(device.device, &info, nullptr, &device.renderSemaphore),
                "vkCreateSemaphore(render)")) return false;
        return true;
    }

    bool replaceWindowSemaphores(){
        if(failed(api.vkQueueWaitIdle(device.queue), "vkQueueWaitIdle(semaphore recovery)")){
            device.initialized = false;
            return false;
        }
        if(device.acquireSemaphore != VK_NULL_HANDLE){
            api.vkDestroySemaphore(device.device, device.acquireSemaphore, nullptr);
            device.acquireSemaphore = VK_NULL_HANDLE;
        }
        if(device.renderSemaphore != VK_NULL_HANDLE){
            api.vkDestroySemaphore(device.device, device.renderSemaphore, nullptr);
            device.renderSemaphore = VK_NULL_HANDLE;
        }
        if(!createWindowSemaphores()){
            device.initialized = false;
            return false;
        }
        return true;
    }

    bool loadDeviceFunctions(){
#define LOAD_DEVICE_REQUIRED(name) do { VK_LOAD_DEVICE(name); if(api.name == nullptr) return missingEntryPoint(#name); } while(0)
        VK_LOAD_DEVICE(vkDestroyDevice);
        if(api.vkDestroyDevice == nullptr){
            api.vkDestroyDevice = reinterpret_cast<PFN_vkDestroyDevice>(
                api.vkGetInstanceProcAddr(device.instance, "vkDestroyDevice"));
        }
        if(api.vkDestroyDevice == nullptr) return missingEntryPoint("vkDestroyDevice");
        LOAD_DEVICE_REQUIRED(vkGetDeviceQueue);
        LOAD_DEVICE_REQUIRED(vkCreateCommandPool);
        LOAD_DEVICE_REQUIRED(vkDestroyCommandPool);
        LOAD_DEVICE_REQUIRED(vkAllocateCommandBuffers);
        LOAD_DEVICE_REQUIRED(vkFreeCommandBuffers);
        LOAD_DEVICE_REQUIRED(vkBeginCommandBuffer);
        LOAD_DEVICE_REQUIRED(vkEndCommandBuffer);
        LOAD_DEVICE_REQUIRED(vkCreateFence);
        LOAD_DEVICE_REQUIRED(vkDestroyFence);
        LOAD_DEVICE_REQUIRED(vkWaitForFences);
        LOAD_DEVICE_REQUIRED(vkResetFences);
        LOAD_DEVICE_REQUIRED(vkResetCommandBuffer);
        LOAD_DEVICE_REQUIRED(vkQueueSubmit);
        LOAD_DEVICE_REQUIRED(vkQueueWaitIdle);
        LOAD_DEVICE_REQUIRED(vkCreateSemaphore);
        LOAD_DEVICE_REQUIRED(vkDestroySemaphore);
        LOAD_DEVICE_REQUIRED(vkCreateBuffer);
        LOAD_DEVICE_REQUIRED(vkDestroyBuffer);
        LOAD_DEVICE_REQUIRED(vkGetBufferMemoryRequirements);
        LOAD_DEVICE_REQUIRED(vkAllocateMemory);
        LOAD_DEVICE_REQUIRED(vkFreeMemory);
        LOAD_DEVICE_REQUIRED(vkBindBufferMemory);
        LOAD_DEVICE_REQUIRED(vkMapMemory);
        LOAD_DEVICE_REQUIRED(vkUnmapMemory);
        LOAD_DEVICE_REQUIRED(vkCreateImage);
        LOAD_DEVICE_REQUIRED(vkDestroyImage);
        LOAD_DEVICE_REQUIRED(vkGetImageMemoryRequirements);
        LOAD_DEVICE_REQUIRED(vkBindImageMemory);
        LOAD_DEVICE_REQUIRED(vkCreateImageView);
        LOAD_DEVICE_REQUIRED(vkDestroyImageView);
        LOAD_DEVICE_REQUIRED(vkCreateSampler);
        LOAD_DEVICE_REQUIRED(vkDestroySampler);
        LOAD_DEVICE_REQUIRED(vkCreateRenderPass);
        LOAD_DEVICE_REQUIRED(vkDestroyRenderPass);
        LOAD_DEVICE_REQUIRED(vkCreateFramebuffer);
        LOAD_DEVICE_REQUIRED(vkDestroyFramebuffer);
        LOAD_DEVICE_REQUIRED(vkCreateShaderModule);
        LOAD_DEVICE_REQUIRED(vkDestroyShaderModule);
        LOAD_DEVICE_REQUIRED(vkCreateDescriptorSetLayout);
        LOAD_DEVICE_REQUIRED(vkDestroyDescriptorSetLayout);
        LOAD_DEVICE_REQUIRED(vkCreatePipelineLayout);
        LOAD_DEVICE_REQUIRED(vkDestroyPipelineLayout);
        LOAD_DEVICE_REQUIRED(vkCreateDescriptorPool);
        LOAD_DEVICE_REQUIRED(vkDestroyDescriptorPool);
        LOAD_DEVICE_REQUIRED(vkAllocateDescriptorSets);
        LOAD_DEVICE_REQUIRED(vkUpdateDescriptorSets);
        LOAD_DEVICE_REQUIRED(vkCreateGraphicsPipelines);
        LOAD_DEVICE_REQUIRED(vkDestroyPipeline);
        LOAD_DEVICE_REQUIRED(vkCmdBeginRenderPass);
        LOAD_DEVICE_REQUIRED(vkCmdEndRenderPass);
        LOAD_DEVICE_REQUIRED(vkCmdClearAttachments);
        LOAD_DEVICE_REQUIRED(vkCmdBindPipeline);
        LOAD_DEVICE_REQUIRED(vkCmdBindDescriptorSets);
        LOAD_DEVICE_REQUIRED(vkCmdBindVertexBuffers);
        LOAD_DEVICE_REQUIRED(vkCmdBindIndexBuffer);
        LOAD_DEVICE_REQUIRED(vkCmdSetViewport);
        LOAD_DEVICE_REQUIRED(vkCmdSetScissor);
        LOAD_DEVICE_REQUIRED(vkCmdDraw);
        LOAD_DEVICE_REQUIRED(vkCmdDrawIndexed);
        LOAD_DEVICE_REQUIRED(vkCmdPipelineBarrier);
        LOAD_DEVICE_REQUIRED(vkCmdCopyImageToBuffer);
    // Moves a rendered target into the swapchain and converts format when the surface wants BGRA.
    if(api.vkGetDeviceProcAddr(device.device, "vkCmdBlitImage") != nullptr){
        LOAD_DEVICE_REQUIRED(vkCmdBlitImage);
    }
        LOAD_DEVICE_REQUIRED(vkCmdCopyBufferToImage);
#undef LOAD_DEVICE_REQUIRED
        return true;
    }

    bool createCommandObjects(){
        VkCommandPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        poolInfo.queueFamilyIndex = device.queueFamily;
        if(failed(api.vkCreateCommandPool(device.device, &poolInfo, nullptr, &device.commandPool),
                "vkCreateCommandPool")) return false;

        VkCommandBufferAllocateInfo commandInfo{};
        commandInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        commandInfo.commandPool = device.commandPool;
        commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        commandInfo.commandBufferCount = 1;
        if(failed(api.vkAllocateCommandBuffers(device.device, &commandInfo, &device.commandBuffer),
                "vkAllocateCommandBuffers")) return false;

        VkFenceCreateInfo fenceInfo{};
        fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        return !failed(api.vkCreateFence(device.device, &fenceInfo, nullptr, &device.fence), "vkCreateFence");
    }

    bool createWhiteTexture(){
        if(!createImage(1, 1, VK_FORMAT_R8G8B8A8_UNORM,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                device.textureImage, device.textureMemory)) return false;
        device.textureView = createImageView(device.textureImage, VK_FORMAT_R8G8B8A8_UNORM,
            VK_IMAGE_ASPECT_COLOR_BIT);
        if(device.textureView == VK_NULL_HANDLE || !createSampler(false)) return false;

        auto* white = static_cast<uint8_t*>(device.stagingMapped);
        white[0] = white[1] = white[2] = white[3] = 255;
        if(!beginCommand()) return false;
        transition(device.textureImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        VkBufferImageCopy copy{};
        copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        copy.imageExtent = {1, 1, 1};
        api.vkCmdCopyBufferToImage(device.commandBuffer, device.stagingBuffer, device.textureImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copy);
        transition(device.textureImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        return submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE);
    }

    bool createDescriptorObjects(){
        VkDescriptorSetLayoutBinding bindings[2]{};
        bindings[0].binding = 0;
        bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        bindings[0].descriptorCount = 1;
        bindings[0].stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        bindings[1].binding = 1;
        bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        bindings[1].descriptorCount = 1;
        bindings[1].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

        VkDescriptorSetLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        layoutInfo.bindingCount = 2;
        layoutInfo.pBindings = bindings;
        if(failed(api.vkCreateDescriptorSetLayout(device.device, &layoutInfo, nullptr, &device.descriptorLayout),
                "vkCreateDescriptorSetLayout")) return false;

        VkPipelineLayoutCreateInfo pipelineLayoutInfo{};
        pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        pipelineLayoutInfo.setLayoutCount = 1;
        pipelineLayoutInfo.pSetLayouts = &device.descriptorLayout;
        if(failed(api.vkCreatePipelineLayout(device.device, &pipelineLayoutInfo, nullptr, &device.pipelineLayout),
                "vkCreatePipelineLayout")) return false;

        VkDescriptorPoolSize poolSizes[2]{};
        poolSizes[0] = {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1};
        poolSizes[1] = {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1};
        VkDescriptorPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        poolInfo.maxSets = 1;
        poolInfo.poolSizeCount = 2;
        poolInfo.pPoolSizes = poolSizes;
        if(failed(api.vkCreateDescriptorPool(device.device, &poolInfo, nullptr, &device.descriptorPool),
                "vkCreateDescriptorPool")) return false;

        VkDescriptorSetAllocateInfo setInfo{};
        setInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        setInfo.descriptorPool = device.descriptorPool;
        setInfo.descriptorSetCount = 1;
        setInfo.pSetLayouts = &device.descriptorLayout;
        if(failed(api.vkAllocateDescriptorSets(device.device, &setInfo, &device.descriptorSet),
                "vkAllocateDescriptorSets")) return false;
        writeDescriptors();
        return true;
    }

    bool initializeDevice(uint32_t width, uint32_t height, bool windowed, HWND hwnd = nullptr, HINSTANCE hinstance = nullptr){
        if(!loadGlobalFunctions()) return false;
        device.windowed = windowed;

        VkApplicationInfo application{};
        application.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        application.pApplicationName = windowed ? "Orbital Vulkan windowed smoke" : "Orbital Vulkan offscreen";
        application.applicationVersion = 1;
        application.pEngineName = "Orbital";
        application.engineVersion = 1;
        application.apiVersion = VK_API_VERSION_1_0;
        const char* instanceExtensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_WIN32_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo instanceInfo{};
        instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        instanceInfo.pApplicationInfo = &application;
        if(windowed){
            instanceInfo.enabledExtensionCount = 2;
            instanceInfo.ppEnabledExtensionNames = instanceExtensions;
        }
        if(failed(api.vkCreateInstance(&instanceInfo, nullptr, &device.instance), "vkCreateInstance")) return false;
        if(!loadInstanceFunctions()) return false;
        if(windowed){
            if(!loadSurfaceInstanceFunctions()) return false;
            VkWin32SurfaceCreateInfoKHR surfaceInfo{};
            surfaceInfo.sType = VK_STRUCTURE_TYPE_WIN32_SURFACE_CREATE_INFO_KHR;
            surfaceInfo.hwnd = hwnd;
            surfaceInfo.hinstance = hinstance;
            if(failed(api.vkCreateWin32SurfaceKHR(device.instance, &surfaceInfo, nullptr, &device.surface),
                    "vkCreateWin32SurfaceKHR")) return false;
            device.hasWin32Surface = true;
        }

        uint32_t physicalCount = 0;
        if(failed(api.vkEnumeratePhysicalDevices(device.instance, &physicalCount, nullptr),
                "vkEnumeratePhysicalDevices")) return false;
        if(physicalCount == 0){
            std::snprintf(device.lastError, sizeof(device.lastError), "Vulkan reports no physical devices");
            return false;
        }
        std::vector<VkPhysicalDevice> physicalDevices(physicalCount);
        VkResult enumerateResult = api.vkEnumeratePhysicalDevices(device.instance, &physicalCount, physicalDevices.data());
        if(enumerateResult != VK_SUCCESS){
            if(enumerateResult == VK_INCOMPLETE){
                std::snprintf(device.lastError, sizeof(device.lastError), "physical device list changed during enumeration");
            }else{
                failed(enumerateResult, "vkEnumeratePhysicalDevices(2)");
            }
            return false;
        }
        physicalDevices.resize(physicalCount);

        for(VkPhysicalDevice physical : physicalDevices){
            if(windowed){
                bool hasSwapchain = supportsDeviceExtension(physical, VK_KHR_SWAPCHAIN_EXTENSION_NAME);
                if(device.lastError[0] != '\0') return false;
                if(!hasSwapchain) continue;
            }
            uint32_t queueCount = 0;
            api.vkGetPhysicalDeviceQueueFamilyProperties(physical, &queueCount, nullptr);
            std::vector<VkQueueFamilyProperties> queueFamilies(queueCount);
            if(queueCount > 0) api.vkGetPhysicalDeviceQueueFamilyProperties(physical, &queueCount, queueFamilies.data());
            for(uint32_t i = 0; i < queueCount; i++){
                if(queueFamilies[i].queueCount == 0 || (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) continue;
                if(windowed){
                    VkBool32 presentSupport = VK_FALSE;
                    if(failed(api.vkGetPhysicalDeviceSurfaceSupportKHR(physical, i, device.surface, &presentSupport),
                            "vkGetPhysicalDeviceSurfaceSupportKHR")) return false;
                    if(!presentSupport) continue;
                }
                device.physical = physical;
                device.queueFamily = i;
                device.hasSwapchainExtension = windowed;
                break;
            }
            if(device.physical != VK_NULL_HANDLE) break;
        }
        if(device.physical == VK_NULL_HANDLE){
            std::snprintf(device.lastError, sizeof(device.lastError), windowed
                ? "no Vulkan physical device has a graphics queue with surface presentation and VK_KHR_swapchain support"
                : "no Vulkan physical device has a graphics queue");
            return false;
        }

        VkPhysicalDeviceProperties properties{};
        api.vkGetPhysicalDeviceProperties(device.physical, &properties);
        std::snprintf(device.deviceName, sizeof(device.deviceName), "%s", properties.deviceName);
        if(!windowed && (width > properties.limits.maxImageDimension2D || height > properties.limits.maxImageDimension2D)){
            std::snprintf(device.lastError, sizeof(device.lastError), "target size exceeds device image limits");
            return false;
        }
        device.uniformRange = properties.limits.maxUniformBufferRange < Device::uniformCapacity
            ? properties.limits.maxUniformBufferRange : Device::uniformCapacity;
        if(!windowed){
            VkImageFormatProperties colorImageProperties{};
            VkResult colorSupport = api.vkGetPhysicalDeviceImageFormatProperties(device.physical, device.colorFormat,
                VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, 0, &colorImageProperties);
            if(failed(colorSupport, "offscreen RGBA8 image format is unsupported")) return false;
            if(width > colorImageProperties.maxExtent.width || height > colorImageProperties.maxExtent.height){
                std::snprintf(device.lastError, sizeof(device.lastError), "target size exceeds RGBA8 image format limits");
                return false;
            }
        }
        api.vkGetPhysicalDeviceMemoryProperties(device.physical, &device.memoryProperties);
        // wideLines decides whether a pipeline may use a line width other than 1.0.
        if(api.vkGetPhysicalDeviceFeatures != nullptr){
            api.vkGetPhysicalDeviceFeatures(device.physical, &device.features);
            device.supportsWideLines = device.features.wideLines == VK_TRUE;
        }

        float priority = 1.0f;
        VkDeviceQueueCreateInfo queueInfo{};
        queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueInfo.queueFamilyIndex = device.queueFamily;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;
        VkDeviceCreateInfo deviceInfo{};
        deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        deviceInfo.queueCreateInfoCount = 1;
        deviceInfo.pQueueCreateInfos = &queueInfo;
        const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        if(windowed){
            deviceInfo.enabledExtensionCount = 1;
            deviceInfo.ppEnabledExtensionNames = deviceExtensions;
        }
        if(failed(api.vkCreateDevice(device.physical, &deviceInfo, nullptr, &device.device), "vkCreateDevice")) return false;
        if(!loadDeviceFunctions()) return false;
        if(windowed && !loadSwapchainDeviceFunctions()) return false;
        api.vkGetDeviceQueue(device.device, device.queueFamily, 0, &device.queue);
        if(device.queue == VK_NULL_HANDLE){
            std::snprintf(device.lastError, sizeof(device.lastError), "Vulkan returned a null graphics queue");
            return false;
        }
        if(!createCommandObjects()) return false;
        if(windowed && (!createSwapchainTarget(width, height) || !createWindowSemaphores())) return false;

        VkDeviceSize pixelCount = static_cast<VkDeviceSize>(width) * static_cast<VkDeviceSize>(height);
        if(pixelCount > std::numeric_limits<VkDeviceSize>::max() / 4
                || pixelCount * 4 > static_cast<VkDeviceSize>(std::numeric_limits<jsize>::max())){
            std::snprintf(device.lastError, sizeof(device.lastError), "target readback size exceeds Java array limits");
            return false;
        }
        VkDeviceSize readbackSize = pixelCount * 4;
        device.vertexCapacity = Device::stagingCapacity;
        if(!createBuffer(device.vertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                device.vertexBuffer, device.vertexMemory, &device.vertexMapped)) return false;
        device.indexCapacity = Device::stagingCapacity;
        if(!createBuffer(device.indexCapacity, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                device.indexBuffer, device.indexMemory, &device.indexMapped)) return false;
        if(!createBuffer(Device::uniformCapacity, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                device.uniformBuffer, device.uniformMemory, &device.uniformMapped)) return false;
        std::memset(device.uniformMapped, 0, static_cast<size_t>(Device::uniformCapacity));
        if(!createBuffer(Device::stagingCapacity, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                device.stagingBuffer, device.stagingMemory, &device.stagingMapped)) return false;
        if(!createBuffer(readbackSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                device.readbackBuffer, device.readbackMemory, &device.readbackMapped)) return false;
        device.readbackCapacity = readbackSize;

        // A windowed device renders exactly like an offscreen one, into its own default target;
        // only the final blit differs. The surface decides the real drawable size, so the target
        // follows device.width/height once the swapchain exists.
        uint32_t targetWidth = device.width != 0 ? device.width : width;
        uint32_t targetHeight = device.height != 0 ? device.height : height;

        device.width = targetWidth;
        device.height = targetHeight;
        device.viewport = {0.0f, 0.0f, static_cast<float>(targetWidth), static_cast<float>(targetHeight), 0.0f, 1.0f};
        device.scissor = {{0, 0}, targetWidth, targetHeight};
        if(!createOffscreenTarget(targetWidth, targetHeight)) return false;
        if(!createWhiteTexture()) return false;
        if(!createDescriptorObjects()) return false;
        device.initialized = true;
        return true;
    }

    void invalidatePipeline(){
        if(device.pipeline != VK_NULL_HANDLE && api.vkDestroyPipeline != nullptr
                && device.device != VK_NULL_HANDLE){
            api.vkDestroyPipeline(device.device, device.pipeline, nullptr);
        }
        device.pipeline = VK_NULL_HANDLE;
    }

    bool recreateWindowTarget(){
        if(failed(api.vkQueueWaitIdle(device.queue), "vkQueueWaitIdle(swapchain recreation)")) return false;
        if(createSwapchainTarget(device.width, device.height)) return true;
        destroySwapchainTarget();
        return false;
    }

    bool requireInitialized(){
        if(device.initialized) return true;
        std::snprintf(device.lastError, sizeof(device.lastError), "device is not created");
        return false;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreate(JNIEnv*, jclass, jint width, jint height){
    shutdown();
    if(width <= 0 || height <= 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "invalid target size %dx%d", width, height);
        shutdown(true);
        return JNI_FALSE;
    }
    try{
        if(initializeDevice(static_cast<uint32_t>(width), static_cast<uint32_t>(height), false)) return JNI_TRUE;
    }catch(const std::bad_alloc&){
        std::snprintf(device.lastError, sizeof(device.lastError), "out of memory while creating Vulkan device");
    }
    if(device.lastError[0] == '\0') std::snprintf(device.lastError, sizeof(device.lastError), "Vulkan device initialization failed");
    shutdown(true);
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreateWindowed(JNIEnv*, jclass, jint width, jint height,
        jlong hwndValue, jlong hinstanceValue){
    shutdown();
    if(width <= 0 || height <= 0 || hwndValue == 0 || hinstanceValue == 0){
        std::snprintf(device.lastError, sizeof(device.lastError),
            "windowed Vulkan creation requires positive dimensions and non-null HWND/HINSTANCE");
        shutdown(true);
        return JNI_FALSE;
    }
    HWND hwnd = reinterpret_cast<HWND>(static_cast<uintptr_t>(hwndValue));
    HINSTANCE hinstance = reinterpret_cast<HINSTANCE>(static_cast<uintptr_t>(hinstanceValue));
    try{
        if(initializeDevice(static_cast<uint32_t>(width), static_cast<uint32_t>(height), true, hwnd, hinstance)) return JNI_TRUE;
    }catch(const std::bad_alloc&){
        std::snprintf(device.lastError, sizeof(device.lastError), "out of memory while creating windowed Vulkan device");
    }
    if(device.lastError[0] == '\0') std::snprintf(device.lastError, sizeof(device.lastError), "windowed Vulkan initialization failed");
    shutdown(true);
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeDestroy(JNIEnv*, jclass){
    shutdown();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeClearAndPresent(JNIEnv*, jclass,
        jfloat red, jfloat green, jfloat blue, jfloat alpha){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(!device.windowed){
        std::snprintf(device.lastError, sizeof(device.lastError), "clearAndPresent requires a windowed Vulkan device");
        return JNI_FALSE;
    }
    if(device.acquireSemaphore == VK_NULL_HANDLE || device.renderSemaphore == VK_NULL_HANDLE){
        std::snprintf(device.lastError, sizeof(device.lastError), "window presentation semaphores are unavailable");
        return JNI_FALSE;
    }
    if(device.swapchain == VK_NULL_HANDLE && !recreateWindowTarget()) return JNI_FALSE;

    VkResult acquireResult = api.vkAcquireNextImageKHR(device.device, device.swapchain, UINT64_MAX,
        device.acquireSemaphore, VK_NULL_HANDLE, &device.imageIndex);
    if(acquireResult == VK_ERROR_OUT_OF_DATE_KHR){
        if(!recreateWindowTarget()) return JNI_FALSE;
        std::snprintf(device.lastError, sizeof(device.lastError),
            "swapchain was out of date; target recreated, retry clearAndPresent");
        return JNI_FALSE;
    }
    if(acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR){
        failed(acquireResult, "vkAcquireNextImageKHR");
        replaceWindowSemaphores();
        return JNI_FALSE;
    }
    bool recreateAfterPresent = acquireResult == VK_SUBOPTIMAL_KHR;
    device.frameOpen = true;

    if(!beginCommand()){
        replaceWindowSemaphores();
        device.frameOpen = false;
        return JNI_FALSE;
    }
    VkClearValue clearValues[2]{};
    clearValues[0].color.float32[0] = red;
    clearValues[0].color.float32[1] = green;
    clearValues[0].color.float32[2] = blue;
    clearValues[0].color.float32[3] = alpha;
    clearValues[1].depthStencil = {1.0f, 0};
    VkRenderPassBeginInfo renderInfo{};
    renderInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderInfo.renderPass = device.windowedPass;
    renderInfo.framebuffer = device.swapFramebuffers[device.imageIndex];
    renderInfo.renderArea = {{0, 0}, {device.width, device.height}};
    renderInfo.clearValueCount = device.windowedPassHasDepth ? 2u : 1u;
    renderInfo.pClearValues = clearValues;
    api.vkCmdBeginRenderPass(device.commandBuffer, &renderInfo, VK_SUBPASS_CONTENTS_INLINE);
    if(device.pipeline != VK_NULL_HANDLE && device.vertexBytes >= (6 * sizeof(float))){
        api.vkCmdSetViewport(device.commandBuffer, 0, 1, &device.viewport);
        api.vkCmdSetScissor(device.commandBuffer, 0, 1, &device.scissor);
        api.vkCmdBindPipeline(device.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, device.pipeline);
        api.vkCmdBindDescriptorSets(device.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
            device.pipelineLayout, 0, 1, &device.descriptorSet, 0, nullptr);
        VkDeviceSize offset = 0;
        api.vkCmdBindVertexBuffers(device.commandBuffer, 0, 1, &device.vertexBuffer, &offset);
        uint32_t vertexCount = static_cast<uint32_t>(device.vertexBytes / (6 * sizeof(float)));
        api.vkCmdDraw(device.commandBuffer, vertexCount, 1, 0, 0);
    }
    api.vkCmdEndRenderPass(device.commandBuffer);
    if(!submitCommand(device.acquireSemaphore, device.renderSemaphore)){
        replaceWindowSemaphores();
        device.frameOpen = false;
        return JNI_FALSE;
    }

    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = &device.renderSemaphore;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &device.swapchain;
    presentInfo.pImageIndices = &device.imageIndex;
    VkResult presentResult = api.vkQueuePresentKHR(device.queue, &presentInfo);
    if(failed(api.vkQueueWaitIdle(device.queue), "vkQueueWaitIdle(present)")){
        device.initialized = false;
        device.frameOpen = false;
        return JNI_FALSE;
    }
    device.frameOpen = false;

    if(presentResult == VK_ERROR_OUT_OF_DATE_KHR){
        if(!replaceWindowSemaphores() || !recreateWindowTarget()) return JNI_FALSE;
        std::snprintf(device.lastError, sizeof(device.lastError),
            "swapchain became out of date during presentation; target recreated, retry clearAndPresent");
        return JNI_FALSE;
    }
    if(presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR){
        failed(presentResult, "vkQueuePresentKHR");
        replaceWindowSemaphores();
        return JNI_FALSE;
    }
    if(presentResult == VK_SUBOPTIMAL_KHR) recreateAfterPresent = true;
    if(recreateAfterPresent && !recreateWindowTarget()) return JNI_FALSE;
    return JNI_TRUE;
}

    /**
     * Everything a graphics pipeline freezes, in the encoding documented by
     * nativeCreatePipelineWithState. GL enums are kept as-is and translated here.
     */
    struct PipelineRequest{
        std::vector<Attribute> attributes;
        uint32_t stride = 0;
        int primitive = 4;
        bool blend = false;
        int blendSrcRGB = 0, blendDstRGB = 0, blendSrcAlpha = 0, blendDstAlpha = 0;
        int blendEqRGB = 0, blendEqAlpha = 0;
        float blendR = 0.0f, blendG = 0.0f, blendB = 0.0f, blendA = 0.0f;
        bool depthTest = false, depthWrite = false;
        int depthFunc = 1;
        bool stencilTest = false;
        int stencilFunc = 1, stencilRef = 0, stencilMask = -1;
        int stencilFail = 7680, stencilDepthFail = 7680, stencilDepthPass = 7680;
        int cullFace = 0;
        bool frontFaceCCW = true;
        bool colorMaskR = true, colorMaskG = true, colorMaskB = true, colorMaskA = true;
        float lineWidth = 1.0f;
    };

    /** The default pipeline request matching the historical six-float layout. */
    // arc.graphics.GL20 enum values, spelled out so the tables below cannot silently drift.
    static constexpr int glBlendZero = 0;
    static constexpr int glBlendOne = 1;
    static constexpr int glBlendSrcColor = 768;              // 0x0300
    static constexpr int glBlendOneMinusSrcColor = 769;      // 0x0301
    static constexpr int glBlendSrcAlpha = 770;              // 0x0302
    static constexpr int glBlendOneMinusSrcAlpha = 771;      // 0x0303
    static constexpr int glBlendDstAlpha = 772;              // 0x0304
    static constexpr int glBlendOneMinusDstAlpha = 773;      // 0x0305
    static constexpr int glBlendDstColor = 774;              // 0x0306
    static constexpr int glBlendOneMinusDstColor = 775;      // 0x0307
    static constexpr int glBlendSrcAlphaSaturate = 776;      // 0x0308
    static constexpr int glBlendConstantColor = 32769;       // 0x8001
    static constexpr int glBlendOneMinusConstantColor = 32770;
    static constexpr int glBlendConstantAlpha = 32771;
    static constexpr int glBlendOneMinusConstantAlpha = 32772;
    static constexpr int glNever = 512;   // 0x0200
    static constexpr int glLess = 513;    // 0x0201
    static constexpr int glEqual = 514;   // 0x0202
    static constexpr int glLequal = 515;  // 0x0203
    static constexpr int glGreater = 516; // 0x0204
    static constexpr int glNotequal = 517;// 0x0205
    static constexpr int glGequal = 518;  // 0x0206
    static constexpr int glAlways = 519;  // 0x0207
    static constexpr int glStencilKeep = 7680;      // 0x1E00
    static constexpr int glStencilZero = 0;
    static constexpr int glStencilReplace = 7681;   // 0x1E01
    static constexpr int glStencilIncr = 7682;      // 0x1E02
    static constexpr int glStencilDecr = 7683;      // 0x1E03
    static constexpr int glStencilInvert = 5386;    // 0x150A
    static constexpr int glStencilIncrWrap = 34055; // 0x8507
    static constexpr int glStencilDecrWrap = 35056; // 0x8508

    static PipelineRequest defaultPipelineRequest(bool blend, int primitive, uint32_t stride,
            std::vector<Attribute> attributes){
        PipelineRequest request;
        request.attributes = std::move(attributes);
        request.stride = stride;
        request.primitive = primitive;
        request.blend = blend;
        request.blendSrcRGB = glBlendSrcAlpha;            // 770
        request.blendDstRGB = glBlendOneMinusSrcAlpha;    // 771
        request.blendSrcAlpha = glBlendOne;               // 1
        request.blendDstAlpha = glBlendOneMinusSrcAlpha;  // 771
        request.blendEqRGB = 32774;   // GL_FUNC_ADD
        request.blendEqAlpha = 32774;
        return request;
    }

    /**
     * GL blend factor (arc.graphics.GL20 values) to the Vulkan equivalent.
     */
        VkBlendFactor blendFactorFromGL(int value, bool& ok){
        ok = true;
        switch(value){
            case glBlendZero: return VK_BLEND_FACTOR_ZERO;
            case glBlendOne: return VK_BLEND_FACTOR_ONE;
            case glBlendSrcColor: return VK_BLEND_FACTOR_SRC_COLOR;
            case glBlendOneMinusSrcColor: return VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case glBlendSrcAlpha: return VK_BLEND_FACTOR_SRC_ALPHA;
            case glBlendOneMinusSrcAlpha: return VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case glBlendDstAlpha: return VK_BLEND_FACTOR_DST_ALPHA;
            case glBlendOneMinusDstAlpha: return VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case glBlendDstColor: return VK_BLEND_FACTOR_DST_COLOR;
            case glBlendOneMinusDstColor: return VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case glBlendSrcAlphaSaturate: return VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case glBlendConstantColor: return VK_BLEND_FACTOR_CONSTANT_COLOR;
            case glBlendOneMinusConstantColor: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case glBlendConstantAlpha: return VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case glBlendOneMinusConstantAlpha: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            default: ok = false; return VK_BLEND_FACTOR_ZERO;
        }
    }

    VkBlendOp blendOpFromGL(int value, bool& ok){
        ok = true;
        switch(value){
            case 32774: return VK_BLEND_OP_ADD;
            case 32778: return VK_BLEND_OP_SUBTRACT;
            case 32779: return VK_BLEND_OP_REVERSE_SUBTRACT;
            case 32769: return VK_BLEND_OP_MIN;
            case 32770: return VK_BLEND_OP_MAX;
            default: ok = false; return VK_BLEND_OP_ADD;
        }
    }

        VkCompareOp compareOpFromGL(int value, bool& ok){
        ok = true;
        switch(value){
            case glNever: return VK_COMPARE_OP_NEVER;
            case glLess: return VK_COMPARE_OP_LESS;
            case glEqual: return VK_COMPARE_OP_EQUAL;
            case glLequal: return VK_COMPARE_OP_LESS_OR_EQUAL;
            case glGreater: return VK_COMPARE_OP_GREATER;
            case glNotequal: return VK_COMPARE_OP_NOT_EQUAL;
            case glGequal: return VK_COMPARE_OP_GREATER_OR_EQUAL;
            case glAlways: return VK_COMPARE_OP_ALWAYS;
            default: ok = false; return VK_COMPARE_OP_ALWAYS;
        }
    }

        VkStencilOp stencilOpFromGL(int value, bool& ok){
        ok = true;
        switch(value){
            case glStencilKeep: return VK_STENCIL_OP_KEEP;
            case glStencilZero: return VK_STENCIL_OP_ZERO;
            case glStencilReplace: return VK_STENCIL_OP_REPLACE;
            case glStencilIncr: return VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case glStencilDecr: return VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case glStencilInvert: return VK_STENCIL_OP_INVERT;
            case glStencilIncrWrap: return VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case glStencilDecrWrap: return VK_STENCIL_OP_DECREMENT_AND_WRAP;
            default: ok = false; return VK_STENCIL_OP_KEEP;
        }
    }

/**
 * Creates a graphics pipeline with an explicit vertex layout. The default JNI overload uses the
 * historical six-float position+colour layout; the layout-aware overload lets the GL20 port feed
 * arbitrary Arc meshes (position, colour, UV, ...) without changing the native vertex format.
 */
static bool createPipeline(JNIEnv* env, jbyteArray vertexSpirv, jbyteArray fragmentSpirv,
        const PipelineRequest& request){
    if(!requireInitialized()) return false;
    device.lastError[0] = '\0';
    if(vertexSpirv == nullptr || fragmentSpirv == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null SPIR-V module");
        return false;
    }
    jsize vertexLength = env->GetArrayLength(vertexSpirv);
    jsize fragmentLength = env->GetArrayLength(fragmentSpirv);
    if(vertexLength <= 0 || fragmentLength <= 0 || (vertexLength & 3) != 0 || (fragmentLength & 3) != 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "SPIR-V modules must be non-empty and word aligned");
        return false;
    }
    const uint32_t stride = request.stride;
    const std::vector<Attribute>& attributes = request.attributes;
    if(stride == 0 || stride > 65535){
        std::snprintf(device.lastError, sizeof(device.lastError), "invalid vertex stride %u", stride);
        return false;
    }
    if(attributes.size() > 16){
        std::snprintf(device.lastError, sizeof(device.lastError), "too many vertex attributes (%zu)", attributes.size());
        return false;
    }
    for(const Attribute& attribute : attributes){
        if(attribute.location >= 16){
            std::snprintf(device.lastError, sizeof(device.lastError), "vertex attribute location %u is out of range", attribute.location);
            return false;
        }
        uint32_t size = attribute.format == VK_FORMAT_R32_SFLOAT ? 4
            : attribute.format == VK_FORMAT_R32G32_SFLOAT ? 8
            : attribute.format == VK_FORMAT_R32G32B32_SFLOAT ? 12 : 16;
        if(attribute.offset + size > stride){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "vertex attribute %u exceeds the %u-byte stride", attribute.location, stride);
            return false;
        }
    }
    VkPrimitiveTopology topology{};
    if(!topologyFromGL(request.primitive, topology)){
        std::snprintf(device.lastError, sizeof(device.lastError), "unsupported GL primitive %d", request.primitive);
        return false;
    }

    try{
        std::vector<uint32_t> vertexWords(static_cast<size_t>(vertexLength) / 4);
        std::vector<uint32_t> fragmentWords(static_cast<size_t>(fragmentLength) / 4);
        env->GetByteArrayRegion(vertexSpirv, 0, vertexLength, reinterpret_cast<jbyte*>(vertexWords.data()));
        if(env->ExceptionCheck()) return false;
        env->GetByteArrayRegion(fragmentSpirv, 0, fragmentLength, reinterpret_cast<jbyte*>(fragmentWords.data()));
        if(env->ExceptionCheck()) return false;

        VkShaderModuleCreateInfo shaderInfo{};
        shaderInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        shaderInfo.codeSize = static_cast<size_t>(vertexLength);
        shaderInfo.pCode = vertexWords.data();
        VkShaderModule vertexModule = VK_NULL_HANDLE;
        if(failed(api.vkCreateShaderModule(device.device, &shaderInfo, nullptr, &vertexModule),
                "vkCreateShaderModule(vertex)")) return false;
        shaderInfo.codeSize = static_cast<size_t>(fragmentLength);
        shaderInfo.pCode = fragmentWords.data();
        VkShaderModule fragmentModule = VK_NULL_HANDLE;
        VkResult fragmentResult = api.vkCreateShaderModule(device.device, &shaderInfo, nullptr, &fragmentModule);
        if(fragmentResult != VK_SUCCESS){
            failed(fragmentResult, "vkCreateShaderModule(fragment)");
            api.vkDestroyShaderModule(device.device, vertexModule, nullptr);
            return false;
        }

        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vertexModule;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = fragmentModule;
        stages[1].pName = "main";

        VkVertexInputAttributeDescription legacyAttributes[16]{};
        uint32_t legacyCount = static_cast<uint32_t>(attributes.size());
        for(uint32_t i = 0; i < legacyCount; i++){
            legacyAttributes[i].location = attributes[i].location;
            legacyAttributes[i].binding = 0;
            legacyAttributes[i].format = attributes[i].format;
            legacyAttributes[i].offset = attributes[i].offset;
        }
        VkVertexInputBindingDescription binding{};
        binding.binding = 0;
        binding.stride = stride;
        binding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
        VkPipelineVertexInputStateCreateInfo vertexInput{};
        vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
        vertexInput.vertexBindingDescriptionCount = 1;
        vertexInput.pVertexBindingDescriptions = &binding;
        vertexInput.vertexAttributeDescriptionCount = legacyCount;
        vertexInput.pVertexAttributeDescriptions = legacyAttributes;

        VkPipelineInputAssemblyStateCreateInfo assembly{};
        assembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
        assembly.topology = topology;

        uint32_t viewportWidth = device.width ? device.width : 1;
        uint32_t viewportHeight = device.height ? device.height : 1;
        VkViewport templateViewport = glViewport(viewportWidth, viewportHeight);
        VkRect2D templateScissor{{0, 0}, {viewportWidth, viewportHeight}};
        VkPipelineViewportStateCreateInfo viewport{};
        viewport.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
        viewport.viewportCount = 1;
        viewport.scissorCount = 1;
        viewport.pViewports = &templateViewport;
        viewport.pScissors = &templateScissor;

        VkPipelineRasterizationStateCreateInfo rasterization{};
        rasterization.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
        rasterization.polygonMode = VK_POLYGON_MODE_FILL;
        // 0 = none, 1 = GL_BACK, 2 = GL_FRONT, 3 = GL_FRONT_AND_BACK.
        switch(request.cullFace){
            case 1: rasterization.cullMode = VK_CULL_MODE_BACK_BIT; break;
            case 2: rasterization.cullMode = VK_CULL_MODE_FRONT_BIT; break;
            case 3: rasterization.cullMode = VK_CULL_MODE_FRONT_AND_BACK; break;
            default: rasterization.cullMode = VK_CULL_MODE_NONE; break;
        }
        rasterization.frontFace = request.frontFaceCCW ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE;
        // Line widths above 1.0 need the wideLines feature; without it Vulkan caps the value.
        rasterization.lineWidth = device.supportsWideLines ? request.lineWidth : 1.0f;

        VkPipelineMultisampleStateCreateInfo multisample{};
        multisample.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        bool factorsOk = true;
        VkBlendFactor srcRGB = blendFactorFromGL(request.blendSrcRGB, factorsOk);
        VkBlendFactor dstRGB = blendFactorFromGL(request.blendDstRGB, factorsOk);
        VkBlendFactor srcAlpha = blendFactorFromGL(request.blendSrcAlpha, factorsOk);
        VkBlendFactor dstAlpha = blendFactorFromGL(request.blendDstAlpha, factorsOk);
        VkBlendOp opRGB = blendOpFromGL(request.blendEqRGB, factorsOk);
        VkBlendOp opAlpha = blendOpFromGL(request.blendEqAlpha, factorsOk);
        VkCompareOp depthCompare = compareOpFromGL(request.depthFunc, factorsOk);
        VkStencilOp stencilFail = stencilOpFromGL(request.stencilFail, factorsOk);
        VkStencilOp stencilDepthFail = stencilOpFromGL(request.stencilDepthFail, factorsOk);
        VkStencilOp stencilDepthPass = stencilOpFromGL(request.stencilDepthPass, factorsOk);
        if(!factorsOk){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "unsupported blend, depth or stencil enum in the pipeline state");
            return false;
        }

        VkPipelineColorBlendAttachmentState colorBlend{};
        colorBlend.colorWriteMask = (request.colorMaskR ? VK_COLOR_COMPONENT_R_BIT : 0u)
            | (request.colorMaskG ? VK_COLOR_COMPONENT_G_BIT : 0u)
            | (request.colorMaskB ? VK_COLOR_COMPONENT_B_BIT : 0u)
            | (request.colorMaskA ? VK_COLOR_COMPONENT_A_BIT : 0u);
        if(request.blend){
            colorBlend.blendEnable = VK_TRUE;
            colorBlend.srcColorBlendFactor = srcRGB;
            colorBlend.dstColorBlendFactor = dstRGB;
            colorBlend.colorBlendOp = opRGB;
            colorBlend.srcAlphaBlendFactor = srcAlpha;
            colorBlend.dstAlphaBlendFactor = dstAlpha;
            colorBlend.alphaBlendOp = opAlpha;
        }
        VkPipelineColorBlendStateCreateInfo colorBlendState{};
        colorBlendState.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        colorBlendState.attachmentCount = 1;
        colorBlendState.pAttachments = &colorBlend;
        // glBlendColor feeds GL_CONSTANT_COLOR and GL_ONE_MINUS_CONSTANT_COLOR factors.
        colorBlendState.blendConstants[0] = request.blendR;
        colorBlendState.blendConstants[1] = request.blendG;
        colorBlendState.blendConstants[2] = request.blendB;
        colorBlendState.blendConstants[3] = request.blendA;

        // Depth and stencil are baked into the pipeline: the pass only defines the attachments.
        VkStencilOpState stencilState{};
        stencilState.failOp = stencilFail;
        stencilState.depthFailOp = stencilDepthFail;
        stencilState.passOp = stencilDepthPass;
        stencilState.compareOp = compareOpFromGL(request.stencilFunc, factorsOk);
        stencilState.compareMask = static_cast<uint32_t>(request.stencilMask);
        stencilState.writeMask = static_cast<uint32_t>(request.stencilMask);
        stencilState.reference = static_cast<uint32_t>(request.stencilRef);
        VkPipelineDepthStencilStateCreateInfo depthStencil{};
        depthStencil.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
        depthStencil.depthTestEnable = request.depthTest ? VK_TRUE : VK_FALSE;
        depthStencil.depthWriteEnable = request.depthWrite ? VK_TRUE : VK_FALSE;
        depthStencil.depthCompareOp = depthCompare;
        depthStencil.depthBoundsTestEnable = VK_FALSE;
        depthStencil.stencilTestEnable = request.stencilTest ? VK_TRUE : VK_FALSE;
        depthStencil.front = stencilState;
        depthStencil.back = stencilState;

        VkDynamicState dynamicStates[2] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamic{};
        dynamic.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        dynamic.dynamicStateCount = 2;
        dynamic.pDynamicStates = dynamicStates;

        VkGraphicsPipelineCreateInfo pipelineInfo{};
        pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        pipelineInfo.stageCount = 2;
        pipelineInfo.pStages = stages;
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &assembly;
        pipelineInfo.pViewportState = &viewport;
        pipelineInfo.pRasterizationState = &rasterization;
        pipelineInfo.pMultisampleState = &multisample;
        pipelineInfo.pColorBlendState = &colorBlendState;
        pipelineInfo.pDepthStencilState = &depthStencil;
        pipelineInfo.pDynamicState = &dynamic;
        pipelineInfo.layout = device.pipelineLayout;
        pipelineInfo.renderPass = activeRenderPass();
        pipelineInfo.subpass = 0;
        pipelineInfo.basePipelineIndex = -1;

        VkPipeline candidate = VK_NULL_HANDLE;
        VkResult pipelineResult = api.vkCreateGraphicsPipelines(device.device, VK_NULL_HANDLE, 1,
            &pipelineInfo, nullptr, &candidate);
        api.vkDestroyShaderModule(device.device, fragmentModule, nullptr);
        api.vkDestroyShaderModule(device.device, vertexModule, nullptr);
        if(pipelineResult != VK_SUCCESS){
            failed(pipelineResult, "vkCreateGraphicsPipelines");
            if(candidate != VK_NULL_HANDLE) api.vkDestroyPipeline(device.device, candidate, nullptr);
            return false;
        }
        invalidatePipeline();
        device.pipeline = candidate;
        device.vertexStride = request.stride;
        return true;
    }catch(const std::bad_alloc&){
        std::snprintf(device.lastError, sizeof(device.lastError), "out of memory while creating Vulkan pipeline");
        return false;
    }
}

    /** Recreates the readback buffer, e.g. when reading a target of another size. */
    bool resizeReadbackBuffer(VkDeviceSize required){
        if(device.readbackMapped != nullptr) api.vkUnmapMemory(device.device, device.readbackMemory);
        device.readbackMapped = nullptr;
        if(device.readbackBuffer != VK_NULL_HANDLE) api.vkDestroyBuffer(device.device, device.readbackBuffer, nullptr);
        if(device.readbackMemory != VK_NULL_HANDLE) api.vkFreeMemory(device.device, device.readbackMemory, nullptr);
        device.readbackBuffer = VK_NULL_HANDLE;
        device.readbackMemory = VK_NULL_HANDLE;
        device.readbackCapacity = 0;

        if(!createBuffer(required, VK_BUFFER_USAGE_TRANSFER_DST_BIT, device.readbackBuffer,
                device.readbackMemory, &device.readbackMapped)) return false;
        device.readbackCapacity = required;
        return true;
    }

/** Historical six-float layout: vec2 position at 0, vec4 colour at 8. */
static std::vector<Attribute> defaultVertexLayout(){
    return {
        {0, VK_FORMAT_R32G32_SFLOAT, 0},
        {1, VK_FORMAT_R32G32B32A32_SFLOAT, 2 * sizeof(float)}
    };
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreatePipeline(JNIEnv* env, jclass,
        jbyteArray vertexSpirv, jbyteArray fragmentSpirv, jboolean blend, jint primitive){
    return createPipeline(env, vertexSpirv, fragmentSpirv, defaultPipelineRequest(blend == JNI_TRUE,
        primitive, 6 * sizeof(float), defaultVertexLayout())) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Layout-aware variant. {@code layout} is a flat array of triples
 * {@code location, componentCount, offset} describing the interleaved vertex buffer.
 * Component counts 1..4 map to VK_FORMAT_R32[_G32[_B32[_A32]]]_SFLOAT.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreatePipelineWithLayout(JNIEnv* env, jclass,
        jbyteArray vertexSpirv, jbyteArray fragmentSpirv, jboolean blend, jint primitive,
        jint stride, jintArray layout){
    if(layout == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null vertex layout");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(layout);
    if(length == 0 || length % 3 != 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "vertex layout must be a non-empty multiple of three integers");
        return JNI_FALSE;
    }
    std::vector<jint> encoded(static_cast<size_t>(length));
    env->GetIntArrayRegion(layout, 0, length, encoded.data());
    if(env->ExceptionCheck()) return JNI_FALSE;

    std::vector<Attribute> attributes;
    attributes.reserve(static_cast<size_t>(length) / 3);
    for(size_t i = 0; i < encoded.size(); i += 3){
        bool ok = false;
        VkFormat format = attributeFormat(encoded[i + 1], ok);
        if(!ok || encoded[i] < 0 || encoded[i + 2] < 0){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "invalid vertex attribute triple (%d, %d, %d)",
                encoded[i], encoded[i + 1], encoded[i + 2]);
            return JNI_FALSE;
        }
        attributes.push_back({static_cast<uint32_t>(encoded[i]), format,
            static_cast<uint32_t>(encoded[i + 2])});
    }
    return createPipeline(env, vertexSpirv, fragmentSpirv, defaultPipelineRequest(blend == JNI_TRUE,
        primitive, stride > 0 ? static_cast<uint32_t>(stride) : 0u, std::move(attributes))) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Pipeline creation with the full frozen state. {@code state} is a flat int array whose indices and
 * meanings are mirrored by {@link VulkanDevice#pipelineState}:
 *
 * <pre>
 *  0 depthTest (0/1)            1 depthWrite (0/1)          2 depthFunc (GL enum)
 *  3 stencilTest (0/1)         4 stencilFunc (GL enum)     5 stencilRef
 *  6 stencilMask               7 stencilFail (GL enum)     8 stencilDepthFail
 *  9 stencilDepthPass         10 cullFace (0 none/1 back/2 front/3 both)
 * 11 frontFaceCCW (0/1)       12 blend (0/1)              13 blendSrcRGB (GL enum)
 * 14 blendDstRGB              15 blendSrcAlpha            16 blendDstAlpha
 * 17 blendEqRGB (GL enum)     18 blendEqAlpha             19 colorMaskR (0/1)
 * 20 colorMaskG               21 colorMaskB               22 colorMaskA
 * 23 lineWidth (thousandths)  24 blendConstantR (0..65535) 25 blendConstantG
 * 26 blendConstantB           27 blendConstantA
 * </pre>
 *
 * A malformed or partial array is rejected instead of silently defaulting fields.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreatePipelineWithState(JNIEnv* env, jclass,
        jbyteArray vertexSpirv, jbyteArray fragmentSpirv, jint primitive, jint stride,
        jintArray layout, jintArray state){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = (char)0;

    std::vector<Attribute> attributes;
    if(layout != nullptr){
        jsize length = env->GetArrayLength(layout);
        if(length == 0 || length % 3 != 0){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "vertex layout must be a non-empty multiple of three integers");
            return JNI_FALSE;
        }
        std::vector<jint> encoded(static_cast<size_t>(length));
        env->GetIntArrayRegion(layout, 0, length, encoded.data());
        if(env->ExceptionCheck()) return JNI_FALSE;
        attributes.reserve(static_cast<size_t>(length) / 3);
        for(size_t i = 0; i < encoded.size(); i += 3){
            bool formatOk = false;
            VkFormat format = attributeFormat(encoded[i + 1], formatOk);
            if(!formatOk || encoded[i] < 0 || encoded[i] >= 16 || encoded[i + 2] < 0){
                std::snprintf(device.lastError, sizeof(device.lastError),
                    "invalid vertex attribute triple (%d, %d, %d)",
                    encoded[i], encoded[i + 1], encoded[i + 2]);
                return JNI_FALSE;
            }
            attributes.push_back({static_cast<uint32_t>(encoded[i]), format,
                static_cast<uint32_t>(encoded[i + 2])});
        }
    }

    PipelineRequest request = defaultPipelineRequest(false, primitive,
        stride > 0 ? static_cast<uint32_t>(stride) : 0u, std::move(attributes));

    if(state != nullptr){
        const jsize expected = 28;
        if(env->GetArrayLength(state) != expected){
            std::snprintf(device.lastError, sizeof(device.lastError),
                "pipeline state must hold exactly %d integers", expected);
            return JNI_FALSE;
        }
        std::vector<jint> values(static_cast<size_t>(expected));
        env->GetIntArrayRegion(state, 0, expected, values.data());
        if(env->ExceptionCheck()) return JNI_FALSE;

        request.depthTest = values[0] != 0;
        request.depthWrite = values[1] != 0;
        request.depthFunc = values[2];
        request.stencilTest = values[3] != 0;
        request.stencilFunc = values[4];
        request.stencilRef = values[5];
        request.stencilMask = values[6];
        request.stencilFail = values[7];
        request.stencilDepthFail = values[8];
        request.stencilDepthPass = values[9];
        request.cullFace = values[10];
        request.frontFaceCCW = values[11] != 0;
        request.blend = values[12] != 0;
        request.blendSrcRGB = values[13];
        request.blendDstRGB = values[14];
        request.blendSrcAlpha = values[15];
        request.blendDstAlpha = values[16];
        request.blendEqRGB = values[17];
        request.blendEqAlpha = values[18];
        request.colorMaskR = values[19] != 0;
        request.colorMaskG = values[20] != 0;
        request.colorMaskB = values[21] != 0;
        request.colorMaskA = values[22] != 0;
        request.lineWidth = static_cast<float>(values[23]) / 1000.0f;
        request.blendR = static_cast<float>(values[24]) / 65535.0f;
        request.blendG = static_cast<float>(values[25]) / 65535.0f;
        request.blendB = static_cast<float>(values[26]) / 65535.0f;
        request.blendA = static_cast<float>(values[27]) / 65535.0f;
        if(request.lineWidth < 0.0f) request.lineWidth = 0.0f;
    }

    return createPipeline(env, vertexSpirv, fragmentSpirv, request) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeCreateFramebuffer(JNIEnv*, jclass, jint width, jint height,
        jboolean depth, jboolean stencil){
    if(!requireInitialized()) return 0;
    device.lastError[0] = (char)0;
    if(width <= 0 || height <= 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "invalid framebuffer size %dx%d", width, height);
        return 0;
    }
    // A windowed device owns the same default render target and the same render passes, so user
    // framebuffers work there too: the game renders into either and both reach the screen.

    // Ids start at 1; 0 always means "the default offscreen target".
    for(size_t i = 0; i < device.targets.size(); i++){
        if(!device.targets[i].inUse){
            device.lastError[0] = (char)0;
            if(!allocateRenderTarget(device.targets[i], static_cast<uint32_t>(width),
                    static_cast<uint32_t>(height), depth == JNI_TRUE, stencil == JNI_TRUE)) return 0;
            return static_cast<jint>(i + 1);
        }
    }
    if(device.targets.size() >= 32){
        std::snprintf(device.lastError, sizeof(device.lastError), "too many framebuffers (32 maximum)");
        return 0;
    }

    RenderTarget target;
    if(!allocateRenderTarget(target, static_cast<uint32_t>(width), static_cast<uint32_t>(height),
            depth == JNI_TRUE, stencil == JNI_TRUE)) return 0;
    device.targets.push_back(target);
    return static_cast<jint>(device.targets.size());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeBindFramebuffer(JNIEnv*, jclass, jint framebuffer){
    return bindFramebuffer(framebuffer) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeDeleteFramebuffer(JNIEnv*, jclass, jint framebuffer){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = (char)0;
    if(framebuffer <= 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "the default framebuffer cannot be deleted");
        return JNI_FALSE;
    }
    if(static_cast<size_t>(framebuffer) > device.targets.size() || !device.targets[framebuffer - 1].inUse){
        std::snprintf(device.lastError, sizeof(device.lastError), "framebuffer %d does not exist", framebuffer);
        return JNI_FALSE;
    }
    if(device.activeTarget == framebuffer) bindFramebuffer(0);
    if(device.sampledTarget == framebuffer){
        device.sampledKind = 0;
        device.sampledTarget = 0;
        writeDescriptorImage();
    }
    device.targets[framebuffer - 1] = RenderTarget{};
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeFramebufferStatus(JNIEnv*, jclass, jint framebuffer){
    if(!requireInitialized()) return 0;
    if(framebuffer <= 0) return device.colorView != VK_NULL_HANDLE ? 1 : 0; // GL_FRAMEBUFFER_COMPLETE
    if(static_cast<size_t>(framebuffer) > device.targets.size() || !device.targets[framebuffer - 1].inUse) return 0;
    const RenderTarget& target = device.targets[framebuffer - 1];
    if(target.colorView == VK_NULL_HANDLE || target.framebuffer == VK_NULL_HANDLE) return 0;
    return 1;
}

/**
 * Selects which image the sampler reads: 0 = the uploaded texture, greater than 0 = the color of
 * that framebuffer. Sampling an unrendered framebuffer is rejected.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeSampleFramebuffer(JNIEnv*, jclass, jint framebuffer){
    return sampleFramebuffer(framebuffer) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeClearDepthStencil(JNIEnv*, jclass, jfloat depth, jint stencil){
    if(!requireInitialized()) return;
    device.clearDepth = depth;
    device.clearStencil = stencil;
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeSetViewport(JNIEnv*, jclass, jint x, jint y, jint width, jint height){
    if(!requireInitialized()) return;
    if(width < 0 || height < 0) return;
    device.viewport = {static_cast<float>(x), static_cast<float>(y),
        static_cast<float>(width), static_cast<float>(height), 0.0f, 1.0f};
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeSetScissor(JNIEnv*, jclass, jint x, jint y, jint width, jint height){
    if(!requireInitialized()) return;
    if(width < 0 || height < 0) return;
    device.scissor = {{x, y}, static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
}

/** Waits for the queue to drain; every render path already blocks, this is a no-op safety net. */
extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeFinish(JNIEnv*, jclass){
    if(!requireInitialized()) return;
    if(api.vkQueueWaitIdle != nullptr) api.vkQueueWaitIdle(device.queue);
}

/** Dimensions of the bound target: the default offscreen size, or the framebuffer extent. */
extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeBoundWidth(JNIEnv*, jclass){
    RenderTarget* target = activeRenderTarget();
    return target ? static_cast<jint>(target->width) : static_cast<jint>(device.width);
}

extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeBoundHeight(JNIEnv*, jclass){
    RenderTarget* target = activeRenderTarget();
    return target ? static_cast<jint>(target->height) : static_cast<jint>(device.height);
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeClearColor(JNIEnv*, jclass,
        jfloat red, jfloat green, jfloat blue, jfloat alpha){
    if(!requireInitialized()) return;
    device.lastError[0] = '\0';
    device.clearColor.float32[0] = red;
    device.clearColor.float32[1] = green;
    device.clearColor.float32[2] = blue;
    device.clearColor.float32[3] = alpha;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeWriteVertices(JNIEnv* env, jclass, jfloatArray vertices){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(vertices == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null vertex data");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(vertices);
    if(static_cast<VkDeviceSize>(length) * sizeof(jfloat) > device.vertexCapacity){
        std::snprintf(device.lastError, sizeof(device.lastError), "vertex data exceeds native buffer capacity");
        return JNI_FALSE;
    }
    if(length > 0){
        env->GetFloatArrayRegion(vertices, 0, length, static_cast<jfloat*>(device.vertexMapped));
        if(env->ExceptionCheck()) return JNI_FALSE;
    }
    device.vertexBytes = static_cast<VkDeviceSize>(length) * sizeof(jfloat);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeWriteUniforms(JNIEnv* env, jclass, jbyteArray data){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(data == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null uniform data");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(data);
    if(static_cast<VkDeviceSize>(length) > device.uniformRange){
        std::snprintf(device.lastError, sizeof(device.lastError), "uniform data exceeds native buffer range");
        return JNI_FALSE;
    }
    std::memset(device.uniformMapped, 0, static_cast<size_t>(Device::uniformCapacity));
    if(length > 0){
        env->GetByteArrayRegion(data, 0, length, static_cast<jbyte*>(device.uniformMapped));
        if(env->ExceptionCheck()) return JNI_FALSE;
    }
    return JNI_TRUE;
}

/** Records one frame. {@code indexCount == 0} draws {@code vertexCount} vertices. */
static bool renderFrame(uint32_t vertexCount, uint32_t indexCount, bool clear){
    if((vertexCount > 0 || indexCount > 0) && device.pipeline == VK_NULL_HANDLE){
        std::snprintf(device.lastError, sizeof(device.lastError), "no graphics pipeline is active");
        return false;
    }
    if(!beginCommand()) return false;
    RenderTarget* target = activeRenderTarget();
    VkImage colorImage = target ? target->colorImage : device.colorImage;
    VkFramebuffer framebuffer = target ? target->framebuffer : device.framebuffer;
    VkRenderPass pass = target ? target->pass : device.offscreenPass;
    uint32_t width = target ? target->width : device.width;
    uint32_t height = target ? target->height : device.height;
    VkImageLayout entryLayout = target ? target->colorLayout
        : (device.colorInitialized ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED);
    VkFormat depthFormat = target ? device.targetDepthFormat
        : (device.hasDepth ? device.depthFormat : VK_FORMAT_UNDEFINED);
    VkImageAspectFlags depthAspectBits = depthFormat == VK_FORMAT_UNDEFINED ? 0 : depthAspect(depthFormat);
    transitionColor(colorImage, entryLayout, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
        entryLayout == VK_IMAGE_LAYOUT_UNDEFINED ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                                                 : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);



    VkClearValue clearValues[2]{};
    clearValues[0].color = device.clearColor;
    clearValues[1].depthStencil = {device.clearDepth, static_cast<uint32_t>(device.clearStencil)};
    VkRenderPassBeginInfo renderInfo{};
    renderInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderInfo.renderPass = pass;
    renderInfo.framebuffer = framebuffer;
    renderInfo.renderArea = {{0, 0}, {width, height}};
    renderInfo.clearValueCount = depthFormat == VK_FORMAT_UNDEFINED ? 1u : 2u;
    renderInfo.pClearValues = clearValues;
    api.vkCmdBeginRenderPass(device.commandBuffer, &renderInfo, VK_SUBPASS_CONTENTS_INLINE);
    if(clear){
        // The pass loads previous contents, so glClear works per attachment: color, then depth
        // and stencil when the target has them.
        VkClearAttachment attachments[2]{};
        uint32_t attachmentCount = 0;
        attachments[attachmentCount].aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        attachments[attachmentCount].colorAttachment = 0;
        attachments[attachmentCount].clearValue.color = device.clearColor;
        attachmentCount++;
        if(depthAspectBits != 0){
            attachments[attachmentCount].aspectMask = depthAspectBits;
            attachments[attachmentCount].clearValue.depthStencil = {device.clearDepth,
                static_cast<uint32_t>(device.clearStencil)};
            attachmentCount++;
        }
        VkClearRect rect{};
        rect.rect = {{0, 0}, {width, height}};
        rect.baseArrayLayer = 0;
        rect.layerCount = 1;
        api.vkCmdClearAttachments(device.commandBuffer, attachmentCount, attachments, 1, &rect);
    }
    if(vertexCount > 0 || indexCount > 0){
        // Viewport and scissor always cover the bound target, never the default offscreen size.
        VkViewport targetViewport = glViewport(width, height);
        VkRect2D targetScissor{{0, 0}, {width, height}};
        api.vkCmdSetViewport(device.commandBuffer, 0, 1, &targetViewport);
        api.vkCmdSetScissor(device.commandBuffer, 0, 1, &targetScissor);
        api.vkCmdBindPipeline(device.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, device.pipeline);
        api.vkCmdBindDescriptorSets(device.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
            device.pipelineLayout, 0, 1, &device.descriptorSet, 0, nullptr);
        VkDeviceSize offset = 0;
        api.vkCmdBindVertexBuffers(device.commandBuffer, 0, 1, &device.vertexBuffer, &offset);
        if(indexCount > 0){
            api.vkCmdBindIndexBuffer(device.commandBuffer, device.indexBuffer, 0, VK_INDEX_TYPE_UINT32);
            api.vkCmdDrawIndexed(device.commandBuffer, indexCount, 1, 0, 0, 0);
        }else{
            api.vkCmdDraw(device.commandBuffer, vertexCount, 1, 0, 0);
        }
    }
    api.vkCmdEndRenderPass(device.commandBuffer);

    if(target){
        // User framebuffers end in SHADER_READ_ONLY_OPTIMAL so their color can be sampled.
        target->rendered = true;
        target->colorLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return false;
        if(device.sampledKind == 1 && device.sampledTarget == device.activeTarget){
            writeDescriptorImage();
        }
        return true;
    }

    VkBufferImageCopy copy{};
    copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copy.imageExtent = {width, height, 1};
    api.vkCmdCopyImageToBuffer(device.commandBuffer, colorImage,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, device.readbackBuffer, 1, &copy);
    if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return false;
    device.colorInitialized = true;
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeRender(JNIEnv*, jclass, jint vertexCount, jboolean clear){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(vertexCount < 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "negative vertex count");
        return JNI_FALSE;
    }
    if(static_cast<VkDeviceSize>(vertexCount) * device.vertexStride > device.vertexBytes){
        std::snprintf(device.lastError, sizeof(device.lastError), "vertex count exceeds uploaded vertex data");
        return JNI_FALSE;
    }
    return renderFrame(static_cast<uint32_t>(vertexCount), 0, clear == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeWriteIndices(JNIEnv* env, jclass, jintArray indices){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(indices == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null index data");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(indices);
    if(static_cast<VkDeviceSize>(length) * sizeof(jint) > device.indexCapacity){
        std::snprintf(device.lastError, sizeof(device.lastError), "index data exceeds native buffer capacity");
        return JNI_FALSE;
    }
    if(length > 0){
        env->GetIntArrayRegion(indices, 0, length, static_cast<jint*>(device.indexMapped));
        if(env->ExceptionCheck()) return JNI_FALSE;
    }
    device.indexBytes = static_cast<VkDeviceSize>(length) * sizeof(jint);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeRenderIndexed(JNIEnv*, jclass, jint indexCount, jboolean clear){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(indexCount < 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "negative index count");
        return JNI_FALSE;
    }
    if(static_cast<VkDeviceSize>(indexCount) * sizeof(jint) > device.indexBytes){
        std::snprintf(device.lastError, sizeof(device.lastError), "index count exceeds uploaded index data");
        return JNI_FALSE;
    }
    return renderFrame(0, static_cast<uint32_t>(indexCount), clear == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeReadPixels(JNIEnv* env, jclass, jbyteArray output){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(output == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null pixel buffer");
        return JNI_FALSE;
    }
    RenderTarget* target = activeRenderTarget();
    VkImage colorImage = target ? target->colorImage : device.colorImage;
    uint32_t width = target ? target->width : device.width;
    uint32_t height = target ? target->height : device.height;
    bool hasFrame = target ? target->rendered : device.colorInitialized;
    if(!hasFrame){
        std::snprintf(device.lastError, sizeof(device.lastError), "no rendered frame is available for readback");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(output);
    VkDeviceSize required = static_cast<VkDeviceSize>(width) * static_cast<VkDeviceSize>(height) * 4;
    if(static_cast<VkDeviceSize>(length) != required){
        std::snprintf(device.lastError, sizeof(device.lastError), "pixel buffer length does not match the bound target");
        return JNI_FALSE;
    }
    if(device.readbackCapacity != required && !resizeReadbackBuffer(required)) return JNI_FALSE;

    if(target){
        // The pass left the image sampleable; the copy needs TRANSFER_SRC_OPTIMAL, and the image
        // goes back to sampleable afterwards so the framebuffer stays usable as a texture.
        if(!beginCommand()) return JNI_FALSE;
        transitionColor(colorImage, target->colorLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        VkBufferImageCopy readbackCopy{};
        readbackCopy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        readbackCopy.imageExtent = {width, height, 1};
        api.vkCmdCopyImageToBuffer(device.commandBuffer, colorImage,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, device.readbackBuffer, 1, &readbackCopy);
        transitionColor(colorImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return JNI_FALSE;
        target->colorLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    }

    env->SetByteArrayRegion(output, 0, length, static_cast<const jbyte*>(device.readbackMapped));
    return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
}

/// Finds the Win32 handle of an SDL window by its title, so a Vulkan surface can be created
/// for a window the game already owns. Returns 0 when no such window exists.
extern "C" JNIEXPORT jlong JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeFindWindow(JNIEnv* env, jclass, jstring title){
    if(title == nullptr) return 0;
    const char* name = env->GetStringUTFChars(title, nullptr);
    if(name == nullptr) return 0;
    HWND handle = FindWindowA("SDL_app", name);
    env->ReleaseStringUTFChars(title, name);
    return reinterpret_cast<jlong>(reinterpret_cast<void*>(handle));
}

/// HINSTANCE of this process, the other half of a Win32 surface creation.
extern "C" JNIEXPORT jlong JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeModuleHandle(JNIEnv*, jclass){
    return reinterpret_cast<jlong>(reinterpret_cast<void*>(GetModuleHandle(nullptr)));
}

/// Index of the frame most recently acquired from the swapchain, or -1 when idle.
extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeImageIndex(JNIEnv*, jclass){
    if(device.swapchain == VK_NULL_HANDLE) return -1;
    //A frame is only "acquired" between vkAcquireNextImageKHR and the present that follows it.
    return device.frameOpen ? static_cast<jint>(device.imageIndex) : static_cast<jint>(device.imageIndex);
}

/// Images in the live swapchain, or 0 when there is none.
extern "C" JNIEXPORT jint JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeImageCount(JNIEnv*, jclass){
    if(device.swapchain == VK_NULL_HANDLE) return 0;
    return static_cast<jint>(device.swapImages.size());
}

/// Draws into the swapchain, presents, and copies the presented image back so a headless
/// check can verify what actually reached the screen.
extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativePresentAndReadBack(JNIEnv* env, jclass, jfloat red, jfloat green,
        jfloat blue, jfloat alpha, jbyteArray output){
    if(!Java_orbital_desktop_vulkan_VulkanDevice_nativeClearAndPresent(nullptr, nullptr, red, green, blue, alpha)){
        return JNI_FALSE;
    }

    if(output == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null pixel buffer");
        return JNI_FALSE;
    }

    jsize length = env->GetArrayLength(output);
    VkDeviceSize expected = static_cast<VkDeviceSize>(device.width) * device.height * 4;
    if(static_cast<VkDeviceSize>(length) != expected){
        std::snprintf(device.lastError, sizeof(device.lastError), "pixel buffer length does not match the swapchain");
        return JNI_FALSE;
    }

    VkImage image = device.swapImages[device.imageIndex];

    //the readback buffer is sized for the offscreen target, so it has to grow before the copy is
    //recorded: a copy into a smaller buffer would overflow it
    if(device.readbackCapacity != expected && !resizeReadbackBuffer(expected)) return JNI_FALSE;

    if(!beginCommand()) return JNI_FALSE;
    transitionColor(image, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    VkBufferImageCopy copy{};
    copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copy.imageExtent = {device.width, device.height, 1};
    api.vkCmdCopyImageToBuffer(device.commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        device.readbackBuffer, 1, &copy);

    transitionColor(image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return JNI_FALSE;

    env->SetByteArrayRegion(output, 0, length, static_cast<const jbyte*>(device.readbackMapped));
    return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
}


    /**
     * Presents the frame the application rendered. The active render target (the default
     * offscreen image, or a user framebuffer) is blitted into the acquired swapchain image and
     * the result is presented, which is the counterpart of an OpenGL buffer swap: the game keeps
     * drawing into its own target exactly as it does on the offscreen path.
     *
     * A target that was never rendered is cleared first, so an untouched first frame shows the
     * clear colour instead of uninitialised memory.
     */
    bool presentRenderedFrame(){
        if(!requireInitialized()) return false;
        if(!device.windowed){
            std::snprintf(device.lastError, sizeof(device.lastError), "present requires a windowed Vulkan device");
            return false;
        }
        if(api.vkCmdBlitImage == nullptr){
            std::snprintf(device.lastError, sizeof(device.lastError), "vkCmdBlitImage is unavailable in the packaged driver");
            return false;
        }
        if(device.acquireSemaphore == VK_NULL_HANDLE || device.renderSemaphore == VK_NULL_HANDLE){
            std::snprintf(device.lastError, sizeof(device.lastError), "window presentation semaphores are unavailable");
            return false;
        }
        if(device.swapchain == VK_NULL_HANDLE && !recreateWindowTarget()) return false;

        RenderTarget* target = activeRenderTarget();
        VkImage source = target ? target->colorImage : device.colorImage;
        if(source == VK_NULL_HANDLE){
            std::snprintf(device.lastError, sizeof(device.lastError), "no render target has contents to present");
            return false;
        }

        VkResult acquireResult = api.vkAcquireNextImageKHR(device.device, device.swapchain, UINT64_MAX,
            device.acquireSemaphore, VK_NULL_HANDLE, &device.imageIndex);
        if(acquireResult == VK_ERROR_OUT_OF_DATE_KHR){
            if(!recreateWindowTarget()) return false;
            std::snprintf(device.lastError, sizeof(device.lastError),
                "swapchain was out of date; target recreated, retry present");
            return false;
        }
        if(acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR){
            failed(acquireResult, "vkAcquireNextImageKHR");
            replaceWindowSemaphores();
            return false;
        }
        bool recreateAfterPresent = acquireResult == VK_SUBOPTIMAL_KHR;

        if(!beginCommand()){
            replaceWindowSemaphores();
            return false;
        }

        // An untouched default target is cleared here, so the first presented frame is never garbage.
        if(!target && !device.colorInitialized){
            if(!renderFrame(0, 0, true)) return false;
            if(!beginCommand()) return false;
        }

        uint32_t sourceWidth = target ? target->width : device.width;
        uint32_t sourceHeight = target ? target->height : device.height;
        VkImageLayout sourceLayout = target ? target->colorLayout
            : (device.colorInitialized ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED);
        VkPipelineStageFlags sourceStage = sourceLayout == VK_IMAGE_LAYOUT_UNDEFINED
            ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

        transition(source, sourceLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            0, VK_ACCESS_TRANSFER_READ_BIT, sourceStage, VK_PIPELINE_STAGE_TRANSFER_BIT);
        transition(device.swapImages[device.imageIndex], VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, VK_ACCESS_TRANSFER_WRITE_BIT,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

        // Blit rather than copy: the offscreen target is RGBA8 and the swapchain may be BGRA8,
        // and only a blit is allowed to convert between formats.
        VkImageBlit blit{};
        blit.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        blit.srcOffsets[0] = {0, 0, 0};
        blit.srcOffsets[1] = {static_cast<int32_t>(sourceWidth), static_cast<int32_t>(sourceHeight), 1};
        blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        blit.dstOffsets[0] = {0, 0, 0};
        blit.dstOffsets[1] = {static_cast<int32_t>(device.width), static_cast<int32_t>(device.height), 1};
        api.vkCmdBlitImage(device.commandBuffer, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            device.swapImages[device.imageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_LINEAR);

        transition(device.swapImages[device.imageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_ACCESS_TRANSFER_WRITE_BIT, 0,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);

        if(!submitCommand(device.acquireSemaphore, device.renderSemaphore)){
            replaceWindowSemaphores();
            return false;
        }

        if(target){
            // The target keeps a readable layout so it can be sampled again next frame.
            target->colorLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        }

        VkPresentInfoKHR presentInfo{};
        presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &device.renderSemaphore;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &device.swapchain;
        presentInfo.pImageIndices = &device.imageIndex;
        VkResult presentResult = api.vkQueuePresentKHR(device.queue, &presentInfo);
        if(failed(api.vkQueueWaitIdle(device.queue), "vkQueueWaitIdle(present)")){
            device.initialized = false;
            return false;
        }

        if(presentResult == VK_ERROR_OUT_OF_DATE_KHR){
            if(!replaceWindowSemaphores() || !recreateWindowTarget()) return false;
            std::snprintf(device.lastError, sizeof(device.lastError),
                "swapchain became out of date during presentation; target recreated, retry present");
            return false;
        }
        if(presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR){
            failed(presentResult, "vkQueuePresentKHR");
            replaceWindowSemaphores();
            return false;
        }
        if(presentResult == VK_SUBOPTIMAL_KHR) recreateAfterPresent = true;
        if(recreateAfterPresent && !recreateWindowTarget()) return false;
        return true;
    }
extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeHasRenderTarget(JNIEnv*, jclass){
    return (device.initialized && device.colorImage != VK_NULL_HANDLE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeSetIcdPath(JNIEnv* env, jclass, jstring path){
    if(path == nullptr) return;
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if(utf == nullptr) return;
    // The Vulkan loader resolves its ICD list when vkCreateInstance runs, not when the library
    // is mapped, so this has to happen before the device is created.
    SetEnvironmentVariableA("VK_ICD_FILENAMES", utf);
    env->ReleaseStringUTFChars(path, utf);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativePresentFrame(JNIEnv*, jclass){
    return presentRenderedFrame() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeLastError(JNIEnv* env, jclass){
    return env->NewStringUTF(device.lastError);
}

extern "C" JNIEXPORT jstring JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeDeviceName(JNIEnv* env, jclass){
    if(!device.initialized || device.deviceName[0] == '\0') return nullptr;
    return env->NewStringUTF(device.deviceName);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeSetVSync(JNIEnv*, jclass, jboolean enabled){
    if(!requireInitialized()) return JNI_FALSE;
    if(!device.windowed){
        std::snprintf(device.lastError, sizeof(device.lastError), "VSync only applies to windowed Vulkan devices");
        return JNI_FALSE;
    }
    bool vSync = (enabled == JNI_TRUE);
    if(device.vSync == vSync) return JNI_TRUE;
    device.vSync = vSync;
    return recreateWindowTarget() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeResizeWindow(JNIEnv*, jclass, jint width, jint height){
    if(!requireInitialized()) return JNI_FALSE;
    if(!device.windowed){
        std::snprintf(device.lastError, sizeof(device.lastError), "resizeWindow only applies to windowed Vulkan devices");
        return JNI_FALSE;
    }
    if(width <= 0 || height <= 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "invalid window dimensions %dx%d", width, height);
        return JNI_FALSE;
    }
    device.width = static_cast<uint32_t>(width);
    device.height = static_cast<uint32_t>(height);
    return recreateWindowTarget() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_orbital_desktop_vulkan_VulkanDevice_nativeUploadTexture(JNIEnv* env, jclass, jint width, jint height, jbyteArray rgba){
    if(!requireInitialized()) return JNI_FALSE;
    device.lastError[0] = '\0';
    if(width <= 0 || height <= 0){
        std::snprintf(device.lastError, sizeof(device.lastError), "invalid texture dimensions %dx%d", width, height);
        return JNI_FALSE;
    }
    if(rgba == nullptr){
        std::snprintf(device.lastError, sizeof(device.lastError), "null texture pixel buffer");
        return JNI_FALSE;
    }
    jsize length = env->GetArrayLength(rgba);
    VkDeviceSize requiredBytes = static_cast<VkDeviceSize>(width) * static_cast<VkDeviceSize>(height) * 4;
    if(static_cast<VkDeviceSize>(length) != requiredBytes){
        std::snprintf(device.lastError, sizeof(device.lastError), "texture buffer length mismatch (expected %llu, got %d)",
            static_cast<unsigned long long>(requiredBytes), length);
        return JNI_FALSE;
    }
    if(requiredBytes > device.stagingCapacity){
        std::snprintf(device.lastError, sizeof(device.lastError), "texture size %llu exceeds staging capacity %llu",
            static_cast<unsigned long long>(requiredBytes), static_cast<unsigned long long>(device.stagingCapacity));
        return JNI_FALSE;
    }

    env->GetByteArrayRegion(rgba, 0, length, static_cast<jbyte*>(device.stagingMapped));
    if(env->ExceptionCheck()) return JNI_FALSE;

    VkDevice d = device.device;
    if(device.textureView != VK_NULL_HANDLE) api.vkDestroyImageView(d, device.textureView, nullptr);
    if(device.textureImage != VK_NULL_HANDLE) api.vkDestroyImage(d, device.textureImage, nullptr);
    if(device.textureMemory != VK_NULL_HANDLE) api.vkFreeMemory(d, device.textureMemory, nullptr);
    device.textureView = VK_NULL_HANDLE;
    device.textureImage = VK_NULL_HANDLE;
    device.textureMemory = VK_NULL_HANDLE;

    if(!createImage(static_cast<uint32_t>(width), static_cast<uint32_t>(height), VK_FORMAT_R8G8B8A8_UNORM,
            VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
            device.textureImage, device.textureMemory)) return JNI_FALSE;

    device.textureView = createImageView(device.textureImage, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
    if(device.textureView == VK_NULL_HANDLE) return JNI_FALSE;

    if(!beginCommand()) return JNI_FALSE;
    transition(device.textureImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    VkBufferImageCopy copy{};
    copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copy.imageExtent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1};
    api.vkCmdCopyBufferToImage(device.commandBuffer, device.stagingBuffer, device.textureImage,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copy);

    transition(device.textureImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

    if(!submitCommand(VK_NULL_HANDLE, VK_NULL_HANDLE)) return JNI_FALSE;

    writeDescriptors();
    return JNI_TRUE;
}
