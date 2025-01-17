package net.mslivo.core.engine.ui_engine.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.NumberUtils;
import net.mslivo.core.engine.media_manager.*;

import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * A substitute for {@link com.badlogic.gdx.graphics.g2d.SpriteBatch} that adds better coloring.
 * This class is based on code from https://github.com/tommyettinger/colorful-gdx
 */
public class SpriteRenderer implements Batch {

    private static final String TWEAK_ATTRIBUTE = "a_tweak";

    private static final String VERTEX_SHADER = """
            attribute vec4 $POSITION_ATTRIBUTE;
            attribute vec4 $COLOR_ATTRIBUTE;
            attribute vec2 $TEXCOORD_ATTRIBUTE;
            attribute vec4 $TWEAK_ATTRIBUTE;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec4 v_tweak;
            varying vec2 v_texCoords;
            const vec3 forward = vec3(1.0 / 3.0);
            
            vec3 rgbToLabColor(vec3 start) {
               vec3 lab = mat3(+0.2104542553, +1.9779984951, +0.0259040371, +0.7936177850, -2.4285922050, +0.7827717662, -0.0040720468, +0.4505937099, -0.8086757660) *
                          pow(mat3(0.4121656120, 0.2118591070, 0.0883097947, 0.5362752080, 0.6807189584, 0.2818474174, 0.0514575653, 0.1074065790, 0.6302613616)
                          * (start.rgb * start.rgb), forward);
               lab.x = pow(lab.x, 1.48);
               lab.yz = lab.yz * 0.5 + 0.5;
               return lab;
            }
            
            void main()
            {
               // Tint Color
               v_color = $COLOR_ATTRIBUTE;
               v_color.w = v_color.w * (255.0/254.0);
               v_color.rgb = rgbToLabColor(v_color.rgb);
            
               // Tweak Color
               v_tweak = $TWEAK_ATTRIBUTE;
            
               // Position & TextCoord
               gl_Position =  u_projTrans * $POSITION_ATTRIBUTE;
               v_texCoords = $TEXCOORD_ATTRIBUTE;
            }
            """
            .replace("$POSITION_ATTRIBUTE", ShaderProgram.POSITION_ATTRIBUTE)
            .replace("$COLOR_ATTRIBUTE", ShaderProgram.COLOR_ATTRIBUTE)
            .replace("$TEXCOORD_ATTRIBUTE", ShaderProgram.TEXCOORD_ATTRIBUTE + "0")
            .replace("$TWEAK_ATTRIBUTE", TWEAK_ATTRIBUTE);
    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
                #define LOW lowp
                #define MED mediump
                #define HIGH highp
                precision mediump float;
            #else
                #define MED
                #define LOW
                #define HIGH
            #endif
            
            
            
            varying vec2 v_texCoords;
            varying vec4 v_color;
            varying vec4 v_tweak;
            
            uniform MED sampler2D u_texture;
            uniform MED vec2 u_textureSize;
            
            const vec3 forward = vec3(1.0 / 3.0);
            const float twoThird = 2.0 / 3.0;
            
            const mat3 rgb2xyz = mat3(
                0.4121656120, 0.2118591070, 0.0883097947,
                0.5362752080, 0.6807189584, 0.2818474174,
                0.0514575653, 0.1074065790, 0.6302613616
            );
            
            const mat3 xyz2lab = mat3(
                0.2104542553, 1.9779984951, 0.0259040371,
                0.7936177850, -2.4285922050, 0.7827717662,
                -0.0040720468, 0.4505937099, -0.8086757660
            );
            
            const mat3 lab2rgbMat1 = mat3(
                1.0, 1.0, 1.0,
                0.3963377774, -0.1055613458, -0.0894841775,
                0.2158037573, -0.0638541728, -1.2914855480
            );
            
            const mat3 lab2rgbMat2 = mat3(
                4.0767245293, -1.2681437731, -0.0041119885,
                -3.3072168827, 2.6093323231, -0.7034763098,
                0.2307590544, -0.3411344290, 1.7068625689
            );
            
            vec3 rgbToLabFragment(vec3 start) {
                vec3 xyz = rgb2xyz * (start.rgb * start.rgb);
                vec3 lab = xyz2lab * pow(xyz, forward);
                lab.x = (pow(lab.x, 1.51) - 0.5) * 2.0;
                return lab;
            }
            
            void main() {
                // Pixelation
                MED vec2 texCoords = v_texCoords;
            
                // Calculate pixelation factor
                MED float pixelSize = 2.0 + floor(v_tweak.w * 14.0);
            
                texCoords = texCoords * u_textureSize;
                texCoords = mix(texCoords, floor((texCoords / pixelSize) + 0.5) * pixelSize, step(0.001, v_tweak.w));
                texCoords = texCoords / u_textureSize;
            
                // OkLab Tweaks
                MED vec4 tgt = texture2D(u_texture, texCoords);
                MED vec3 lab = rgbToLabFragment(tgt.xyz);
                lab.x = pow(clamp(lab.x * v_tweak.x + v_color.x, 0.0, 1.0), twoThird);
                lab.yz = clamp((lab.yz * v_tweak.yz + v_color.yz - 0.5) * 2.0, -1.0, 1.0);
                lab = lab2rgbMat1 * lab;
                // back to RGB
                MED vec3 rgb = sqrt(clamp(lab2rgbMat2 * (lab * lab * lab), 0.0, 1.0));
            
                gl_FragColor = vec4(rgb, v_color.a * tgt.a);
            }
            """;

    public static final int SIZE_MAX = 16383;

    private static final String ERROR_END_BEGIN = "SpriteRenderer.end must be called before begin.";
    private static final String ERROR_BEGIN_END = "SpriteRenderer.begin must be called before end.";
    private static final int VERTEX_SIZE = 6;
    private static final int INDICES_SIZE = 6;
    private static final int SPRITE_SIZE = 24;
    private static final int RGB_SRC = 0, RGB_DST = 1, ALPHA_SRC = 2, ALPHA_DST = 3;
    private static final String FLUSH_WARNING = "%d intermediate flushes detected | vertices.length=%d | %s";

    private int size;
    private final Color tempColor;
    private VertexData vertexData;
    private IndexData indexData;
    private float[] vertices;
    private int idx;
    private Texture lastTexture;
    private float invTexWidth, invTexHeight;
    private boolean drawing;

    private final Matrix4 projectionMatrix;
    private final Matrix4 transformMatrix;
    private final Matrix4 combinedMatrix;
    private ShaderProgram shader;
    private boolean defaultShader;
    private MediaManager mediaManager;
    private int u_projTrans;
    private int u_texture;
    private int u_textureSize;
    private Vector2 textureSizeD4Vector;
    private int renderCalls;
    private int totalRenderCalls;
    private int maxSpritesInBatch;
    private boolean flushWarning;
    private int intermediateFlushes;
    private float color;
    private float tweak;
    private final int[] blend;

    private float backup_tweak;
    private float backup_color;
    private int[] backup_blend;

    private float reset_tweak;
    private float reset_color;
    private final int[] reset_blend;

    public SpriteRenderer() {
        this(null, null, SIZE_MAX, false);
    }

    public SpriteRenderer(MediaManager mediaManager) {
        this(mediaManager, null, SIZE_MAX, false);
    }

    public SpriteRenderer(MediaManager mediaManager, ShaderProgram shader) {
        this(mediaManager, shader, SIZE_MAX, false);
    }

    public SpriteRenderer(MediaManager mediaManager, ShaderProgram shader, int size) {
        this(mediaManager, shader, size, false);
    }

    public SpriteRenderer(MediaManager mediaManager, ShaderProgram shader, int size, boolean flushWarning) {
        if (size > SIZE_MAX)
            throw new IllegalArgumentException("Can't have more than " + SIZE_MAX + " sprites per batch: " + size);
        if (shader == null) {
            this.shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (!this.shader.isCompiled())
                throw new IllegalArgumentException("Error compiling shader: " + this.shader.getLog());
            defaultShader = true;
        } else {
            this.shader = shader;
        }

        this.size = size;
        this.flushWarning = flushWarning;
        this.u_projTrans = this.shader.getUniformLocation("u_projTrans");
        this.u_texture = this.shader.getUniformLocation("u_texture");
        this.u_textureSize = this.shader.getUniformLocation("u_textureSize");
        this.textureSizeD4Vector = new Vector2(0, 0);
        this.drawing = false;
        this.idx = 0;
        this.intermediateFlushes = 0;
        this.lastTexture = null;
        this.transformMatrix = new Matrix4();
        this.combinedMatrix = new Matrix4();
        this.tempColor = new Color(Color.GRAY);
        this.renderCalls = this.totalRenderCalls = this.maxSpritesInBatch = 0;
        this.invTexWidth = this.invTexHeight = 0;
        this.vertexData = createVertexData(size);
        this.indexData = createIndexData(size);
        this.vertices = createVerticesArray(size);
        this.projectionMatrix = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        this.reset_tweak = colorPackedRGBA(0.5f, 0.5f, 0.5f, 0.0f);
        this.reset_color = colorPackedRGBA(0.5f, 0.5f, 0.5f, 1f);
        this.reset_blend = new int[]{GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA};

        this.color = reset_color;
        this.tweak = reset_tweak;
        this.blend = new int[]{this.reset_blend[RGB_SRC], this.reset_blend[RGB_DST], this.reset_blend[ALPHA_SRC], this.reset_blend[ALPHA_DST]};

        this.backup_color = this.color;
        this.backup_tweak = this.tweak;
        this.backup_blend = new int[]{this.blend[RGB_SRC], this.blend[RGB_DST], this.blend[ALPHA_SRC], this.blend[ALPHA_DST]};
        this.mediaManager = mediaManager;
    }

    private IndexBufferObject createIndexData(int size) {
        int len = size * INDICES_SIZE;
        int j = 0;
        short[] indices = new short[len];

        for (int i = 0; i < len; i += 6, j += 4) {
            indices[i] = intToUnsignedShort(j);
            indices[i + 1] = intToUnsignedShort(j + 1);
            indices[i + 2] = intToUnsignedShort(j + 2);
            indices[i + 3] = intToUnsignedShort(j + 2);
            indices[i + 4] = intToUnsignedShort(j + 3);
            indices[i + 5] = intToUnsignedShort(j);
        }

        IndexBufferObject indexBufferObject = new IndexBufferObject(true, size * INDICES_SIZE);
        indexBufferObject.setIndices(indices, 0, indices.length);

        return indexBufferObject;
    }

    public static short intToUnsignedShort(int value) {
        return (short) (value & 0xFFFF);
    }

    private float[] createVerticesArray(int size) {
        float[] newVertices = new float[size * SPRITE_SIZE];
        return newVertices;
    }

    private VertexData createVertexData(int size) {
        return new VertexBufferObjectWithVAO(true, size * VERTEX_SIZE,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, TWEAK_ATTRIBUTE));
    }

    @Override
    public void begin() {
        if (drawing) throw new IllegalStateException(ERROR_END_BEGIN);
        renderCalls = 0;
        Gdx.gl.glDepthMask(false);

        shader.bind();
        setupMatrices();

        Gdx.gl.glEnable(GL32.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(this.blend[RGB_SRC], this.blend[RGB_DST], this.blend[ALPHA_SRC], this.blend[ALPHA_DST]);

        drawing = true;
    }

    @Override
    public void end() {
        if (!drawing) throw new IllegalStateException(ERROR_BEGIN_END);
        if (idx > 0) flush();
        lastTexture = null;
        Gdx.gl.glDepthMask(true);
        if (flushWarning && this.intermediateFlushes > 0) {
            printFlushWarning();
        }
        this.intermediateFlushes = 0;
        drawing = false;
    }

    private void printFlushWarning() {
        System.err.println(String.format(FLUSH_WARNING, (this.intermediateFlushes + 1), this.vertices.length, Thread.currentThread().getStackTrace()[2].toString()));
    }

    @Override
    public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
                     float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        if (texture != lastTexture)
            switchTexture(texture);
        else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x1;
        vertices[idx + 1] = y1;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x2;
        vertices[idx + 7] = y2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = x3;
        vertices[idx + 13] = y3;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = x4;
        vertices[idx + 19] = y4;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth,
                     int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        if (texture != lastTexture)
            switchTexture(texture);
        else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;
        final float fx2 = x + width;
        final float fy2 = y + height;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x;
        vertices[idx + 1] = y;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x;
        vertices[idx + 7] = fy2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = fx2;
        vertices[idx + 13] = fy2;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = fx2;
        vertices[idx + 19] = y;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (!drawing) throw new IllegalStateException("SpritRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        if (texture != lastTexture)
            switchTexture(texture);
        else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        final float u = srcX * invTexWidth;
        final float v = (srcY + srcHeight) * invTexHeight;
        final float u2 = (srcX + srcWidth) * invTexWidth;
        final float v2 = srcY * invTexHeight;
        final float fx2 = x + srcWidth;
        final float fy2 = y + srcHeight;

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x;
        vertices[idx + 1] = y;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x;
        vertices[idx + 7] = fy2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = fx2;
        vertices[idx + 13] = fy2;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = fx2;
        vertices[idx + 19] = y;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        if (texture != lastTexture)
            switchTexture(texture);
        else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        final float fx2 = x + width;
        final float fy2 = y + height;

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x;
        vertices[idx + 1] = y;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x;
        vertices[idx + 7] = fy2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = fx2;
        vertices[idx + 13] = fy2;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = fx2;
        vertices[idx + 19] = y;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(Texture texture, float x, float y) {
        draw(texture, x, y, texture.getWidth(), texture.getHeight());
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        if (texture != lastTexture)
            switchTexture(texture);
        else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        final float fx2 = x + width;
        final float fy2 = y + height;
        final float u = 0;
        final float v = 1;
        final float u2 = 1;
        final float v2 = 0;

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x;
        vertices[idx + 1] = y;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x;
        vertices[idx + 7] = fy2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = fx2;
        vertices[idx + 13] = fy2;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = fx2;
        vertices[idx + 19] = y;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        count = (count / 5) * 6;
        int verticesLength = vertices.length;
        int remainingVertices = verticesLength;
        if (texture != lastTexture)
            switchTexture(texture);
        else {
            remainingVertices -= idx;
            if (remainingVertices == 0) {
                flush();
                remainingVertices = verticesLength;
            }
        }
        int copyCount = Math.min(remainingVertices, count);
        final float tweak = this.tweak;

        ////old way, breaks when libGDX code expects SPRITE_SIZE to be 20
        //System.arraycopy(spriteVertices, offset, vertices, idx, copyCount);
        ////new way, thanks mgsx
        for (int s = offset, v = idx, i = 0; i < copyCount; i += 6) {
            vertices[v++] = spriteVertices[s++];
            vertices[v++] = spriteVertices[s++];
            vertices[v++] = spriteVertices[s++];
            vertices[v++] = spriteVertices[s++];
            vertices[v++] = spriteVertices[s++];
            vertices[v++] = tweak;
        }
        idx += copyCount;
        count -= copyCount;
        while (count > 0) {
            offset += (copyCount / 6) * 5;
            flush();
            copyCount = Math.min(verticesLength, count);
            ////old way, breaks when libGDX code expects SPRITE_SIZE to be 20
            //System.arraycopy(spriteVertices, offset, vertices, 0, copyCount);
            ////new way, thanks mgsx
            for (int s = offset, v = 0, i = 0; i < copyCount; i += 6) {
                vertices[v++] = spriteVertices[s++];
                vertices[v++] = spriteVertices[s++];
                vertices[v++] = spriteVertices[s++];
                vertices[v++] = spriteVertices[s++];
                vertices[v++] = spriteVertices[s++];
                vertices[v++] = tweak;
            }
            idx += copyCount;
            count -= copyCount;
        }
    }


    public void drawExactly(Texture texture, float[] spriteVertices, int offset, int count) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        int verticesLength = vertices.length;
        int remainingVertices = verticesLength;
        if (texture != lastTexture)
            switchTexture(texture);
        else {
            remainingVertices -= idx;
            if (remainingVertices == 0) {
                flush();
                remainingVertices = verticesLength;
            }
        }
        int copyCount = Math.min(remainingVertices, count);

        System.arraycopy(spriteVertices, offset, vertices, idx, copyCount);
        idx += copyCount;
        count -= copyCount;
        while (count > 0) {
            offset += copyCount;
            flush();
            copyCount = Math.min(verticesLength, count);
            System.arraycopy(spriteVertices, offset, vertices, 0, copyCount);
            idx += copyCount;
            count -= copyCount;
        }
    }

    @Override
    public void draw(TextureRegion region, float x, float y) {
        draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        Texture texture = region.getTexture();
        if (texture != lastTexture) {
            switchTexture(texture);
        } else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        final float fx2 = x + width;
        final float fy2 = y + height;
        final float u = region.getU();
        final float v = region.getV2();
        final float u2 = region.getU2();
        final float v2 = region.getV();

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x;
        vertices[idx + 1] = y;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x;
        vertices[idx + 7] = fy2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = fx2;
        vertices[idx + 13] = fy2;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = fx2;
        vertices[idx + 19] = y;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                     float scaleX, float scaleY, float rotation) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        Texture texture = region.getTexture();
        if (texture != lastTexture) {
            switchTexture(texture);
        } else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        final float u = region.getU();
        final float v = region.getV2();
        final float u2 = region.getU2();
        final float v2 = region.getV();

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x1;
        vertices[idx + 1] = y1;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x2;
        vertices[idx + 7] = y2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = x3;
        vertices[idx + 13] = y3;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = x4;
        vertices[idx + 19] = y4;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                     float scaleX, float scaleY, float rotation, boolean clockwise) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        Texture texture = region.getTexture();
        if (texture != lastTexture) {
            switchTexture(texture);
        } else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u1, v1, u2, v2, u3, v3, u4, v4;
        if (clockwise) {
            u1 = region.getU2();
            v1 = region.getV2();
            u2 = region.getU();
            v2 = region.getV2();
            u3 = region.getU();
            v3 = region.getV();
            u4 = region.getU2();
            v4 = region.getV();
        } else {
            u1 = region.getU();
            v1 = region.getV();
            u2 = region.getU2();
            v2 = region.getV();
            u3 = region.getU2();
            v3 = region.getV2();
            u4 = region.getU();
            v4 = region.getV2();
        }

        final float color = this.color;
        final float tweak = this.tweak;


        vertices[idx] = x1;
        vertices[idx + 1] = y1;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u1;
        vertices[idx + 4] = v1;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x2;
        vertices[idx + 7] = y2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u2;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = x3;
        vertices[idx + 13] = y3;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u3;
        vertices[idx + 16] = v3;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = x4;
        vertices[idx + 19] = y4;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u4;
        vertices[idx + 22] = v4;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @Override
    public void draw(TextureRegion region, float width, float height, Affine2 transform) {
        if (!drawing) throw new IllegalStateException("SpriteRenderer.begin must be called before draw.");

        float[] vertices = this.vertices;

        Texture texture = region.getTexture();
        if (texture != lastTexture) {
            switchTexture(texture);
        } else if (idx == vertices.length) {
            this.intermediateFlushes++;
            flush();
        }

        // construct corner points
        float x1 = transform.m02;
        float y1 = transform.m12;
        float x2 = transform.m01 * height + transform.m02;
        float y2 = transform.m11 * height + transform.m12;
        float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
        float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
        float x4 = transform.m00 * width + transform.m02;
        float y4 = transform.m10 * width + transform.m12;

        float u = region.getU();
        float v = region.getV2();
        float u2 = region.getV2();
        float v2 = region.getV();

        final float color = this.color;
        final float tweak = this.tweak;

        vertices[idx] = x1;
        vertices[idx + 1] = y1;
        vertices[idx + 2] = color;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        vertices[idx + 5] = tweak;

        vertices[idx + 6] = x2;
        vertices[idx + 7] = y2;
        vertices[idx + 8] = color;
        vertices[idx + 9] = u;
        vertices[idx + 10] = v2;
        vertices[idx + 11] = tweak;

        vertices[idx + 12] = x3;
        vertices[idx + 13] = y3;
        vertices[idx + 14] = color;
        vertices[idx + 15] = u2;
        vertices[idx + 16] = v2;
        vertices[idx + 17] = tweak;

        vertices[idx + 18] = x4;
        vertices[idx + 19] = y4;
        vertices[idx + 20] = color;
        vertices[idx + 21] = u2;
        vertices[idx + 22] = v;
        vertices[idx + 23] = tweak;

        idx += SPRITE_SIZE;

    }

    @SuppressWarnings("RedundantCast") // These casts are absolutely not redundant! Java 9 changed Buffer ABI.
    @Override
    public void flush() {
        if (idx == 0) return;

        renderCalls++;
        totalRenderCalls++;

        int spritesInBatch = idx / SPRITE_SIZE;
        if (spritesInBatch > maxSpritesInBatch) maxSpritesInBatch = spritesInBatch;
        int count = spritesInBatch * 6;

        lastTexture.bind();

        vertexData.setVertices(this.vertices, 0, this.idx);
        vertexData.bind(shader);

        ShortBuffer indexBuffer = indexData.getBuffer(true);
        indexBuffer.position(0);
        indexBuffer.limit(count);
        indexData.bind();

        Gdx.gl32.glDrawElements(GL32.GL_TRIANGLES, count, GL32.GL_UNSIGNED_SHORT, 0);

        idx = 0;
    }


    @Override
    public void disableBlending() {
        throw new RuntimeException("not supported");
    }

    @Override
    public void enableBlending() {
        throw new RuntimeException("not supported");
    }

    @Override
    public void setBlendFunction(int srcFunc, int dstFunc) {
        setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    @Override
    public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {
        if (this.blend[RGB_SRC] == srcFuncColor && this.blend[RGB_DST] == dstFuncColor && this.blend[ALPHA_SRC] == srcFuncAlpha && this.blend[ALPHA_DST] == dstFuncAlpha)
            return;

        this.blend[RGB_SRC] = srcFuncColor;
        this.blend[RGB_DST] = dstFuncColor;
        this.blend[ALPHA_SRC] = srcFuncAlpha;
        this.blend[ALPHA_DST] = dstFuncAlpha;
        if (drawing) {
            flush();
            Gdx.gl.glBlendFuncSeparate(blend[RGB_SRC], blend[RGB_DST], blend[ALPHA_SRC], blend[ALPHA_DST]);
        }
    }

    @Override
    public int getBlendSrcFunc() {
        return blend[RGB_SRC];
    }

    @Override
    public int getBlendDstFunc() {
        return blend[RGB_DST];
    }

    @Override
    public int getBlendSrcFuncAlpha() {
        return blend[ALPHA_SRC];
    }

    @Override
    public int getBlendDstFuncAlpha() {
        return blend[ALPHA_DST];
    }

    @Override
    public void dispose() {
        vertexData.dispose();
        indexData.dispose();
        if (defaultShader && shader != null) shader.dispose();
    }

    @Override
    public Matrix4 getProjectionMatrix() {
        return projectionMatrix;
    }

    @Override
    public Matrix4 getTransformMatrix() {
        return transformMatrix;
    }

    @Override
    public void setProjectionMatrix(Matrix4 projection) {
        if (Arrays.equals(projectionMatrix.val, projection.val)) return;
        if (drawing) flush();
        this.projectionMatrix.set(projection);
        if (drawing) setupMatrices();
    }

    @Override
    public void setTransformMatrix(Matrix4 transform) {
        if (drawing) flush();
        transformMatrix.set(transform);
        if (drawing) setupMatrices();
    }

    protected void setupMatrices() {
        combinedMatrix.set(projectionMatrix).mul(transformMatrix);
        shader.setUniformMatrix(u_projTrans, combinedMatrix);
        shader.setUniformi(u_texture, 0);
    }

    protected void switchTexture(Texture texture) {
        flush();
        lastTexture = texture;
        invTexWidth = 1.0f / texture.getWidth();
        invTexHeight = 1.0f / texture.getHeight();

        this.textureSizeD4Vector.set(texture.getWidth(), texture.getHeight());
        shader.setUniformf(this.u_textureSize, this.textureSizeD4Vector);
    }

    @Override
    public void setShader(ShaderProgram shader) {
        if (drawing) {
            flush();
        }
        this.shader = shader;
        this.u_projTrans = shader.getUniformLocation("u_projTrans");
        this.u_texture = shader.getUniformLocation("u_texture");
        this.shader.bind();
    }

    @Override
    public ShaderProgram getShader() {
        return this.shader;
    }

    @Override
    public boolean isBlendingEnabled() {
        throw new RuntimeException("not supported");
    }

    public boolean isDrawing() {
        return drawing;
    }

    // ####### MediaManager Draw Methods #######

    // ----- CMediaSprite -----

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region, x, y);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float width, float height) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region, x, y, width, height);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region.getTexture(), x, y, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region.getTexture(), x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region.getTexture(), x, y, width, height, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region.getTexture(), x, y, width, height, u, v, u2, v2);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float[] spriteVertices, int offset, int count) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region.getTexture(), spriteVertices, offset, count);
    }

    public void drawCMediaSprite(CMediaSprite cMediaSprite, int index, float animationTimer, float width, float height, Affine2 transform) {
        TextureRegion region = mediaManager.sprite(cMediaSprite, index, animationTimer);
        this.draw(region, width, height, transform);
    }

    // ----- CMediaimage -----

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region, x, y);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float width, float height) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region, x, y, width, height);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region.getTexture(), x, y, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region.getTexture(), x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region.getTexture(), x, y, width, height, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region.getTexture(), x, y, width, height, u, v, u2, v2);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float[] spriteVertices, int offset, int count) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region.getTexture(), spriteVertices, offset, count);
    }

    public void drawCMediaImage(CMediaImage cMediaImage, float width, float height, Affine2 transform) {
        TextureRegion region = mediaManager.image(cMediaImage);
        this.draw(region, width, height, transform);
    }

    // ----- CMediaArray -----

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region, x, y);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float width, float height) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region, x, y, width, height);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region.getTexture(), x, y, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region.getTexture(), x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region.getTexture(), x, y, width, height, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region.getTexture(), x, y, width, height, u, v, u2, v2);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float[] spriteVertices, int offset, int count) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region.getTexture(), spriteVertices, offset, count);
    }

    public void drawCMediaArray(CMediaArray cMediaArray, int index, float width, float height, Affine2 transform) {
        TextureRegion region = mediaManager.array(cMediaArray, index);
        this.draw(region, width, height, transform);
    }

    // ----- CMediaAnimation -----

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region, x, y);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float width, float height) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region, x, y, width, height);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region.getTexture(), x, y, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region.getTexture(), x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region.getTexture(), x, y, width, height, region.getRegionX() + srcX, region.getRegionY() + srcY, srcWidth, srcHeight, flipX, flipY);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region.getTexture(), x, y, width, height, u, v, u2, v2);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float[] spriteVertices, int offset, int count) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region.getTexture(), spriteVertices, offset, count);
    }

    public void drawCMediaAnimation(CMediaAnimation cMediaAnimation, float animationTimer, float width, float height, Affine2 transform) {
        TextureRegion region = mediaManager.animation(cMediaAnimation).getKeyFrame(animationTimer);
        this.draw(region, width, height, transform);
    }

    // ----- CMediaFont -----

    public void drawCMediaFont(CMediaFont cMediaFont, float x, float y, String text) {
        this.drawCMediaFont(cMediaFont,x,y,text,false, false,0,this.getAlpha());
    }

    public void drawCMediaFont(CMediaFont cMediaFont, float x, float y, String text, boolean centerX, boolean centerY) {
        this.drawCMediaFont(cMediaFont,x,y,text,centerX, centerY,0,this.getAlpha());
    }

    public void drawCMediaFont(CMediaFont cMediaFont, float x, float y, String text, boolean centerX, boolean centerY, int maxWidth) {
        this.drawCMediaFont(cMediaFont,x,y,text,centerX, centerY,maxWidth,this.getAlpha());
    }

    public void drawCMediaFont(CMediaFont cMediaFont, float x, float y, String text, boolean centerX, boolean centerY, int maxWidth, float alpha) {
        if (cMediaFont == null) return;
        int xOffset = cMediaFont.offset_x;
        int yOffset = cMediaFont.offset_y;
        if (centerX)
            xOffset -= MathUtils.round(mediaManager.fontTextWidth(cMediaFont, text) / 2f);
        if (centerY)
            yOffset -= MathUtils.round(mediaManager.fontTextHeight(cMediaFont, text) / 2f);
        BitmapFontCache fontCache = mediaManager.font(cMediaFont).getCache();
        fontCache.clear();
        fontCache.addText(text, (x + xOffset), (y + yOffset), 0, text.length(), maxWidth, Align.left, false, maxWidth > 0 ? "" : null);
        fontCache.setAlphas(alpha);
        fontCache.draw(this);
    }



    // ####### Getter / Setters #######

    // ----- Tint Color -----

    @Override
    public void setColor(Color color) {
        this.color = colorPackedRGBA(color.r, color.g, color.b, color.a);
    }

    public void setColor(Color color, float alpha) {
        this.color = colorPackedRGBA(color.r, color.g, color.b, alpha);
    }

    @Override
    public void setColor(float r, float g, float b, float alpha) {
        this.color = colorPackedRGBA(r, g, b, alpha);
    }

    @Override
    public void setPackedColor(final float color) {
        this.color = color;
    }

    @Override
    public Color getColor() {
        Color.abgr8888ToColor(tempColor, color);
        return tempColor;
    }

    @Override
    public float getPackedColor() {
        return this.color;
    }

    public float getR() {
        int c = NumberUtils.floatToIntColor(this.color);
        return ((c & 0x000000ff)) / 255f;
    }

    public float getG() {
        int c = NumberUtils.floatToIntColor(this.color);
        return ((c & 0x0000ff00) >>> 8) / 255f;
    }

    public float getB() {
        int c = NumberUtils.floatToIntColor(this.color);
        return ((c & 0x00ff0000) >>> 16) / 255f;
    }

    public float getAlpha() {
        int c = NumberUtils.floatToIntColor(this.color);
        return ((c & 0xff000000) >>> 24) / 255f;
    }

    // ----- Tweak -----

    public void setTweak(float L, float A, float B, float pixelation) {
        tweak = colorPackedRGBA(L, A, B, pixelation);
    }

    public void setPackedTweak(final float tweak) {
        this.tweak = tweak;
    }

    public void setTweakL(float L) {
        int c = NumberUtils.floatToIntColor(tweak);
        float Contrast = ((c & 0xff000000) >>> 24) / 255f;
        float B = ((c & 0x00ff0000) >>> 16) / 255f;
        float A = ((c & 0x0000ff00) >>> 8) / 255f;
        tweak = colorPackedRGBA(L, A, B, Contrast);
    }

    public void setTweakA(float A) {
        int c = NumberUtils.floatToIntColor(tweak);
        float Contrast = ((c & 0xff000000) >>> 24) / 255f;
        float B = ((c & 0x00ff0000) >>> 16) / 255f;
        float L = ((c & 0x000000ff)) / 255f;
        tweak = colorPackedRGBA(L, A, B, Contrast);
    }

    public void setTweakB(float B) {
        int c = NumberUtils.floatToIntColor(tweak);
        float Contrast = ((c & 0xff000000) >>> 24) / 255f;
        float A = ((c & 0x0000ff00) >>> 8) / 255f;
        float L = ((c & 0x000000ff)) / 255f;
        tweak = colorPackedRGBA(L, A, B, Contrast);
    }

    public void setTweakPixelation(float pixelation) {
        int c = NumberUtils.floatToIntColor(tweak);
        float b = ((c & 0x00ff0000) >>> 16) / 255f;
        float g = ((c & 0x0000ff00) >>> 8) / 255f;
        float r = ((c & 0x000000ff)) / 255f;
        tweak = colorPackedRGBA(r, g, b, pixelation);
    }

    public float getTweakL() {
        int c = NumberUtils.floatToIntColor(this.tweak);
        return ((c & 0x000000ff)) / 255f;
    }

    public float getTweakA() {
        int c = NumberUtils.floatToIntColor(this.tweak);
        return ((c & 0x0000ff00) >>> 8) / 255f;
    }

    public float getTweakB() {
        int c = NumberUtils.floatToIntColor(this.tweak);
        return ((c & 0x00ff0000) >>> 16) / 255f;
    }

    public float getTweakPixelation() {
        int c = NumberUtils.floatToIntColor(this.tweak);
        return ((c & 0xff000000) >>> 24) / 255f;
    }

    public float getPackedTweak() {
        return this.tweak;
    }

    public Color getTweak() {
        Color.abgr8888ToColor(tempColor, tweak);
        return tempColor;
    }

    // ---- RESET & STATE ----

    public void setTweakReset() {
        setPackedTweak(reset_tweak);
    }

    public void setColorReset() {
        setPackedColor(reset_color);
    }

    public void setBlendFunctionReset() {
        setBlendFunctionSeparate(reset_blend[RGB_SRC], reset_blend[RGB_DST], reset_blend[ALPHA_SRC], reset_blend[ALPHA_DST]);
    }

    public void setTweakAndColorReset() {
        setTweakReset();
        setColorReset();
    }

    public void setAllReset() {
        setTweakReset();
        setColorReset();
        setBlendFunctionReset();
    }

    public void saveState() {
        this.backup_color = this.color;
        this.backup_tweak = this.tweak;
        System.arraycopy(this.blend, 0, this.backup_blend, 0, 4);
    }

    public void loadState() {
        setPackedColor(this.backup_color);
        setPackedTweak(this.backup_tweak);
        setBlendFunctionSeparate(this.backup_blend[RGB_SRC], this.backup_blend[RGB_DST], this.backup_blend[ALPHA_SRC], this.backup_blend[ALPHA_DST]);
    }


    public void setColorResetValues(float r, float g, float b, float a) {
        this.reset_color = colorPackedRGBA(r, g, b, a);
        this.setColorReset();
    }

    public void setTweakResetValues(float l, float a, float b, float pixelation) {
        this.reset_tweak = colorPackedRGBA(l, a, b, pixelation);
        this.setTweakReset();
    }

    public void setBlendFunctionSeparateResetValues(int blend_rgb_src, int blend_rgb_dst, int blend_alpha_src, int blend_alpha_blend) {
        this.reset_blend[RGB_SRC] = blend_rgb_src;
        this.reset_blend[RGB_DST] = blend_rgb_dst;
        this.reset_blend[ALPHA_SRC] = blend_alpha_src;
        this.reset_blend[ALPHA_DST] = blend_alpha_blend;
        this.setBlendFunctionReset();
    }

    public void setBlendFunctionResetValues(int blend_src, int blend_dst) {
        this.reset_blend[RGB_SRC] = blend_src;
        this.reset_blend[RGB_DST] = blend_dst;
        this.reset_blend[ALPHA_SRC] = blend_src;
        this.reset_blend[ALPHA_DST] = blend_dst;
        this.setBlendFunctionReset();
    }

    private static float colorPackedRGBA(float red, float green, float blue, float alpha) {
        return NumberUtils.intBitsToFloat(((int) (alpha * 255) << 24 & 0xFE000000) | ((int) (blue * 255) << 16 & 0xFF0000)
                | ((int) (green * 255) << 8 & 0xFF00) | ((int) (red * 255) & 0xFF));
    }

    public int getRenderCalls() {
        return this.renderCalls;
    }

    public int getTotalRenderCalls() {
        return this.totalRenderCalls;
    }
}