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
import java.util.ArrayList;
import java.util.List;

public class SCP173Visualizer extends GLJPanel implements GLEventListener, KeyListener {

    static {
        System.setProperty("sun.java2d.uiScale", "1");
        System.setProperty("sun.java2d.d3d", "false");
    }

    private static final int FPS = 60;

    // --- RUTAS DE ARCHIVOS (Verifica que existan) ---
    private final String OBJ_PATH = "./data/173.obj";
    private final String TEX_BASE_PATH = "./data/173texture.jpg";
    private final String TEX_SPEC_PATH = "./data/173_spec.jpg";
    private final String TEX_NORM_PATH = "./data/173_norm.jpg"; // No usada en pipeline fijo

    // --- DATOS DEL MODELO ---
    private List<float[]> vertices = new ArrayList<>();
    private List<float[]> textureCoords = new ArrayList<>();
    private List<float[]> normals = new ArrayList<>();
    private List<int[][]> faces = new ArrayList<>();

    // --- TEXTURAS ---
    private Texture texBase;
    private Texture texSpec;

    // --- CÁMARA Y MOVIMIENTO ---
    private float rotateY = 0f;
    private float rotateX = 0f;
    private float zoom = -20.0f;

    // --- ESTADOS DE LUZ ---
    private boolean flashlightOn = true;  // Linterna empieza ENCENDIDA
    private boolean roomLightOn = false;  // Luz ambiente empieza APAGADA

    private static FPSAnimator animator;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile profile = GLProfile.getDefault();
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setHardwareAccelerated(true);
            caps.setDoubleBuffered(true);

            JFrame frame = new JFrame("SCP-173 Visualizer - [F] Linterna | [L] Luz Sala");
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

        // Fondo muy oscuro para resaltar la linterna
        gl.glClearColor(0.02f, 0.02f, 0.05f, 1.0f);
        gl.glClearDepth(1.0f);

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glShadeModel(GL2.GL_SMOOTH);

        // --- CONFIGURACIÓN DE ILUMINACIÓN ---
        gl.glEnable(GL2.GL_LIGHTING);

        // CONFIG: LUZ 0 (Ambiente General)
        float[] ambientLightColor = {0.3f, 0.3f, 0.3f, 1.0f}; // Luz tenue
        float[] ambientPos = {0.0f, 10.0f, 0.0f, 1.0f};      // Desde arriba
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, ambientLightColor, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, ambientPos, 0);

        // CONFIG: LUZ 1 (Linterna / Spotlight)
        float[] flashDiffuse = {1.0f, 0.98f, 0.85f, 1.0f}; // Color cálido brillante
        float[] flashSpecular = {1.0f, 1.0f, 1.0f, 1.0f};

        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, flashDiffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, flashSpecular, 0);

        // Parámetros del Cono (Spotlight)
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_CUTOFF, 20.0f);   // Ángulo de apertura (estrecho)
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_EXPONENT, 10.0f); // Intensidad en el centro

        // Atenuación de la linterna (se debilita con la distancia)
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_CONSTANT_ATTENUATION, 1.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_LINEAR_ATTENUATION, 0.05f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_QUADRATIC_ATTENUATION, 0.0f);

        // Cargar recursos
        texBase = loadTexture(gl, TEX_BASE_PATH);
        texSpec = loadTexture(gl, TEX_SPEC_PATH);
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
            } else {
                System.err.println("Textura no encontrada: " + path);
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

        // ==========================================
        //         LÓGICA DE LA LINTERNA
        // ==========================================

        // 1. Control Luz Ambiente
        if (roomLightOn) gl.glEnable(GL2.GL_LIGHT0);
        else gl.glDisable(GL2.GL_LIGHT0);

        // 2. Control Linterna
        if (flashlightOn) {
            gl.glEnable(GL2.GL_LIGHT1);
            // La luz se coloca en (0,0,0) ANTES de mover el mundo.
            // Esto hace que la luz esté pegada a la "cámara".
            float[] lightPos = {0.0f, 0.0f, 0.0f, 1.0f};
            float[] spotDir = {0.0f, 0.0f, -1.0f}; // Apunta hacia el fondo (donde vemos)

            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, lightPos, 0);
            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPOT_DIRECTION, spotDir, 0);
        } else {
            gl.glDisable(GL2.GL_LIGHT1);
        }

        // ==========================================
        //       TRANSFORMACIÓN DEL MODELO
        // ==========================================
        gl.glTranslatef(0.0f, -8.0f, zoom);
        gl.glRotatef(rotateX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(rotateY, 0.0f, 1.0f, 0.0f);

        // Configuración de Material
        float[] matAmbient = {0.2f, 0.2f, 0.2f, 1.0f};
        float[] matDiffuse = {1.0f, 1.0f, 1.0f, 1.0f}; // Blanco para reflejar la textura
        float[] matSpecular = {0.5f, 0.5f, 0.5f, 1.0f};
        float matShininess = 50.0f;

        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_AMBIENT, matAmbient, 0);
        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_DIFFUSE, matDiffuse, 0);
        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_SPECULAR, matSpecular, 0);
        gl.glMaterialf(GL2.GL_FRONT, GL2.GL_SHININESS, matShininess);

        // ==========================================
        //             DIBUJO DEL MODELO
        // ==========================================

        // Capa 0: Base
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        if (texBase != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            texBase.bind(gl);
            // MODULATE: Mezcla el color de la textura con la luz (linterna)
            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
        }

        // Capa 1: Specular
        gl.glActiveTexture(GL2.GL_TEXTURE1);
        if (texSpec != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            texSpec.bind(gl);
            // ADD: Suma brillo en zonas específicas
            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_ADD);
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
        }

        gl.glColor3f(1.0f, 1.0f, 1.0f); // Color base blanco para no teñir la textura

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

        // Limpieza de estados
        gl.glActiveTexture(GL2.GL_TEXTURE1);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        if (texBase != null) gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void drawTriangle(GL2 gl, int[] p1, int[] p2, int[] p3) {
        drawVertex(gl, p1); drawVertex(gl, p2); drawVertex(gl, p3);
    }

    private void drawVertex(GL2 gl, int[] pointData) {
        // Normales (Crucial para que la luz funcione)
        if (pointData[2] >= 0 && pointData[2] < normals.size()) {
            float[] n = normals.get(pointData[2]);
            gl.glNormal3f(n[0], n[1], n[2]);
        }

        // Texturas
        if (pointData[1] >= 0 && pointData[1] < textureCoords.size()) {
            float[] t = textureCoords.get(pointData[1]);
            float u = t[0];
            float v = t[1]; // Ajuste de coordenadas si es necesario

            gl.glMultiTexCoord2f(GL2.GL_TEXTURE0, u, v);
            gl.glMultiTexCoord2f(GL2.GL_TEXTURE1, u, v);
        }

        // Posición
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

        // Movimiento Cámara / Modelo
        if (k == KeyEvent.VK_LEFT) rotateY -= 5;
        if (k == KeyEvent.VK_RIGHT) rotateY += 5;
        if (k == KeyEvent.VK_UP) zoom += 1;
        if (k == KeyEvent.VK_DOWN) zoom -= 1;
        if (k == KeyEvent.VK_W) rotateX -= 5;
        if (k == KeyEvent.VK_S) rotateX += 5;

        // ACCIONES
        if (k == KeyEvent.VK_F) flashlightOn = !flashlightOn; // Linterna
        if (k == KeyEvent.VK_L) roomLightOn = !roomLightOn;   // Luz Sala

        if (k == KeyEvent.VK_ESCAPE) System.exit(0);

    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}