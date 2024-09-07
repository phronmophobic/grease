//
//  Renderer.m
//  TestSkia
//
//  Created by Adrian Smith on 6/11/21.
//

#include "GrDirectContext.h"
#include "gl/GrGLInterface.h"
#include "SkData.h"
#include "SkImage.h"
#include "SkStream.h"
#include "SkSurface.h"
#include "SkCanvas.h"
#include "SkPaint.h"
#include "SkTextBlob.h"
#include "SkFont.h"

#include "skia.h"
#include "bb.h"
#import "MembraneView.h"

#import <simd/simd.h>
#import <ModelIO/ModelIO.h>

#import "Renderer.h"

// Include header shared between C code here, which executes Metal API commands, and .metal files
#import "ShaderTypes.h"

#import <CoreMotion/CoreMotion.h>

graal_isolate_t *isolate = NULL;
graal_isolatethread_t *thread = NULL;
static const NSUInteger MaxBuffersInFlight = 3;

sk_sp<SkSurface> SkMtkViewToSurface(MTKView* mtkView, GrRecordingContext* rContext) {
    if (!rContext ||
        MTLPixelFormatDepth32Float_Stencil8 != [mtkView depthStencilPixelFormat] ||
        MTLPixelFormatBGRA8Unorm != [mtkView colorPixelFormat]) {
        return nullptr;
    }

    const SkColorType colorType = kBGRA_8888_SkColorType;  // MTLPixelFormatBGRA8Unorm
    sk_sp<SkColorSpace> colorSpace = nullptr;  // MTLPixelFormatBGRA8Unorm
    const GrSurfaceOrigin origin = kTopLeft_GrSurfaceOrigin;
    const SkSurfaceProps surfaceProps;
    int sampleCount = (int)[mtkView sampleCount];

    return SkSurface::MakeFromMTKView(rContext, (__bridge GrMTLHandle)mtkView, origin, sampleCount,
                                      colorType, colorSpace, &surfaceProps);
}

void testDraw(SkCanvas* canvas){
    canvas->clear(SK_ColorWHITE);
    
    SkPaint fillPaint;
    SkPaint strokePaint;
    strokePaint.setStyle(SkPaint::kStroke_Style);
    strokePaint.setStrokeWidth(3.0f);


    canvas->drawRect(SkRect::MakeXYWH(10, 10, 60, 20), fillPaint);
    canvas->drawRect(SkRect::MakeXYWH(80, 10, 60, 20), strokePaint);

    strokePaint.setStrokeWidth(5.0f);
    canvas->drawOval(SkRect::MakeXYWH(150, 10, 60, 20), strokePaint);

    sk_sp<SkTextBlob> blob = SkTextBlob::MakeFromString("SKIA", SkFont(nullptr, 80));

    fillPaint.setColor(SkColorSetARGB(0xFF, 0xFF, 0x00, 0x00));
    canvas->drawTextBlob(blob.get(), 20, 120, fillPaint);

    fillPaint.setColor(SkColorSetARGB(0xFF, 0x00, 0x00, 0xFF));
    canvas->drawTextBlob(blob.get(), 20, 220, fillPaint);
    

}


@implementation Renderer
{
    dispatch_semaphore_t _inFlightSemaphore;
    id <MTLDevice> _device;
    id <MTLCommandQueue> _commandQueue;

    id <MTLBuffer> _dynamicUniformBuffer[MaxBuffersInFlight];
    id <MTLRenderPipelineState> _pipelineState;
    id <MTLDepthStencilState> _depthState;
    id <MTLTexture> _colorMap;
    MTLVertexDescriptor *_mtlVertexDescriptor;

    uint8_t _uniformBufferIndex;

    matrix_float4x4 _projectionMatrix;

    float _rotation;

    MTKMesh *_mesh;
    
    sk_sp<GrDirectContext> grContext;
//    sk_sp<SkSurface> surface;
}

-(nonnull instancetype)initWithMetalKitView:(nonnull MTKView *)view;
{
    self = [super init];
    if(self)
    {
        _device = view.device;
//        _inFlightSemaphore = dispatch_semaphore_create(MaxBuffersInFlight);
        grContext= nullptr;
        [self _loadMetalWithView:view];
        

        if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
          fprintf(stderr, "initialization error\n");
        }
        
        ((MembraneView*)view).isolate = isolate;
        ((MembraneView*)view).thread = thread;
        clj_init(thread);
        

    }

    return self;
}

- (void)_loadMetalWithView:(nonnull MTKView *)view;
{
    /// Load Metal state objects and initialize renderer dependent view properties

//    view.depthStencilPixelFormat = MTLPixelFormatDepth32Float_Stencil8;
//    view.colorPixelFormat = MTLPixelFormatBGRA8Unorm_sRGB;
//    view.sampleCount = 1;
    
    [view setDepthStencilPixelFormat:MTLPixelFormatDepth32Float_Stencil8];
    [view setColorPixelFormat:MTLPixelFormatBGRA8Unorm];
    [view setSampleCount:1];
    
    
    
//    const GrMtlBackendContext interface;
    // Leaving interface as null makes Skia extract pointers to OpenGL functions for the current
    // context in a platform-specific way. Alternatively, you may create your own GrGLInterface and
    // initialize it however you like to attach to an alternate OpenGL implementation or intercept
    // Skia's OpenGL calls.
    _commandQueue = [_device newCommandQueue];
    sk_sp<GrDirectContext> context = GrDirectContext::MakeMetal((void*)CFBridgingRetain(_device), (void*)CFBridgingRetain(_commandQueue));
    grContext = context;


//    _mtlVertexDescriptor = [[MTLVertexDescriptor alloc] init];
//
//    _mtlVertexDescriptor.attributes[VertexAttributePosition].format = MTLVertexFormatFloat3;
//    _mtlVertexDescriptor.attributes[VertexAttributePosition].offset = 0;
//    _mtlVertexDescriptor.attributes[VertexAttributePosition].bufferIndex = BufferIndexMeshPositions;
//
//    _mtlVertexDescriptor.attributes[VertexAttributeTexcoord].format = MTLVertexFormatFloat2;
//    _mtlVertexDescriptor.attributes[VertexAttributeTexcoord].offset = 0;
//    _mtlVertexDescriptor.attributes[VertexAttributeTexcoord].bufferIndex = BufferIndexMeshGenerics;
//
//    _mtlVertexDescriptor.layouts[BufferIndexMeshPositions].stride = 12;
//    _mtlVertexDescriptor.layouts[BufferIndexMeshPositions].stepRate = 1;
//    _mtlVertexDescriptor.layouts[BufferIndexMeshPositions].stepFunction = MTLVertexStepFunctionPerVertex;
//
//    _mtlVertexDescriptor.layouts[BufferIndexMeshGenerics].stride = 8;
//    _mtlVertexDescriptor.layouts[BufferIndexMeshGenerics].stepRate = 1;
//    _mtlVertexDescriptor.layouts[BufferIndexMeshGenerics].stepFunction = MTLVertexStepFunctionPerVertex;
//
//    id<MTLLibrary> defaultLibrary = [_device newDefaultLibrary];
//
//    id <MTLFunction> vertexFunction = [defaultLibrary newFunctionWithName:@"vertexShader"];
//
//    id <MTLFunction> fragmentFunction = [defaultLibrary newFunctionWithName:@"fragmentShader"];
//
//    MTLRenderPipelineDescriptor *pipelineStateDescriptor = [[MTLRenderPipelineDescriptor alloc] init];
//    pipelineStateDescriptor.label = @"MyPipeline";
//    pipelineStateDescriptor.sampleCount = view.sampleCount;
//    pipelineStateDescriptor.vertexFunction = vertexFunction;
//    pipelineStateDescriptor.fragmentFunction = fragmentFunction;
//    pipelineStateDescriptor.vertexDescriptor = _mtlVertexDescriptor;
//    pipelineStateDescriptor.colorAttachments[0].pixelFormat = view.colorPixelFormat;
//    pipelineStateDescriptor.depthAttachmentPixelFormat = view.depthStencilPixelFormat;
//    pipelineStateDescriptor.stencilAttachmentPixelFormat = view.depthStencilPixelFormat;
//
//    NSError *error = NULL;
//    _pipelineState = [_device newRenderPipelineStateWithDescriptor:pipelineStateDescriptor error:&error];
//    if (!_pipelineState)
//    {
//        NSLog(@"Failed to created pipeline state, error %@", error);
//    }
//
//    MTLDepthStencilDescriptor *depthStateDesc = [[MTLDepthStencilDescriptor alloc] init];
//    depthStateDesc.depthCompareFunction = MTLCompareFunctionLess;
//    depthStateDesc.depthWriteEnabled = YES;
//    _depthState = [_device newDepthStencilStateWithDescriptor:depthStateDesc];
//
//    for(NSUInteger i = 0; i < MaxBuffersInFlight; i++)
//    {
//        _dynamicUniformBuffer[i] = [_device newBufferWithLength:sizeof(Uniforms)
//                                                        options:MTLResourceStorageModeShared];
//
//        _dynamicUniformBuffer[i].label = @"UniformBuffer";
//    }
//

}

- (void)_loadAssets
{
    /// Load assets into metal objects

    NSError *error;

    MTKMeshBufferAllocator *metalAllocator = [[MTKMeshBufferAllocator alloc]
                                              initWithDevice: _device];

    MDLMesh *mdlMesh = [MDLMesh newBoxWithDimensions:(vector_float3){4, 4, 4}
                                            segments:(vector_uint3){2, 2, 2}
                                        geometryType:MDLGeometryTypeTriangles
                                       inwardNormals:NO
                                           allocator:metalAllocator];

    MDLVertexDescriptor *mdlVertexDescriptor =
    MTKModelIOVertexDescriptorFromMetal(_mtlVertexDescriptor);

    mdlVertexDescriptor.attributes[VertexAttributePosition].name  = MDLVertexAttributePosition;
    mdlVertexDescriptor.attributes[VertexAttributeTexcoord].name  = MDLVertexAttributeTextureCoordinate;

    mdlMesh.vertexDescriptor = mdlVertexDescriptor;

    _mesh = [[MTKMesh alloc] initWithMesh:mdlMesh
                                   device:_device
                                    error:&error];

    if(!_mesh || error)
    {
        NSLog(@"Error creating MetalKit mesh %@", error.localizedDescription);
    }

    MTKTextureLoader* textureLoader = [[MTKTextureLoader alloc] initWithDevice:_device];

    NSDictionary *textureLoaderOptions =
    @{
      MTKTextureLoaderOptionTextureUsage       : @(MTLTextureUsageShaderRead),
      MTKTextureLoaderOptionTextureStorageMode : @(MTLStorageModePrivate)
      };

    _colorMap = [textureLoader newTextureWithName:@"ColorMap"
                                      scaleFactor:1.0
                                           bundle:nil
                                          options:textureLoaderOptions
                                            error:&error];

    if(!_colorMap || error)
    {
        NSLog(@"Error creating texture %@", error.localizedDescription);
    }
}

- (void)_updateGameState
{
    /// Update any game state before encoding renderint commands to our drawable

    Uniforms * uniforms = (Uniforms*)_dynamicUniformBuffer[_uniformBufferIndex].contents;

    uniforms->projectionMatrix = _projectionMatrix;

    vector_float3 rotationAxis = {1, 1, 0};
    matrix_float4x4 modelMatrix = matrix4x4_rotation(_rotation, rotationAxis);
    matrix_float4x4 viewMatrix = matrix4x4_translation(0.0, 0.0, -8.0);

    uniforms->modelViewMatrix = matrix_multiply(viewMatrix, modelMatrix);

    _rotation += .01;
}

- (void)drawInMTKView:(nonnull MTKView *)view
{
    /// Per frame updates here

//    dispatch_semaphore_wait(_inFlightSemaphore, DISPATCH_TIME_FOREVER);
//
//    _uniformBufferIndex = (_uniformBufferIndex + 1) % MaxBuffersInFlight;
//
//    id <MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
//    commandBuffer.label = @"MyCommand";
//
//    __block dispatch_semaphore_t block_sema = _inFlightSemaphore;
//    [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> buffer)
//     {
//         dispatch_semaphore_signal(block_sema);
//     }];

//    [self _updateGameState];
    if (!clj_needs_redraw(thread)){
        return;
    }

    
    sk_sp<SkSurface> surface = SkMtkViewToSurface(view, grContext.get());
    CGFloat contentScale = [[UIScreen mainScreen] nativeScale];
    surface->getCanvas()->scale(contentScale, contentScale);

    if (!surface) {
        NSLog(@"error: no sksurface");
        return;
    }
    
    SkiaResource resource(grContext, surface);
//surface->getCanvas()->clear(SK_ColorWHITE);
    
//    testDraw(surface->getCanvas());
    
    clj_draw(thread,&resource);

    // Must flush *and* present for this to work!
    surface->flushAndSubmit();

    id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
    [commandBuffer presentDrawable:[view currentDrawable]];
    [commandBuffer commit];

    
   
    


    /// Delay getting the currentRenderPassDescriptor until absolutely needed. This avoids
    ///   holding onto the drawable and blocking the display pipeline any longer than necessary
//    MTLRenderPassDescriptor* renderPassDescriptor = view.currentRenderPassDescriptor;
//
//    if(renderPassDescriptor != nil)
//    {
//        /// Final pass rendering code here
//
//        id <MTLRenderCommandEncoder> renderEncoder =
//        [commandBuffer renderCommandEncoderWithDescriptor:renderPassDescriptor];
//        renderEncoder.label = @"MyRenderEncoder";
//
//        [renderEncoder pushDebugGroup:@"DrawBox"];
//
//        [renderEncoder setFrontFacingWinding:MTLWindingCounterClockwise];
//        [renderEncoder setCullMode:MTLCullModeBack];
//        [renderEncoder setRenderPipelineState:_pipelineState];
//        [renderEncoder setDepthStencilState:_depthState];
//
//        [renderEncoder setVertexBuffer:_dynamicUniformBuffer[_uniformBufferIndex]
//                                offset:0
//                               atIndex:BufferIndexUniforms];
//
//        [renderEncoder setFragmentBuffer:_dynamicUniformBuffer[_uniformBufferIndex]
//                                  offset:0
//                                 atIndex:BufferIndexUniforms];
//
//        for (NSUInteger bufferIndex = 0; bufferIndex < _mesh.vertexBuffers.count; bufferIndex++)
//        {
//            MTKMeshBuffer *vertexBuffer = _mesh.vertexBuffers[bufferIndex];
//            if((NSNull*)vertexBuffer != [NSNull null])
//            {
//                [renderEncoder setVertexBuffer:vertexBuffer.buffer
//                                        offset:vertexBuffer.offset
//                                       atIndex:bufferIndex];
//            }
//        }
//
//        [renderEncoder setFragmentTexture:_colorMap
//                                  atIndex:TextureIndexColor];
//
//        for(MTKSubmesh *submesh in _mesh.submeshes)
//        {
//            [renderEncoder drawIndexedPrimitives:submesh.primitiveType
//                                      indexCount:submesh.indexCount
//                                       indexType:submesh.indexType
//                                     indexBuffer:submesh.indexBuffer.buffer
//                               indexBufferOffset:submesh.indexBuffer.offset];
//        }
//
//        [renderEncoder popDebugGroup];
//
//        [renderEncoder endEncoding];
//
//        [commandBuffer presentDrawable:view.currentDrawable];
//    }
//
//    [commandBuffer commit];
}

- (void)mtkView:(nonnull MTKView *)view drawableSizeWillChange:(CGSize)size
{
    /// Respond to drawable size or orientation changes here

//    float aspect = size.width / (float)size.height;
//    _projectionMatrix = matrix_perspective_right_hand(65.0f * (M_PI / 180.0f), aspect, 0.1f, 100.0f);
}

#pragma mark Matrix Math Utilities

matrix_float4x4 matrix4x4_translation(float tx, float ty, float tz)
{
    return (matrix_float4x4) {{
        { 1,   0,  0,  0 },
        { 0,   1,  0,  0 },
        { 0,   0,  1,  0 },
        { tx, ty, tz,  1 }
    }};
}

static matrix_float4x4 matrix4x4_rotation(float radians, vector_float3 axis)
{
    axis = vector_normalize(axis);
    float ct = cosf(radians);
    float st = sinf(radians);
    float ci = 1 - ct;
    float x = axis.x, y = axis.y, z = axis.z;

    return (matrix_float4x4) {{
        { ct + x * x * ci,     y * x * ci + z * st, z * x * ci - y * st, 0},
        { x * y * ci - z * st,     ct + y * y * ci, z * y * ci + x * st, 0},
        { x * z * ci + y * st, y * z * ci - x * st,     ct + z * z * ci, 0},
        {                   0,                   0,                   0, 1}
    }};
}

matrix_float4x4 matrix_perspective_right_hand(float fovyRadians, float aspect, float nearZ, float farZ)
{
    float ys = 1 / tanf(fovyRadians * 0.5);
    float xs = ys / aspect;
    float zs = farZ / (nearZ - farZ);

    return (matrix_float4x4) {{
        { xs,   0,          0,  0 },
        {  0,  ys,          0,  0 },
        {  0,   0,         zs, -1 },
        {  0,   0, nearZ * zs,  0 }
    }};
}

@end
