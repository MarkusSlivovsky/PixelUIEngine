package net.mslivo.core.engine.ui_engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.NumberUtils;

public class ImmediateRenderer {
    private static final String ERROR_END_BEGIN = "ImmediateRenderer.end must be called before begin.";
    private static final String ERROR_BEGIN_END = "ImmediateRenderer.begin must be called before end.";
    public static final String TWEAK_ATTRIBUTE = "a_tweak";
    public static final String COLOR_ATTRIBUTE = "a_color";
    public static final String VERTEX_COLOR_ATTRIBUTE = "a_vertexColor";

    private static final String VERTEX = """
                attribute vec4 a_position;
                attribute vec4 a_vertexColor;
                attribute vec4 a_color;
                attribute vec4 a_tweak;
                uniform mat4 u_projModelView;
                varying vec4 v_vertexColor;
                varying vec4 v_color;
                varying vec4 v_tweak;
                
                void main() {
                   v_vertexColor = a_vertexColor;
                   v_vertexColor.a *= 255.0 / 254.0;
                   
                   v_color = a_color;
                   v_color.a *= 255.0 / 254.0;
                   
                   v_tweak = a_tweak;
                   v_tweak.a = v_tweak.a * (255.0/254.0);
                   
                   gl_PointSize = 1.0;
                   gl_Position = u_projModelView * a_position;
                }
            """;
    private static final String FRAGMENT = """
                #ifdef GL_ES
                #define LOWP lowp
                 precision mediump float;
                #else
                 #define LOWP
                #endif
                varying LOWP vec4 v_vertexColor;
                varying LOWP vec4 v_color;
                varying LOWP vec4 v_tweak;
                const float eps = 1.0e-10;
                
                vec4 rgb2hsl(vec4 c)
                {
                    const vec4 J = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                    vec4 p = mix(vec4(c.bg, J.wz), vec4(c.gb, J.xy), step(c.b, c.g));
                    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                    float d = q.x - min(q.w, q.y);
                    float l = q.x * (1.0 - 0.5 * d / (q.x + eps));
                    return vec4(abs(q.z + (q.w - q.y) / (6.0 * d + eps)), (q.x - l) / (min(l, 1.0 - l) + eps), l, c.a);
                }
                                
                vec4 hsl2rgb(vec4 c)
                {
                    const vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                    vec3 p = abs(fract(c.x + K.xyz) * 6.0 - K.www);
                    float v = (c.z + c.y * min(c.z, 1.0 - c.z));
                    return vec4(v * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), 2.0 * (1.0 - c.z / (v + eps))), c.w);
                }
                
                void main() {
                   vec4 tgt = v_vertexColor;
                   tgt = rgb2hsl(tgt); // convert to HSL
                      
                   tgt.x = fract(tgt.x+v_tweak.x); // tweak Hue
                   tgt.y *= (v_tweak.y*2.0); // tweak Saturation
                   tgt.z += (v_tweak.z-0.5) * 2.0; // tweak Lightness
                   vec4 color = hsl2rgb(tgt); // convert back to RGB 
                   vec4 color_tinted = color*v_color; // multiply with batch tint color
                   color = mix(color, color_tinted, v_tweak.w); // mixed with tinted color based on tweak Tint
                   color.rgb = mix(vec3(dot(color.rgb, vec3(0.3333))), color.rgb,  (v_tweak.y*2.0));  // remove colors based on tweak.saturation
                      
                   gl_FragColor = color;
                }
            """;
    private int primitiveType;
    private final int MESH_RESIZE_STEP = 5000 * 4;
    private Matrix4 projection;
    private Color vertexColor;
    private Color color = Color.WHITE;
    private boolean blend;
    private ShaderProgram shader;
    private Mesh mesh;
    private float vertices[];
    private int vertexIdx, vertexSize;
    private static final float TWEAK_RESET = Color.toFloatBits(0f, 0.5f, 0.5f, 1f);
    private float tweak = TWEAK_RESET;


    private int u_projModelView;
    private boolean drawing;

    public ImmediateRenderer() {
        this.primitiveType = GL20.GL_POINTS;
        this.blend = false;
        this.vertexColor = new Color(Color.WHITE);
        this.shader = new ShaderProgram(VERTEX, FRAGMENT);
        this.u_projModelView = shader.getUniformLocation("u_projModelView");
        if (!shader.isCompiled()) throw new GdxRuntimeException("Error compiling shader: " + shader.getLog());
        this.vertices = new float[MESH_RESIZE_STEP];
        this.mesh = createMesh(MESH_RESIZE_STEP);
        this.vertexIdx = 0;
        this.vertexSize = mesh.getVertexAttributes().vertexSize / 4;
        this.projection = new Matrix4();
        this.drawing = false;
    }

    public void setProjectionMatrix(Matrix4 projection) {
        this.projection.set(projection);
    }

    public void begin() {
        begin(GL20.GL_POINTS);
    }

    public void begin(int primitiveType) {
        if (drawing) throw new IllegalStateException(ERROR_END_BEGIN);
        this.primitiveType = primitiveType;
        this.blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        if (!blend) Gdx.gl.glEnable(GL20.GL_BLEND);
        shader.bind();
        shader.setUniformMatrix(u_projModelView, this.projection);
        this.drawing = true;
    }

    public void end() {
        if (!drawing) throw new IllegalStateException(ERROR_BEGIN_END);
        this.drawing = false;
        if(vertexIdx == 0) return;
        mesh.setVertices(vertices, 0, vertexIdx);
        mesh.render(shader, this.primitiveType);
        vertexIdx = 0;
    }

    public void setVertexColor(Color vertexColor) {
        setColor(vertexColor.r, vertexColor.g, vertexColor.b, vertexColor.a);
    }

    public void setColor(float r, float g, float b) {
        setColor(r, g, b, 1f);
    }

    public void setColor(float r, float g, float b, float a) {
        this.vertexColor.set(r, g, b, a);
    }

    public Color getVertexColor() {
        return this.vertexColor;
    }

    public void dispose() {
        this.mesh.dispose();
        projection = null;
    }

    public void vertex(float x, float y, float z) {
        if (!drawing) throw new IllegalStateException("ImmediateRenderer.begin must be called before draw.");
        checkMeshSize(vertexSize);
        vertices[vertexIdx] = x;
        vertices[vertexIdx + 1] = y;
        vertices[vertexIdx + 2] = z;
        vertices[vertexIdx + 3] = NumberUtils.intToFloatColor(((int)(255 * this.vertexColor.a) << 24) | ((int)(255 * this.vertexColor.b) << 16) | ((int)(255 * this.vertexColor.g) << 8) | ((int)(255 * this.vertexColor.r)));
        vertices[vertexIdx + 4] = NumberUtils.intToFloatColor(((int)(255 * this.color.a) << 24) | ((int)(255 * this.color.b) << 16) | ((int)(255 * this.color.g) << 8) | ((int)(255 * this.color.r)));;
        vertices[vertexIdx + 5] = tweak;
        vertexIdx += vertexSize;
    }

    public void vertex(float x, float y) {
        vertex(x, y,0f);
    }


    public boolean isDrawing() {
        return drawing;
    }

    private void checkMeshSize(int size) {
        if ((vertexIdx + size) > mesh.getMaxVertices()) {
            int newSize = mesh.getMaxVertices() + MESH_RESIZE_STEP;
            float[] newVertices = new float[newSize];
            System.arraycopy(vertices, 0, newVertices, 0, vertices.length);
            this.vertices = newVertices;
            Mesh newMesh = createMesh(newSize);
            mesh.dispose();
            mesh = newMesh;
        }
    }

    private Mesh createMesh(int maxVertices) {
        return new Mesh(false, maxVertices, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, VERTEX_COLOR_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, COLOR_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, TWEAK_ATTRIBUTE)
        );
    }

    public int getPrimitiveType() {
        return primitiveType;
    }

}
