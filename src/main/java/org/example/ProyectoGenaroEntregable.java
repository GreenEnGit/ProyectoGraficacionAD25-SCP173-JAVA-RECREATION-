package org.example;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Proyecto hecho por Raul Alberto Guerrero Aguilar y Ramirez Sánchez Luis Eduardo para la materia de Graficación
// Solución aplicada: Clases internas cambiadas a public para evitar errores de acceso.

public class ProyectoGenaroEntregable extends GLJPanel implements GLEventListener, KeyListener, MouseMotionListener {

    private static final int FPS = 60;

    // --- RUTAS DE MODELOS ---
    private final String MAP_OBJ_PATH = "./data/camaracontencion.obj";
    private final String SCP_OBJ_PATH = "./data/173.obj";
    private final String SCP_TEX_PATH = "./data/173texture.jpg";

    // --- CONFIGURACIÓN DE JUGADOR ---
    private float camX = -2.95f;
    private float camY = -1.25f;
    private final float CAMERA_TILT = -10.0f;
    private float camZ = 5.53f;
    private final boolean INVERT_V = false;

    private float viewAngleX = 8f;
    private float viewAngleY = 387f;

    private final float MOVE_SPEED = 0.15f;
    private final float PLAYER_RADIUS = 0.1f;

    private boolean noclipMode = false;

    // --- CONFIGURACIÓN DE LUCES ---
    private boolean flashlightActive = true;
    private boolean globalLightActive = false;
    private final float FLASHLIGHT_Y_OFFSET = 0.2f;

    private final float GLOBAL_AMBIENT_LOW = 0.05f;
    private final float GLOBAL_AMBIENT_HIGH = 0.7f;

    // --- MECÁNICA DE SCP-173 Y PARPADEO ---
    private boolean isBlinking = false;
    private int blinkTimer = 0;
    private final int BLINK_DURATION = 10;
    private final float SCP_MOVE_DISTANCE = 4.0f;
    private final float KILL_DISTANCE = 1.5f;
    private boolean isDead = false;

    // --- ENTIDAD SCP-173 ---
    private SCP173Entity scp173;

    // CAMBIO: public static class para evitar errores de acceso
    public static class SCP173Entity {
        List<float[]> vertices = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<int[][]> faces = new ArrayList<>();
        Texture texture;

        float scale = 1.0f;
        float x, y, z;
        float heightOffset = 0f;

        // Variable para la rotación del SCP
        float rotationY = 0f;

        float rawMinX, rawMaxX, rawMinZ, rawMaxZ;
        public float minWorldX, maxWorldX;
        public float minWorldZ, maxWorldZ;

        public void init(GL2 gl, String objPath, String texPath, float targetHeight, float spawnX, float spawnY, float spawnZ, float playerX, float playerZ) {
            this.x = spawnX;
            this.y = spawnY;
            this.z = spawnZ;

            loadObj(objPath);
            loadTexture(gl, texPath);
            calculateScaleAndCollision(targetHeight, playerX, playerZ);
        }

        // Método updatePosition: Calcula la rotación
        public void updatePosition(float newX, float newZ, float playerX, float playerZ) {
            this.x = newX;
            this.z = newZ;

            // --- LÓGICA DE ROTACIÓN (MIRAR AL JUGADOR) ---
            float dx = playerX - this.x;
            float dz = playerZ - this.z;
            // Sumamos 180 grados para corregir la orientación del modelo
            this.rotationY = (float) Math.toDegrees(Math.atan2(dx, dz)) + 180.0f;

            // Recalcular Bounding Box
            minWorldX = x + (rawMinX * scale);
            maxWorldX = x + (rawMaxX * scale);
            minWorldZ = z + (rawMinZ * scale);
            maxWorldZ = z + (rawMaxZ * scale);
        }

        private void calculateScaleAndCollision(float targetHeight, float playerX, float playerZ) {
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            rawMinX = Float.MAX_VALUE; rawMaxX = -Float.MAX_VALUE;
            rawMinZ = Float.MAX_VALUE; rawMaxZ = -Float.MAX_VALUE;

            for(float[] v : vertices) {
                if(v[1] < minY) minY = v[1];
                if(v[1] > maxY) maxY = v[1];
                if(v[0] < rawMinX) rawMinX = v[0];
                if(v[0] > rawMaxX) rawMaxX = v[0];
                if(v[2] < rawMinZ) rawMinZ = v[2];
                if(v[2] > rawMaxZ) rawMaxZ = v[2];
            }
            float rawHeight = maxY - minY;
            if(rawHeight == 0) rawHeight = 1;

            this.scale = targetHeight / rawHeight;
            this.heightOffset = -minY * scale;

            updatePosition(this.x, this.z, playerX, playerZ);
        }

        private void loadTexture(GL2 gl, String path) {
            try {
                File f = new File(path);
                if(f.exists()) {
                    texture = TextureIO.newTexture(f, true);
                    texture.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                    texture.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
                }
            } catch (Exception e) { }
        }

        private void loadObj(String path) {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) continue;
                    String[] parts = line.split("\\s+");
                    switch (parts[0]) {
                        case "v": vertices.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])}); break;
                        case "vt": uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])}); break;
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

        public void draw(GL2 gl) {
            gl.glPushMatrix();
            gl.glTranslatef(x, y + heightOffset, z);

            // APLICAR ROTACIÓN HACIA EL JUGADOR
            gl.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);

            gl.glScalef(scale, scale, scale);

            if (texture != null) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                texture.bind(gl);
                gl.glColor3f(1f, 1f, 1f);
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor3f(0.8f, 0.7f, 0.6f);
            }

            gl.glBegin(GL2.GL_TRIANGLES);
            for (int[][] face : faces) {
                int nv = face.length;
                if (nv < 3) continue;
                for (int i = 1; i < nv - 1; i++) {
                    drawFace(gl, face[0], face[i], face[i+1]);
                }
            }
            gl.glEnd();
            gl.glPopMatrix();
        }

        private void drawFace(GL2 gl, int[] p1, int[] p2, int[] p3) {
            drawVertex(gl, p1);
            drawVertex(gl, p2);
            drawVertex(gl, p3);
        }

        private void drawVertex(GL2 gl, int[] p) {
            if (p == null) return;
            if (p[2] >= 0 && p[2] < normals.size()) { float[] n = normals.get(p[2]); gl.glNormal3f(n[0], n[1], n[2]); }
            if (p[1] >= 0 && p[1] < uvs.size()) { float[] t = uvs.get(p[1]); gl.glTexCoord2f(t[0], t[1]); }
            if (p[0] >= 0 && p[0] < vertices.size()) { gl.glVertex3f(vertices.get(p[0])[0], vertices.get(p[0])[1], vertices.get(p[0])[2]); }
        }
    }

    // --- VARIABLES MAPA Y COLISIONES ---

    // CAMBIO: public static class para evitar errores de acceso
    public static class CollisionWall {
        float x1, z1, x2, z2;
        float minX, maxX, minZ, maxZ;
        public CollisionWall(float x1, float z1, float x2, float z2) {
            this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
            this.minX = Math.min(x1, x2); this.maxX = Math.max(x1, x2);
            this.minZ = Math.min(z1, z2); this.maxZ = Math.max(z1, z2);
        }
    }

    private List<CollisionWall> mapWalls = new ArrayList<>();
    private final Map<String, String> textureMapping = new HashMap<>();
    private List<float[]> vertices = new ArrayList<>();
    private List<float[]> uvs = new ArrayList<>();
    private List<float[]> normals = new ArrayList<>();

    // CAMBIO: public static class para evitar errores de acceso
    public static class MaterialGroup {
        String materialName;
        List<int[][]> faces = new ArrayList<>();
        public MaterialGroup(String name) { this.materialName = name; }
    }

    private List<MaterialGroup> renderGroups = new ArrayList<>();
    private Map<String, Texture> loadedTextures = new HashMap<>();

    private float modelScale = 1.0f;
    private float centerX, centerY, centerZ;
    private float rawMinX = Float.MAX_VALUE, rawMaxX = -Float.MAX_VALUE;
    private float rawMinY = Float.MAX_VALUE, rawMaxY = -Float.MAX_VALUE;
    private float rawMinZ = Float.MAX_VALUE, rawMaxZ = -Float.MAX_VALUE;

    private Robot robot;
    private Point centerPoint;
    private boolean isRobotMoving = false;
    private float mouseSensitivity = 0.15f;
    private boolean[] keys = new boolean[256];

    private TextRenderer textRenderer;
    private static FPSAnimator animator;
    private boolean showDebugCollisions = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile profile = GLProfile.getDefault();
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setHardwareAccelerated(true);
            caps.setDoubleBuffered(true);
            caps.setDepthBits(24);

            JFrame frame = new JFrame("SCP-173 - Final Game Logic");
            ProyectoGenaroEntregable panel = new ProyectoGenaroEntregable(caps);

            frame.getContentPane().add(panel);
            frame.setSize(1280, 720);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                    cursorImg, new Point(0, 0), "blank cursor");
            frame.getContentPane().setCursor(blankCursor);

            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { if (animator != null) animator.stop(); System.exit(0); }
            });

            frame.setVisible(true);
            animator = new FPSAnimator(panel, FPS, true);
            animator.start();
        });
    }

    public ProyectoGenaroEntregable(GLCapabilities caps) {
        super(caps);
        setupTextureMap();
        this.addGLEventListener(this);
        this.addKeyListener(this);
        this.addMouseMotionListener(this);
        this.setFocusable(true);
        this.requestFocusInWindow();
        try { robot = new Robot(); } catch (AWTException e) { e.printStackTrace(); }
    }

    private void setupTextureMap() {
        textureMapping.put("metal", "metal.jpeg");
        textureMapping.put("white", "white.jpeg");
        textureMapping.put("whitewall", "white.jpeg");
        textureMapping.put("dirtymetal", "dirtymetal.jpeg");
        textureMapping.put("floor", "tilefloor.jpeg");
        textureMapping.put("tilefloor", "tilefloor.jpeg");
        textureMapping.put("door", "Door01.jpeg");
        textureMapping.put("bigdoor", "containment_doors.jpg");
        textureMapping.put("containment_doors", "containment_doors.jpeg");
        textureMapping.put("glass", "glass.jpeg");
        textureMapping.put("controlpanel", "controlpanel.jpeg");
        textureMapping.put("vent", "vent.jpeg");
        textureMapping.put("monitor", "flat_monitor.jpeg");
        textureMapping.put("screen", "flat_monitor.jpeg");
        textureMapping.put("keyboard", "keyboard.jpeg");
        textureMapping.put("seat", "officeseat_a.jpeg");
        textureMapping.put("cabinet", "cabinet_a.jpeg");
        textureMapping.put("logo", "scplogo.jpeg");
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl.glClearDepth(1.0f);

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(1.0f, 1.0f);

        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE);

        gl.glMaterialf(GL2.GL_FRONT, GL2.GL_SHININESS, 0.0f);
        float[] specularColor = {0.0f, 0.0f, 0.0f, 1.0f};
        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_SPECULAR, specularColor, 0);

        textRenderer = new TextRenderer(new Font("Monospaced", Font.BOLD, 14));

        loadMapObj(MAP_OBJ_PATH);
        loadMapTextures(gl);

        float rawX = 8.50f;
        float rawY = -1.25f;
        float rawZ = -2.30f;

        float finalX = (rawX - centerX) * modelScale;
        float finalY = (rawY - centerY) * modelScale;
        float finalZ = (rawZ - centerZ) * modelScale;

        if (Float.isNaN(finalX) || Math.abs(finalX) > 100) {
            finalX = 0.0f; finalY = -1.25f; finalZ = 0.0f;
        }

        scp173 = new SCP173Entity();
        scp173.init(gl, SCP_OBJ_PATH, SCP_TEX_PATH, 1.8f, finalX, finalY, finalZ, camX, camZ);

        setupLighting(gl);
    }

    private void setupLighting(GL2 gl) {
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT1);

        float[] globalAmbient = { GLOBAL_AMBIENT_LOW, GLOBAL_AMBIENT_LOW, GLOBAL_AMBIENT_LOW, 1.0f };
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

        float[] ambient = {0.0f, 0.0f, 0.0f, 1.0f};
        float[] diffuse = {1.5f, 1.5f, 1.3f, 1.0f};
        float[] specular = {0.0f, 0.0f, 0.0f, 1.0f};

        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, ambient, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, specular, 0);

        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_CUTOFF, 30.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_EXPONENT, 25.0f);

        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_CONSTANT_ATTENUATION, 1.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_LINEAR_ATTENUATION, 0.01f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_QUADRATIC_ATTENUATION, 0.005f);
    }

    // --- LOGICA DE MOVIMIENTO DE SCP ---
    private void triggerBlink() {
        if (isDead) return;

        isBlinking = true;
        blinkTimer = BLINK_DURATION;

        // Calcular vector dirección hacia el jugador
        float dx = camX - scp173.x;
        float dz = camZ - scp173.z;
        float distance = (float) Math.sqrt(dx*dx + dz*dz);

        if (distance < KILL_DISTANCE) {
            isDead = true;
        } else {
            // Mover SCP hacia el jugador
            float moveFactor = Math.min(distance, SCP_MOVE_DISTANCE);
            float moveX = (dx / distance) * moveFactor;
            float moveZ = (dz / distance) * moveFactor;

            // Actualizar posición Y ROTACIÓN (pasamos la posición del jugador)
            scp173.updatePosition(scp173.x + moveX, scp173.z + moveZ, camX, camZ);

            // Verificación final tras mover
            float newDist = (float) Math.sqrt(Math.pow(camX - scp173.x, 2) + Math.pow(camZ - scp173.z, 2));
            if (newDist < KILL_DISTANCE) {
                isDead = true;
            }
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        if (isDead) {
            drawDeathScreen(drawable);
            return;
        }

        handleMovement();

        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glRotatef(viewAngleX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(viewAngleY, 0.0f, 1.0f, 0.0f);

        float ambientLevel = globalLightActive ? GLOBAL_AMBIENT_HIGH : GLOBAL_AMBIENT_LOW;
        float[] globalAmbient = { ambientLevel, ambientLevel, ambientLevel, 1.0f };
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

        if (flashlightActive && !globalLightActive) {
            gl.glEnable(GL2.GL_LIGHT1);
            gl.glPushMatrix();
            float[] lightPosEye = {0.0f, 0.0f, 0.0f, 1.0f};
            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, lightPosEye, 0);
            gl.glRotatef(CAMERA_TILT, 1.0f, 0.0f, 0.0f);
            float[] spotDir = {0.0f, 0.0f, -1.0f};
            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPOT_DIRECTION, spotDir, 0);
            gl.glPopMatrix();
        } else {
            gl.glDisable(GL2.GL_LIGHT1);
        }

        gl.glTranslatef(-camX, -camY, -camZ);

        if (!isBlinking) {
            gl.glPushMatrix();
            gl.glScalef(modelScale, modelScale, modelScale);
            gl.glTranslatef(-centerX, -centerY, -centerZ);
            drawMapModel(gl, false);
            drawMapModel(gl, true);
            if (showDebugCollisions && !noclipMode) drawDebugWalls(gl);
            gl.glPopMatrix();

            if(scp173 != null) {
                scp173.draw(gl);
            }

            if (showDebugCollisions && !noclipMode) drawDebugSCPBox(gl);
        }

        drawHUD(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
    }

    private void drawDeathScreen(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Fondo Rojo Sangre
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0, drawable.getSurfaceWidth(), 0, drawable.getSurfaceHeight(), -1, 1);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glColor3f(0.6f, 0.0f, 0.0f);

        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(drawable.getSurfaceWidth(), 0);
        gl.glVertex2f(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        gl.glVertex2f(0, drawable.getSurfaceHeight());
        gl.glEnd();

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_DEPTH_TEST);

        // Texto
        textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        textRenderer.setColor(Color.WHITE);
        textRenderer.draw("CRUNCH - CUELLO ROTO", drawable.getSurfaceWidth()/2 - 100, drawable.getSurfaceHeight()/2);
        textRenderer.draw("Presiona ESC para salir", drawable.getSurfaceWidth()/2 - 110, drawable.getSurfaceHeight()/2 - 30);
        textRenderer.endRendering();

        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    private void drawDebugSCPBox(GL2 gl) {
        if (scp173 == null) return;
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glLineWidth(2.0f);
        gl.glColor3f(1.0f, 1.0f, 0.0f);

        float minX = scp173.minWorldX;
        float maxX = scp173.maxWorldX;
        float minZ = scp173.minWorldZ;
        float maxZ = scp173.maxWorldZ;
        float Y = scp173.y;

        gl.glBegin(GL2.GL_LINES);
        float Y_top = Y + 1.8f;
        gl.glVertex3f(minX, Y, minZ); gl.glVertex3f(maxX, Y, minZ);
        gl.glVertex3f(maxX, Y, minZ); gl.glVertex3f(maxX, Y, maxZ);
        gl.glVertex3f(maxX, Y, maxZ); gl.glVertex3f(minX, Y, maxZ);
        gl.glVertex3f(minX, Y, maxZ); gl.glVertex3f(minX, Y, minZ);
        gl.glVertex3f(minX, Y_top, minZ); gl.glVertex3f(maxX, Y_top, minZ);
        gl.glVertex3f(maxX, Y_top, minZ); gl.glVertex3f(maxX, Y_top, maxZ);
        gl.glVertex3f(maxX, Y_top, maxZ); gl.glVertex3f(minX, Y_top, maxZ);
        gl.glVertex3f(minX, Y_top, maxZ); gl.glVertex3f(minX, Y_top, minZ);
        gl.glVertex3f(minX, Y, minZ); gl.glVertex3f(minX, Y_top, minZ);
        gl.glVertex3f(maxX, Y, minZ); gl.glVertex3f(maxX, Y_top, minZ);
        gl.glVertex3f(maxX, Y, maxZ); gl.glVertex3f(maxX, Y_top, maxZ);
        gl.glVertex3f(minX, Y, maxZ); gl.glVertex3f(minX, Y_top, maxZ);
        gl.glEnd();

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
    }

    private void drawDebugWalls(GL2 gl) {
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glLineWidth(3.0f);
        gl.glColor3f(1.0f, 0.0f, 0.0f);

        gl.glBegin(GL2.GL_LINES);
        for (CollisionWall w : mapWalls) {
            gl.glVertex3f(w.x1, centerY, w.z1);
            gl.glVertex3f(w.x2, centerY, w.z2);
            gl.glVertex3f(w.x1, centerY - 2.0f, w.z1);
            gl.glVertex3f(w.x2, centerY - 2.0f, w.z2);
        }
        gl.glEnd();

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
    }

    private boolean checkSCPCollision(float targetX, float targetZ) {
        if (scp173 == null) return false;
        float playerMinX = targetX - PLAYER_RADIUS;
        float playerMaxX = targetX + PLAYER_RADIUS;
        float playerMinZ = targetZ - PLAYER_RADIUS;
        float playerMaxZ = targetZ + PLAYER_RADIUS;
        float scpMinX = scp173.minWorldX;
        float scpMaxX = scp173.maxWorldX;
        float scpMinZ = scp173.minWorldZ;
        float scpMaxZ = scp173.maxWorldZ;
        boolean overlapX = playerMaxX > scpMinX && playerMinX < scpMaxX;
        boolean overlapZ = playerMaxZ > scpMinZ && playerMinZ < scpMaxZ;
        return overlapX && overlapZ;
    }

    private void handleMovement() {
        if (isBlinking || isDead) return;

        float dx = 0, dz = 0;
        double rads = Math.toRadians(viewAngleY);

        if (keys[KeyEvent.VK_W]) { dx += Math.sin(rads) * MOVE_SPEED; dz -= Math.cos(rads) * MOVE_SPEED; }
        if (keys[KeyEvent.VK_S]) { dx -= Math.sin(rads) * MOVE_SPEED; dz += Math.cos(rads) * MOVE_SPEED; }
        if (keys[KeyEvent.VK_A]) { dx -= Math.cos(rads) * MOVE_SPEED; dz -= Math.sin(rads) * MOVE_SPEED; }
        if (keys[KeyEvent.VK_D]) { dx += Math.cos(rads) * MOVE_SPEED; dz += Math.sin(rads) * MOVE_SPEED; }

        if (dx != 0) {
            float nextX = camX + dx;
            if (noclipMode || (!isCollidingWithWall(nextX, camZ) && !checkSCPCollision(nextX, camZ))) camX = nextX;
        }
        if (dz != 0) {
            float nextZ = camZ + dz;
            if (noclipMode || (!isCollidingWithWall(camX, nextZ) && !checkSCPCollision(camX, nextZ))) camZ = nextZ;
        }
    }

    private boolean isCollidingWithWall(float targetX, float targetZ) {
        float playerObjX = (targetX / modelScale) + centerX;
        float playerObjZ = (targetZ / modelScale) + centerZ;
        float radiusObj = PLAYER_RADIUS / modelScale;

        for (CollisionWall wall : mapWalls) {
            if (playerObjX < wall.minX - radiusObj || playerObjX > wall.maxX + radiusObj ||
                    playerObjZ < wall.minZ - radiusObj || playerObjZ > wall.maxZ + radiusObj) continue;

            if (distancePointToSegment(playerObjX, playerObjZ, wall.x1, wall.z1, wall.x2, wall.z2) < radiusObj) {
                return true;
            }
        }
        return false;
    }

    private float distancePointToSegment(float px, float pz, float x1, float z1, float x2, float z2) {
        float l2 = (x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1);
        if (l2 == 0) return (float) Math.hypot(px - x1, pz - z1);
        float t = ((px - x1) * (x2 - x1) + (pz - z1) * (z2 - z1)) / l2;
        t = Math.max(0, Math.min(1, t));
        float projX = x1 + t * (x2 - x1);
        float projZ = z1 + t * (z2 - z1);
        return (float) Math.hypot(px - projX, pz - projZ);
    }

    private void loadMapObj(String path) {
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
                        currentGroup = new MaterialGroup(matName);
                        renderGroups.add(currentGroup);
                        break;
                    case "v":
                        float x = Float.parseFloat(parts[1]), y = Float.parseFloat(parts[2]), z = Float.parseFloat(parts[3]);
                        vertices.add(new float[]{x, y, z});
                        rawMinX = Math.min(rawMinX, x); rawMaxX = Math.max(rawMaxX, x);
                        rawMinY = Math.min(rawMinY, y); rawMaxY = Math.max(rawMaxY, y);
                        rawMinZ = Math.min(rawMinZ, z); rawMaxZ = Math.max(rawMaxZ, z);
                        break;
                    case "vt": uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])}); break;
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
                        currentGroup.faces.add(faceData);

                        if (numPoints >= 3) {
                            float[] n = (faceData[0][2] >= 0) ? normals.get(faceData[0][2]) : new float[]{0, 1, 0};
                            float absNy = Math.abs(n[1]);

                            if (absNy < 0.5f) {
                                float[] v1 = vertices.get(faceData[0][0]);
                                float[] v2 = vertices.get(faceData[1][0]);
                                float[] v3 = vertices.get(faceData[2][0]);

                                String matNameLower = currentGroup.materialName.toLowerCase();
                                boolean mightBeWall = matNameLower.contains("white") || matNameLower.contains("wall");
                                boolean isMetalFrame = matNameLower.contains("metal") ||
                                        matNameLower.contains("frame") ||
                                        matNameLower.contains("door") ||
                                        matNameLower.contains("glass") ||
                                        matNameLower.contains("dark");

                                if (mightBeWall && !isMetalFrame) {
                                    mapWalls.add(new CollisionWall(v1[0], v1[2], v2[0], v2[2]));
                                    mapWalls.add(new CollisionWall(v2[0], v2[2], v3[0], v3[2]));
                                    mapWalls.add(new CollisionWall(v3[0], v3[2], v1[0], v1[2]));
                                }
                            }
                        }
                        break;
                }
            }
            normalizeModel();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void normalizeModel() {
        centerX = (rawMaxX + rawMinX) / 2.0f;
        centerY = (rawMaxY + rawMinY) / 2.0f;
        centerZ = (rawMaxZ + rawMinZ) / 2.0f;
        float maxDim = Math.max(rawMaxX - rawMinX, Math.max(rawMaxY - rawMinY, rawMaxZ - rawMinZ));
        if (maxDim <= 0.0001f) {
            modelScale = 1.0f;
        } else {
            modelScale = 20.0f / maxDim;
        }
    }

    private void drawHUD(int width, int height) {
        if (isBlinking) {
            GL2 gl = GLContext.getCurrentGL().getGL2();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glOrtho(0, width, 0, height, -1, 1);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();

            gl.glDisable(GL2.GL_LIGHTING);
            gl.glColor3f(0.0f, 0.0f, 0.0f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(width, 0);
            gl.glVertex2f(width, height);
            gl.glVertex2f(0, height);
            gl.glEnd();
            gl.glEnable(GL2.GL_LIGHTING);

            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);

            blinkTimer--;
            if (blinkTimer <= 0) {
                isBlinking = false;
            }
            return;
        }

        textRenderer.beginRendering(width, height);

        if (noclipMode) {
            textRenderer.setColor(Color.RED);
            textRenderer.draw("MODO FANTASMA (NOCLIP) ACTIVADO", width/2 - 100, height - 80);
        }

        textRenderer.setColor(Color.GREEN);
        textRenderer.draw(String.format("POS: %.2f, %.2f, %.2f", camX, camY, camZ), 10, height - 20);
        textRenderer.draw(String.format("ANG: %.1f / %.1f", viewAngleX, viewAngleY), 10, height - 40);
        textRenderer.draw("[ESPACIO] Parpadear", 10, height - 60);
        textRenderer.draw("[F] Linterna: " + (flashlightActive ? "ON" : "OFF"), 10, height - 80);
        textRenderer.draw("[L] Luz Global: " + (globalLightActive ? "ON" : "OFF"), 10, height - 95);

        textRenderer.endRendering();
    }
    private void loadMapTextures(GL2 gl) {
        for (MaterialGroup group : renderGroups) {
            String matName = group.materialName.toLowerCase();
            if (!loadedTextures.containsKey(matName)) {
                String fileName = textureMapping.getOrDefault(matName,
                        textureMapping.entrySet().stream().filter(e -> matName.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("metal.jpeg"));
                try {
                    File f = new File("./data/" + fileName);
                    if (f.exists()) {
                        Texture t = TextureIO.newTexture(f, true);
                        t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT); t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
                        loadedTextures.put(matName, t);
                    }
                } catch (Exception e) { }
            }
        }
    }

    private void drawMapModel(GL2 gl, boolean transparentPass) {
        Texture fallbackTexture = loadedTextures.get("metal");

        for (MaterialGroup group : renderGroups) {
            String matName = group.materialName.toLowerCase();
            boolean isGlass = matName.contains("glass");
            if (transparentPass != isGlass) continue;

            Texture t = loadedTextures.get(matName);
            if (t == null) t = fallbackTexture;

            if (t != null) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                t.bind(gl);
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);

                if(isGlass) {
                    gl.glColor4f(0.8f,0.9f,1f,0.4f); gl.glDepthMask(false);
                } else {
                    gl.glColor3f(0.8f, 0.8f, 0.8f); gl.glDepthMask(true);
                }
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor3f(0.5f,0.5f,0.5f);
            }

            gl.glBegin(GL2.GL_TRIANGLES);
            for (int[][] face : group.faces) {
                int nv = face.length;
                if (nv < 3) continue;
                for (int i = 1; i < nv - 1; i++) {
                    drawVertex(gl, face[0]);
                    drawVertex(gl, face[i]);
                    drawVertex(gl, face[i+1]);
                }
            }
            gl.glEnd();

            if(isGlass) gl.glDepthMask(true);
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void drawVertex(GL2 gl, int[] p) {
        if (p == null) return;
        if (p[2] >= 0 && p[2] < normals.size()) {
            float[] n = normals.get(p[2]); gl.glNormal3f(n[0], n[1], n[2]);
        }
        if (p[1] >= 0 && p[1] < uvs.size()) {
            float[] t = uvs.get(p[1]); gl.glTexCoord2f(t[0], INVERT_V ? 1 - t[1] : t[1]);
        }
        if (p[0] >= 0 && p[0] < vertices.size()) {
            float[] v = vertices.get(p[0]); gl.glVertex3f(v[0], v[1], v[2]);
        }
    }

    @Override public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2(); if (height <= 0) height = 1; float aspect = (float) width / height; gl.glViewport(0, 0, width, height);

        float zNear = 0.1f;
        float zFar = 200.0f;
        float fovy = 45.0f;

        if (this.isShowing()) { Point loc = this.getLocationOnScreen(); centerPoint = new Point(loc.x + width / 2, loc.y + height / 2); }
        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glLoadIdentity();
        float top = (float) Math.tan(Math.toRadians(fovy) / 2.0) * zNear;
        gl.glFrustum(-top * aspect, top * aspect, -top, top, zNear, zFar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }
    @Override public void dispose(GLAutoDrawable drawable) {}

    private float normalizeAngle360(float angle) {
        angle %= 360.0f;
        if (angle < 0) angle += 360.0f;
        return angle;
    }

    @Override public void keyPressed(KeyEvent e) {
        if(e.getKeyCode() < 256) keys[e.getKeyCode()] = true;

        if(e.getKeyCode() == KeyEvent.VK_N) {
            noclipMode = !noclipMode;
        }

        if(e.getKeyCode() == KeyEvent.VK_F) {
            flashlightActive = !flashlightActive;
            if (flashlightActive) {
                globalLightActive = false;
            }
        }

        if(e.getKeyCode() == KeyEvent.VK_L) {
            globalLightActive = !globalLightActive;
            if (globalLightActive) {
                flashlightActive = false;
            }
        }

        // TRIGGER DE PARPADEO
        if(e.getKeyCode() == KeyEvent.VK_SPACE && !isBlinking && !isDead) {
            triggerBlink();
        }

        if(e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
    }
    @Override public void keyReleased(KeyEvent e) { if(e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {
        if (isRobotMoving || centerPoint == null || isBlinking || isDead) return;
        int dx = e.getXOnScreen() - centerPoint.x;
        int dy = e.getYOnScreen() - centerPoint.y;
        if (dx == 0 && dy == 0) return;
        viewAngleY += dx * mouseSensitivity;
        viewAngleX += dy * mouseSensitivity;

        viewAngleY = normalizeAngle360(viewAngleY);

        if (viewAngleX > 89) viewAngleX = 89; if (viewAngleX < -89) viewAngleX = -89;
        isRobotMoving = true; robot.mouseMove(centerPoint.x, centerPoint.y); isRobotMoving = false;
    }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
}