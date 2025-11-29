package org.example;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// CAMBIO 1: Nombre de la clase actualizado
public class SCP173Visualizer extends GLJPanel implements GLEventListener, KeyListener {

    static {
        System.setProperty("sun.java2d.uiScale", "1");
        // A veces es necesario probar con "true" o "false" en D3D si hay parpadeos
        System.setProperty("sun.java2d.d3d", "false");
    }

    private static final int FPS = 60;

    // RUTAS (Asegúrate que siguen siendo correctas)
    private final String OBJ_PATH = "./data/173.obj";
    //private final String TEX_BASE_PATH = "./data/173texture.jpg";
    //private final String TEX_SPEC_PATH = "./data/173_spec.jpg";
    //private final String TEX_NORM_PATH = "./data/173_norm.jpg";

    // Datos del Modelo
    private List<float[]> vertices = new ArrayList<>();
    private List<float[]> textureCoords = new ArrayList<>();
    private List<float[]> normals = new ArrayList<>();
    private List<int[][]> faces = new ArrayList<>();

    // Texturas
    private Texture texBase;
    private Texture texSpec;
    // private Texture texNorm; // No la usamos en este render simple sin shaders

    // Cámara
    private float rotateY = 0f;
    private float rotateX = 0f;
    private float zoom = -20.0f;

    private static FPSAnimator animator;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile profile = GLProfile.getDefault();
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setHardwareAccelerated(true);
            caps.setDoubleBuffered(true);

            // CAMBIO 2: Nombre de la ventana actualizado
            JFrame frame = new JFrame("SCP173Visualizer");

            // CAMBIO 3: Instanciamos la clase con el nuevo nombre
            SCP173Visualizer panel = new SCP173Visualizer(caps);

            frame.getContentPane().add(panel);
            frame.setSize(1024, 768);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (animator != null && animator.isAnimating()) animator.stop();
                    System.exit(0);
                }
            });

            frame.setVisible(true);
            animator = new FPSAnimator(panel, FPS, true);
            animator.start();
        });
    }

    // Constructor actualizado con el nuevo nombre
    public SCP173Visualizer(GLCapabilities caps) {
        super(caps);
        this.addGLEventListener(this);
        this.addKeyListener(this);
        this.setFocusable(true);
        this.requestFocusInWindow();
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClearColor(0.1f, 0.1f, 0.12f, 1.0f);
        gl.glClearDepth(1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glShadeModel(GL2.GL_SMOOTH);

        // Iluminación
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        float[] lightPos = {5.0f, 10.0f, 20.0f, 1.0f};
        float[] lightColor = {1.0f, 0.98f, 0.95f, 1.0f};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPos, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, lightColor, 0);

        // Cargar texturas
        // texBase = loadTexture(gl, TEX_BASE_PATH);
        // texSpec = loadTexture(gl, TEX_SPEC_PATH);
        // texNorm = loadTexture(gl, TEX_NORM_PATH); // Opcional si no usas shaders

        loadObjFile(OBJ_PATH);
    }

    private Texture loadTexture(GL2 gl, String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                Texture t = TextureIO.newTexture(file, true);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
                return t;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadObjFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v": vertices.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])}); break;
                    case "vt": textureCoords.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])}); break;
                    case "vn": normals.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])}); break;
                    case "f":
                        int numPoints = parts.length - 1;
                        int[][] faceData = new int[numPoints][3];
                        for (int i = 0; i < numPoints; i++) {
                            String[] indices = parts[i + 1].split("/");
                            faceData[i][0] = Integer.parseInt(indices[0]) - 1;
                            faceData[i][1] = (indices.length > 1 && !indices[1].isEmpty()) ? Integer.parseInt(indices[1]) - 1 : -1;
                            faceData[i][2] = (indices.length > 2 && !indices[2].isEmpty()) ? Integer.parseInt(indices[2]) - 1 : -1;
                        }
                        faces.add(faceData);
                        break;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glTranslatef(0.0f, -8.0f, zoom);
        gl.glRotatef(rotateX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(rotateY, 0.0f, 1.0f, 0.0f);

        // --- MULTI-TEXTURA ---

        // Capa 0: Base
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        if (texBase != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            texBase.bind(gl);
            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
        }

        // Capa 1: Spec/Rostro (se suma sobre la base)
        gl.glActiveTexture(GL2.GL_TEXTURE1);
        if (texSpec != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            texSpec.bind(gl);
            // Usamos GL_ADD para que las partes claras del spec "brillen" sobre la base
            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_ADD);
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
        }

        gl.glColor3f(1.0f, 1.0f, 1.0f);

        gl.glBegin(GL2.GL_TRIANGLES);
        for (int[][] face : faces) {
            if (face.length == 4) {
                drawTriangle(gl, face[0], face[1], face[2]);
                drawTriangle(gl, face[0], face[2], face[3]);
            } else {
                drawTriangle(gl, face[0], face[1], face[2]);
            }
        }
        gl.glEnd();

        // Limpieza
        gl.glActiveTexture(GL2.GL_TEXTURE1);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        if (texBase != null) gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void drawTriangle(GL2 gl, int[] p1, int[] p2, int[] p3) {
        drawVertex(gl, p1); drawVertex(gl, p2); drawVertex(gl, p3);
    }

    private void drawVertex(GL2 gl, int[] pointData) {
        if (pointData[2] >= 0 && pointData[2] < normals.size()) {
            float[] n = normals.get(pointData[2]);
            gl.glNormal3f(n[0], n[1], n[2]);
        }

        if (pointData[1] >= 0 && pointData[1] < textureCoords.size()) {
            float[] t = textureCoords.get(pointData[1]);
            float u = t[0];

            // CAMBIO 4: ARREGLO DE TEXTURA INVERTIDA
            // Hemos quitado el "1.0f - t[1]" y usamos t[1] directo.
            // Si antes se veía mal, ahora debería verse bien.
            float v = t[1];

            gl.glMultiTexCoord2f(GL2.GL_TEXTURE0, u, v);
            gl.glMultiTexCoord2f(GL2.GL_TEXTURE1, u, v);
        }

        if (pointData[0] >= 0 && pointData[0] < vertices.size()) {
            float[] pos = vertices.get(pointData[0]);
            gl.glVertex3f(pos[0], pos[1], pos[2]);
        }
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        if (height <= 0) height = 1;
        float aspect = (float) width / height;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float fovy = 45.0f;
        float zNear = 1.0f;
        float zFar = 1000.0f;
        float top = (float) Math.tan(Math.toRadians(fovy) / 2.0) * zNear;
        gl.glFrustum(-top * aspect, top * aspect, -top, top, zNear, zFar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override public void dispose(GLAutoDrawable drawable) { }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT) rotateY -= 5;
        if (k == KeyEvent.VK_RIGHT) rotateY += 5;
        if (k == KeyEvent.VK_UP) zoom += 1;
        if (k == KeyEvent.VK_DOWN) zoom -= 1;
        if (k == KeyEvent.VK_W) rotateX -= 5;
        if (k == KeyEvent.VK_S) rotateX += 5;
        if (k == KeyEvent.VK_ESCAPE) System.exit(0);
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}