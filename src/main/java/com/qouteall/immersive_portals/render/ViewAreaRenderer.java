package com.qouteall.immersive_portals.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.portal.Mirror;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.portal.SpecialPortalShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_CLIP_PLANE0;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

public class ViewAreaRenderer {
    private static void buildPortalViewAreaTrianglesBuffer(
        Vec3d fogColor, Portal portal, BufferBuilder bufferbuilder,
        Vec3d cameraPos, float partialTicks, float layerWidth
    ) {
        //if layerWidth is small, the teleportation will not be seamless
        
        //counter-clockwise triangles are front-faced in default
        
        bufferbuilder.begin(GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    
        Vec3d posInPlayerCoordinate = portal.getPositionVec().subtract(cameraPos);
    
        if (portal instanceof Mirror) {
            posInPlayerCoordinate = posInPlayerCoordinate.add(portal.getNormal().scale(-0.001));
        }
    
        Consumer<Vec3d> vertexOutput = p -> putIntoVertex(
            bufferbuilder, p, fogColor
        );
    
        if (portal.specialShape == null) {
            generateTriangleBiLayered(
                vertexOutput,
                portal,
                layerWidth,
                posInPlayerCoordinate
            );
        }
        else {
            generateTriangleSpecialBiLayered(
                vertexOutput,
                portal,
                layerWidth,
                posInPlayerCoordinate
            );
        }
    }
    
    private static void generateTriangleSpecialBiLayered(
        Consumer<Vec3d> vertexOutput,
        Portal portal,
        float layerWidth,
        Vec3d posInPlayerCoordinate
    ) {
        generateTriangleSpecialWithOffset(
            vertexOutput, portal, posInPlayerCoordinate,
            Vec3d.ZERO
        );
        
        generateTriangleSpecialWithOffset(
            vertexOutput, portal, posInPlayerCoordinate,
            portal.getNormal().scale(-layerWidth)
        );
    }
    
    private static void generateTriangleSpecialWithOffset(
        Consumer<Vec3d> vertexOutput,
        Portal portal,
        Vec3d posInPlayerCoordinate,
        Vec3d offset
    ) {
        SpecialPortalShape specialShape = portal.specialShape;
        
        for (SpecialPortalShape.TriangleInPlane triangle : specialShape.triangles) {
            //the face must be flipped
            putIntoLocalVertex(
                vertexOutput, portal, offset, posInPlayerCoordinate,
                triangle.x1, triangle.y1
            );
            putIntoLocalVertex(
                vertexOutput, portal, offset, posInPlayerCoordinate,
                triangle.x3, triangle.y3
            );
            putIntoLocalVertex(
                vertexOutput, portal, offset, posInPlayerCoordinate,
                triangle.x2, triangle.y2
            );
        }
    }
    
    private static void putIntoLocalVertex(
        Consumer<Vec3d> vertexOutput,
        Portal portal,
        Vec3d offset,
        Vec3d posInPlayerCoordinate,
        double localX, double localY
    ) {
        vertexOutput.accept(
            posInPlayerCoordinate
                .add(portal.axisW.scale(localX))
                .add(portal.axisH.scale(localY))
                .add(offset)
        );
    }
    
    private static void generateTriangleBiLayered(
        Consumer<Vec3d> vertexOutput,
        Portal portal,
        float layerWidth,
        Vec3d posInPlayerCoordinate
    ) {
        Vec3d layerOffsest = portal.getNormal().scale(-layerWidth);
        
        Vec3d[] frontFace = Arrays.stream(portal.getFourVerticesRelativeToCenter(0))
            .map(pos -> pos.add(posInPlayerCoordinate))
            .toArray(Vec3d[]::new);
        
        Vec3d[] backFace = Arrays.stream(portal.getFourVerticesRelativeToCenter(0))
            .map(pos -> pos.add(posInPlayerCoordinate).add(layerOffsest))
            .toArray(Vec3d[]::new);
        
        putIntoQuad(
            vertexOutput,
            backFace[0],
            backFace[2],
            backFace[3],
            backFace[1]
        );
        
        putIntoQuad(
            vertexOutput,
            frontFace[0],
            frontFace[2],
            frontFace[3],
            frontFace[1]
        );
    }
    
    static private void putIntoVertex(BufferBuilder bufferBuilder, Vec3d pos, Vec3d fogColor) {
        bufferBuilder
            .pos(pos.x, pos.y, pos.z)
            .color((float) fogColor.x, (float) fogColor.y, (float) fogColor.z, 1.0f)
            .endVertex();
    }
    
    //a d
    //b c
    private static void putIntoQuad(
        Consumer<Vec3d> vertexOutput,
        Vec3d a,
        Vec3d b,
        Vec3d c,
        Vec3d d
    ) {
        //counter-clockwise triangles are front-faced in default
    
        vertexOutput.accept(b);
        vertexOutput.accept(c);
        vertexOutput.accept(d);
    
        vertexOutput.accept(d);
        vertexOutput.accept(a);
        vertexOutput.accept(b);
        
    }
    
    public static void drawPortalViewTriangle(Portal portal) {
        Minecraft.getInstance().getProfiler().startSection("render_view_triangle");
        
        DimensionRenderHelper helper =
            CGlobal.clientWorldLoader.getDimensionRenderHelper(portal.dimensionTo);
        
        Vec3d fogColor = helper.getFogColor();
    
        //important
        GlStateManager.enableCull();
    
        //In OpenGL, if you forget to set one rendering state and the result will be abnormal
        //this design is bug-prone (DirectX is better in this aspect)
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color4f(1, 1, 1, 1);
        GlStateManager.disableFog();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableTexture();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        
        GL11.glDisable(GL_CLIP_PLANE0);

//        if (OFHelper.getIsUsingShader()) {
//            fogColor = Vec3d.ZERO;
//        }
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        buildPortalViewAreaTrianglesBuffer(
            fogColor,
            portal,
            bufferbuilder,
            PortalRenderer.mc.gameRenderer.getActiveRenderInfo().getProjectedView(),
            MyRenderHelper.partialTicks,
            portal instanceof Mirror ? 0 : 0.45F
        );
        
        tessellator.draw();
        
        GlStateManager.enableCull();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
    
        Minecraft.getInstance().getProfiler().endSection();
    }
    
}
