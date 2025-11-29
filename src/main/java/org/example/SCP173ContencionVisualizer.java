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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SCP173ContencionVisualizer extends GLJPanel implements GLEventListener, KeyListener {

    private static final int FPS = 60;
    private final String MODEL_PATH = "./data/camaracontencion.obj";

    // --- CONFIGURACIÓN DE ORIENTACIÓN DE TEXTURAS ---
    private final boolean INVERT_V = false;
    private final boolean INVERT_U = false;

    // Mapeo de texturas
    private final Map<String, String> textureMapping = new HashMap<>();

    // Datos del modelo
    private List<float[]> vertices = new ArrayList<>();
    private List<float[]> uvs = new ArrayList<>();
    private List<float[]> normals = new ArrayList<>();

    // Grupos de renderizado
    private static class MaterialGroup {
        String materialName;
        List<int[][]> faces = new ArrayList<>();
        public MaterialGroup(String name) { this.materialName = name; }
    }
    private List<MaterialGroup> renderGroups = new ArrayList<>();

    // Texturas cargadas
    private Map<String, Texture> loadedTextures = new HashMap<>();

    // Variables de escena
    private float modelScale = 1.0f;
    private float centerX, centerY, centerZ;
    private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    // Cámara
    private float camX = 0f, camY = -5.0f, camZ = 0f;
    private float viewAngleX = 0f, viewAngleY = 0f;
    private float zoomDistance = -5.0f;

    private static FPSAnimator animator;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile profile = GLProfile.getDefault();
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setHardwareAccelerated(true);
            caps.setDoubleBuffered(true);
            caps.setDepthBits(24);

            JFrame frame = new JFrame("SCP-173 Visualizer - Final Textures");
            SCP173ContencionVisualizer panel = new SCP173ContencionVisualizer(caps);

            frame.getContentPane().add(panel);
            frame.setSize(1280, 720);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (animator != null) animator.stop();
                    System.exit(0);
                }
            });

            frame.setVisible(true);
            animator = new FPSAnimator(panel, FPS, true);
            animator.start();
        });
    }

    public SCP173ContencionVisualizer(GLCapabilities caps) {
        super(caps);

        // --- MAPEO DE TEXTURAS (Configurado por ti) ---
        textureMapping.put("metal", "metal.jpeg");
        textureMapping.put("white", "white.jpeg"); // Nota: Asegúrate que este archivo exista
        textureMapping.put("whitewall", "metal.jpeg");

        textureMapping.put("dirtymetal", "dirtymetal.jpeg");
        textureMapping.put("floor", "tilefloor.jpeg");
        textureMapping.put("tilefloor", "tilefloor.jpeg");
        textureMapping.put("door", "Door01.jpeg");
        textureMapping.put("bigdoor", "containment_doors.jpg");
        textureMapping.put("containment_doors", "containment_doors.jpeg");
        textureMapping.put("glass", "glass.jpeg");

        // Objetos
        textureMapping.put("controlpanel", "controlpanel.jpeg");
        textureMapping.put("vent", "vent.jpeg");
        textureMapping.put("monitor", "flat_monitor.jpeg");
        textureMapping.put("screen", "flat_monitor.jpeg");
        textureMapping.put("keyboard", "keyboard.jpeg");
        textureMapping.put("seat", "officeseat_a.jpeg");
        textureMapping.put("cabinet", "cabinet_a.jpeg");
        textureMapping.put("logo", "scplogo.jpeg");

        this.addGLEventListener(this);
        this.addKeyListener(this);
        this.setFocusable(true);
        this.requestFocusInWindow();
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Color de fondo (Gris muy oscuro / Azulado)
        gl.glClearColor(0.05f, 0.05f, 0.08f, 1.0f);

        gl.glClearDepth(1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glShadeModel(GL2.GL_SMOOTH);

        // Transparencias
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        // --- CONFIGURACIÓN DE MATERIALES PARA ILUMINACIÓN ---
        // Esto permite que el color de la textura reaccione a la luz
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE);

        // Configuración de brillo especular (para que el metal brille)
        gl.glMaterialf(GL2.GL_FRONT, GL2.GL_SHININESS, 60.0f); // Brillo concentrado
        float[] specularColor = {0.9f, 0.9f, 0.9f, 1.0f};
        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_SPECULAR, specularColor, 0);

        setupLighting(gl);
        loadObjFile(MODEL_PATH);
        loadTextures(gl);
    }

    private void setupLighting(GL2 gl) {
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0); // Luz Principal
        gl.glEnable(GL2.GL_LIGHT1); // Luz Secundaria

        // --- LUZ 0: Principal (Techo - Blanca Fría Intensa) ---
        float[] ambient0 = {0.1f, 0.1f, 0.12f, 1.0f};   // Ambiente bajo azulado
        float[] diffuse0 = {0.9f, 0.9f, 0.95f, 1.0f};   // Difusa blanca brillante
        float[] specular0 = {0.8f, 0.8f, 0.8f, 1.0f};   // Especular fuerte (brillos)
        float[] pos0 = {0.0f, 20.0f, 0.0f, 1.0f};       // Posición alta centrada

        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambient0, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuse0, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, specular0, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, pos0, 0);

        // --- LUZ 1: Relleno (Lateral - Cálida Tenue) ---
        // Ayuda a ver detalles en las sombras
        float[] ambient1 = {0.0f, 0.0f, 0.0f, 1.0f};
        float[] diffuse1 = {0.4f, 0.4f, 0.35f, 1.0f};   // Difusa amarillenta tenue
        float[] pos1 = {-15.0f, 5.0f, 15.0f, 1.0f};     // Desde una esquina

        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, ambient1, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, diffuse1, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, pos1, 0);
    }


    private void loadTextures(GL2 gl) {
        System.out.println("--- Cargando Texturas ---");
        for (MaterialGroup group : renderGroups) {
            String matName = group.materialName.toLowerCase();
            String fileName = null;

            if (textureMapping.containsKey(matName)) {
                fileName = textureMapping.get(matName);
            } else {
                for (String key : textureMapping.keySet()) {
                    if (matName.contains(key)) {
                        fileName = textureMapping.get(key);
                        break;
                    }
                }
            }
            if (fileName == null) fileName = "metal.jpeg";

            if (!loadedTextures.containsKey(matName)) {
                try {
                    File f = new File("./data/" + fileName);
                    if (f.exists()) {
                        Texture t = TextureIO.newTexture(f, true);
                        t.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                        t.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
                        t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
                        t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
                        loadedTextures.put(matName, t);
                    }
                } catch (Exception e) {
                    System.err.println("Error textura: " + fileName);
                }
            }
        }
    }

    private void loadObjFile(String path) {
        MaterialGroup currentGroup = new MaterialGroup("default");
        renderGroups.add(currentGroup);
        try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "usemtl":
                        String matName = parts[1];
                        if (currentGroup.faces.isEmpty()) currentGroup.materialName = matName;
                        else {
                            currentGroup = new MaterialGroup(matName);
                            renderGroups.add(currentGroup);
                        }
                        break;
                    case "v":
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        vertices.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                        break;
                    case "vt":
                        uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                        break;
                    case "vn":
                        normals.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                        break;
                    case "f":
                        int numPoints = parts.length - 1;
                        int[][] faceData = new int[numPoints][3];
                        for (int i = 0; i < numPoints; i++) {
                            String[] indices = parts[i + 1].split("/");
                            faceData[i][0] = Integer.parseInt(indices[0]) - 1;
                            faceData[i][1] = (indices.length > 1 && !indices[1].isEmpty()) ? Integer.parseInt(indices[1]) - 1 : -1;
                            faceData[i][2] = (indices.length > 2 && !indices[2].isEmpty()) ? Integer.parseInt(indices[2]) - 1 : -1;
                        }
                        currentGroup.faces.add(faceData);
                        break;
                }
            }
            normalizeModel();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void normalizeModel() {
        centerX = (maxX + minX) / 2.0f;
        centerY = (maxY + minY) / 2.0f;
        centerZ = (maxZ + minZ) / 2.0f;
        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        modelScale = 20.0f / maxDim;
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glTranslatef(0.0f, 0.0f, zoomDistance);
        gl.glRotatef(viewAngleX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(viewAngleY, 0.0f, 1.0f, 0.0f);
        gl.glTranslatef(-camX, -camY, -camZ);

        gl.glPushMatrix();
        gl.glScalef(modelScale, modelScale, modelScale);
        gl.glTranslatef(-centerX, -centerY, -centerZ);

        // Opacos
        drawModel(gl, false);
        // Transparentes (Cristal)
        drawModel(gl, true);

        gl.glPopMatrix();
    }

    private void drawModel(GL2 gl, boolean transparentPass) {
        for (MaterialGroup group : renderGroups) {
            String matName = group.materialName.toLowerCase();
            boolean isGlass = matName.contains("glass");
            if (transparentPass != isGlass) continue;

            Texture t = null;
            if (loadedTextures.containsKey(matName)) t = loadedTextures.get(matName);
            else {
                for(String key : loadedTextures.keySet()) {
                    if(matName.contains(key)) { t = loadedTextures.get(key); break; }
                }
            }

            if (t != null) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                t.bind(gl);
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
                if (isGlass) {
                    gl.glColor4f(0.8f, 0.9f, 1.0f, 0.4f);
                    gl.glDepthMask(false);
                } else {
                    gl.glColor3f(1f, 1f, 1f);
                    gl.glDepthMask(true);
                }
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor3f(0.6f, 0.6f, 0.6f);
            }

            gl.glBegin(GL2.GL_TRIANGLES);
            for (int[][] face : group.faces) {
                if (face.length == 4) {
                    drawVertex(gl, face[0]); drawVertex(gl, face[1]); drawVertex(gl, face[2]);
                    drawVertex(gl, face[0]); drawVertex(gl, face[2]); drawVertex(gl, face[3]);
                } else {
                    drawVertex(gl, face[0]); drawVertex(gl, face[1]); drawVertex(gl, face[2]);
                }
            }
            gl.glEnd();
            if (isGlass) gl.glDepthMask(true);
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void drawVertex(GL2 gl, int[] p) {
        if (p[2] >= 0) {
            float[] n = normals.get(p[2]);
            gl.glNormal3f(n[0], n[1], n[2]);
        }

        if (p[1] >= 0) {
            float[] t = uvs.get(p[1]);
            float u = t[0];
            float v = t[1];
            if (INVERT_U) u = 1.0f - u;
            if (INVERT_V) v = 1.0f - v;
            gl.glTexCoord2f(u, v);
        }

        if (p[0] >= 0) {
            float[] v = vertices.get(p[0]);
            gl.glVertex3f(v[0], v[1], v[2]);
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
        float fovy = 60.0f;
        float zNear = 0.1f;
        float zFar = 1000.0f;
        float top = (float) Math.tan(Math.toRadians(fovy) / 2.0) * zNear;
        gl.glFrustum(-top * aspect, top * aspect, -top, top, zNear, zFar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override public void dispose(GLAutoDrawable drawable) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        float moveSpeed = 0.5f;
        float rotSpeed = 2.0f;

        if (k == KeyEvent.VK_LEFT) viewAngleY -= rotSpeed;
        if (k == KeyEvent.VK_RIGHT) viewAngleY += rotSpeed;
        if (k == KeyEvent.VK_UP) viewAngleX -= rotSpeed;
        if (k == KeyEvent.VK_DOWN) viewAngleX += rotSpeed;

        double rads = Math.toRadians(viewAngleY);
        if (k == KeyEvent.VK_W) { camX += Math.sin(rads) * moveSpeed; camZ -= Math.cos(rads) * moveSpeed; }
        if (k == KeyEvent.VK_S) { camX -= Math.sin(rads) * moveSpeed; camZ += Math.cos(rads) * moveSpeed; }
        if (k == KeyEvent.VK_A) { camX -= Math.cos(rads) * moveSpeed; camZ -= Math.sin(rads) * moveSpeed; }
        if (k == KeyEvent.VK_D) { camX += Math.cos(rads) * moveSpeed; camZ += Math.sin(rads) * moveSpeed; }

        if (k == KeyEvent.VK_Q) camY += moveSpeed;
        if (k == KeyEvent.VK_E) camY -= moveSpeed;

        if (k == KeyEvent.VK_PLUS || k == KeyEvent.VK_ADD) zoomDistance += 0.5f;
        if (k == KeyEvent.VK_MINUS || k == KeyEvent.VK_SUBTRACT) zoomDistance -= 0.5f;
        if (k == KeyEvent.VK_ESCAPE) System.exit(0);
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}