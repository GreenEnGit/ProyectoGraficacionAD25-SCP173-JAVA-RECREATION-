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

public class ProyectoGraficacionAD25 extends GLJPanel implements GLEventListener, KeyListener, MouseMotionListener {

    private static final int FPS = 60;

    //  RUTAS DE MODELOS
    private final String MAP_OBJ_PATH = "./data/camaracontencion.obj";
    private final String SCP_OBJ_PATH = "./data/173.obj";
    private final String SCP_TEX_PATH = "./data/173texture.jpg";
    // MENU PRINCIPAL
    private boolean enMenu = true;
    private Texture texturaMenu;


    // CONFIGURACIÓN DE JUGADOR
    private float camX = -2.95f;
    private float camY = -1.25f;
    private final float INCLINACION = -10.0f;
    private float camZ = 5.53f;
    private final boolean INVERT_V = false;

    private float viewAngleX = 8f;
    private float viewAngleY = 387f;

    private final float VELOCIDAD_MOV = 0.13f;
    private final float ESPACIO_JUGADOR = 0.1f;

    private boolean modoEspectador = false;

    // CONFIGURACION DE LUCES
    private boolean estadoLinterna = true;
    private boolean luzGlobal = false;
    private final float alturaLinterna = 0.2f;

    private final float OSCURIDAD_BASE = 0.05f;
    private final float LUZ_MAXIMA = 0.7f;

    //  MECANICA DE SCP 173 Y PARPADEO
    private boolean parpadeo = false;
    private int contadorParpadeo = 0;
    private final int PARPADEO_DURACION = 10;
    private final float SCP_MOVIMIENTO_DISTANCIA = 4.0f;
    private final float DISTANCIA_MUERTE = 1.5f;
    private boolean muerte = false;
    // pestañeo forzado cada cierto tiempo
    private final int FRAMES_ENTRE_PESTANEOS = 300;
    private int contadorPestaneoForzado = 0;
    private boolean parpadeoForzado = false;
    private final int PARPADEO_DURACION_NORMAL = 10;
    private final int PARPADEO_DURACION_DOBLE = 20;
    private int vecesAvanceSCP = 1;
    private static final int NIVELES_BARRA_PARPADEO = 5;      // 5  1
    private int nivelBarraParpadeo = NIVELES_BARRA_PARPADEO;  // nivel actual de la barra
    private int contadorFramesBarra = 0;                      // cuenta frames para bajar la barra
    private final int FRAMES_POR_NIVEL_BARRA = FPS;           // baja 1 nivel cada 1 segundo (FPS)

    // Texturas de la barra de parpadeo
    private Texture[] texturasBarraParpadeo;



    //  ENTIDAD SCP 173
    private SCP173Entidad scp173;


    public static class SCP173Entidad {
        List<float[]> vertices = new ArrayList<>(); //vertices
        List<float[]> uvs = new ArrayList<>(); //coordenadas
        List<float[]> normals = new ArrayList<>();
        List<int[][]> faces = new ArrayList<>();
        Texture textura;

        float escala = 1.0f;
        float x, y, z;
        float AjusteVertical = 0f;

        // Variable para la rotacion del SCP
        float rotacionY = 0f;

        //Caja de colision
        float rawMinX, rawMaxX, rawMinZ, rawMaxZ;
        public float minWorldX, maxWorldX;
        public float minWorldZ, maxWorldZ;

        public void init(GL2 gl, String objPath, String texPath, float targetHeight, float spawnX, float spawnY, float spawnZ, float playerX, float playerZ) {
            this.x = spawnX;
            this.y = spawnY;
            this.z = spawnZ;

            cargarObj(objPath);
            cargarTextura(gl, texPath);
            calcularColision(targetHeight, playerX, playerZ);
        }

        //Metodo para calcular la rotacion
        public void actualizarPosicion(float newX, float newZ, float playerX, float playerZ) {
            this.x = newX;
            this.z = newZ;

            // --- LOGICA DE ROTACION (MIRAR AL JUGADOR) ---
            float dx = playerX - this.x;
            float dz = playerZ - this.z;
            // Sumamos 180 grados para corregir la orientación del modelo
            this.rotacionY = (float) Math.toDegrees(Math.atan2(dx, dz)) + 180.0f;

            // Recalcular la caja de colision
            minWorldX = x + (rawMinX * escala);
            maxWorldX = x + (rawMaxX * escala);
            minWorldZ = z + (rawMinZ * escala);
            maxWorldZ = z + (rawMaxZ * escala);
        }

        private void calcularColision(float alturaObjetivo, float playerX, float playerZ) {
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            rawMinX = Float.MAX_VALUE; rawMaxX = -Float.MAX_VALUE;
            rawMinZ = Float.MAX_VALUE; rawMaxZ = -Float.MAX_VALUE;

            for(float[] v : vertices) {
// 0-x 1-y 2-z
                if(v[1] < minY) minY = v[1];
                if(v[1] > maxY) maxY = v[1];
                if(v[0] < rawMinX) rawMinX = v[0];
                if(v[0] > rawMaxX) rawMaxX = v[0];
                if(v[2] < rawMinZ) rawMinZ = v[2];
                if(v[2] > rawMaxZ) rawMaxZ = v[2];
            }
            float alturaOriginal = maxY - minY;
            if(alturaOriginal == 0) alturaOriginal = 1;

            this.escala = alturaObjetivo / alturaOriginal;
            this.AjusteVertical = -minY * escala;

            actualizarPosicion(this.x, this.z, playerX, playerZ);
        }

        private void cargarTextura(GL2 gl, String path) {
            try {
                //Se crea el objeto tipo File
                File f = new File(path);
                if(f.exists()) {
                    //Convierte la textura a una que pueda leer opengl
                    textura = TextureIO.newTexture(f, true);
                    textura.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR); //interpolacion suave
                    textura.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR); //usa mipmaps
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void cargarObj(String path) {
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

        public void aparicion173(GL2 gl) {
            gl.glPushMatrix();
            //Pone al scp en el mundo
            gl.glTranslatef(x, y + AjusteVertical, z);

            // APLICAR ROTACIÓN HACIA EL JUGADOR
            gl.glRotatef(rotacionY, 0.0f, 1.0f, 0.0f);

            //Escala segun lo pedido

            gl.glScalef(escala, escala, escala);

            if (textura != null) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                textura.bind(gl);
                gl.glColor3f(1f, 1f, 1f); // se pone en blanco la textura
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor3f(0.8f, 0.7f, 0.6f);
            }
            //Empieza a cargar las caras de la textura
            gl.glBegin(GL2.GL_TRIANGLES);
            for (int[][] face : faces) {
                int nv = face.length;
                if (nv < 3) continue;
                for (int i = 1; i < nv - 1; i++) {
                    crearCaras(gl, face[0], face[i], face[i+1]);
                }
            }
            gl.glEnd();
            gl.glPopMatrix();
        }

        private void crearCaras(GL2 gl, int[] p1, int[] p2, int[] p3) {
            crearVertices(gl, p1);
            crearVertices(gl, p2);
            crearVertices(gl, p3);
        }

        private void crearVertices(GL2 gl, int[] p) {
            if (p == null) return;
            if (p[2] >= 0 && p[2] < normals.size()) { float[] n = normals.get(p[2]); gl.glNormal3f(n[0], n[1], n[2]); }
            if (p[1] >= 0 && p[1] < uvs.size()) { float[] t = uvs.get(p[1]); gl.glTexCoord2f(t[0], t[1]); }
            if (p[0] >= 0 && p[0] < vertices.size()) { gl.glVertex3f(vertices.get(p[0])[0], vertices.get(p[0])[1], vertices.get(p[0])[2]); }
        }
    }

    // VARIABLES MAPA Y COLISIONES

    public static class ParedColision {
        float x1, z1, x2, z2;
        float minX, maxX, minZ, maxZ;
        public ParedColision(float x1, float z1, float x2, float z2) {
            this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
            this.minX = Math.min(x1, x2); this.maxX = Math.max(x1, x2);
            this.minZ = Math.min(z1, z2); this.maxZ = Math.max(z1, z2);
        }
    }

    private List<ParedColision> murosMapa = new ArrayList<>();
    private final Map<String, String> texturaMapa = new HashMap<>();
    private List<float[]> vertices = new ArrayList<>();
    private List<float[]> uvs = new ArrayList<>();
    private List<float[]> normals = new ArrayList<>();


    public static class GrupoMaterial {
        String nombreMaterial;
        List<int[][]> Caras = new ArrayList<>();
        public GrupoMaterial(String nombre) { this.nombreMaterial = nombre; }
    }

    //grupos divididos segun su material

    private List<GrupoMaterial> renderGroups = new ArrayList<>();
    //Diccionario de texturas
    private Map<String, Texture> loadedTextures = new HashMap<>();

    private float modelScale = 1.0f;
    private float centerX, centerY, centerZ;
    private float rawMinX = Float.MAX_VALUE, rawMaxX = -Float.MAX_VALUE;
    private float rawMinY = Float.MAX_VALUE, rawMaxY = -Float.MAX_VALUE;
    private float rawMinZ = Float.MAX_VALUE, rawMaxZ = -Float.MAX_VALUE;

    private Robot robot;
    private Point centrado;
    private boolean isRobotMoving = false;
    private float sensibilidadMouse = 0.15f;
    private boolean[] teclas = new boolean[256];

    //Dibuja los datos en pantalla
    private TextRenderer textRenderer;
    private static FPSAnimator animator;
    private boolean DebugColisiones = false;

    //Punto de inicio del programa
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile profile = GLProfile.getDefault();
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setHardwareAccelerated(true);
            caps.setDoubleBuffered(true);
            caps.setDepthBits(24);

            JFrame frame = new JFrame("SCP-173 - Final Game Logic");
            ProyectoGraficacionAD25 panel = new ProyectoGraficacionAD25(caps);

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

    public ProyectoGraficacionAD25(GLCapabilities caps) {
        super(caps);
        inicializaTexturaMapa();
        this.addGLEventListener(this);
        this.addKeyListener(this);
        this.addMouseMotionListener(this);
        this.setFocusable(true);
        this.requestFocusInWindow();
        try { robot = new Robot(); } catch (AWTException e) { e.printStackTrace(); }
    }

    private void cargarTexturasBarraParpadeo(GL2 gl) {
        texturasBarraParpadeo = new Texture[NIVELES_BARRA_PARPADEO + 1]; // índices 1..5

        for (int i = 1; i <= NIVELES_BARRA_PARPADEO; i++) {
            try {
                // Archivos: ./data/1barra.png, ./data/2barra.png, ..., ./data/5barra.png
                File f = new File("./data/" + i + "barra.png");
                if (f.exists()) {
                    Texture t = TextureIO.newTexture(f, true);
                    t.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
                    t.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                    texturasBarraParpadeo[i] = t;
                } else {
                    System.out.println("No se encontró la textura de barra: " + f.getPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void inicializaTexturaMapa() {
        texturaMapa.put("metal", "metal.jpeg");
        texturaMapa.put("white", "white.jpeg");
        texturaMapa.put("whitewall", "white.jpeg");
        texturaMapa.put("dirtymetal", "dirtymetal.jpeg");
        texturaMapa.put("floor", "tilefloor.jpeg");
        texturaMapa.put("tilefloor", "tilefloor.jpeg");
        texturaMapa.put("door", "Door01.jpeg");
        texturaMapa.put("bigdoor", "containment_doors.jpg");
        texturaMapa.put("containment_doors", "containment_doors.jpeg");
        texturaMapa.put("glass", "glass.jpeg");
        texturaMapa.put("controlpanel", "controlpanel.jpeg");
        texturaMapa.put("vent", "vent.jpeg");
        texturaMapa.put("monitor", "flat_monitor.jpeg");
        texturaMapa.put("screen", "flat_monitor.jpeg");
        texturaMapa.put("keyboard", "keyboard.jpeg");
        texturaMapa.put("seat", "officeseat_a.jpeg");
        texturaMapa.put("cabinet", "cabinet_a.jpeg");
        texturaMapa.put("logo", "scplogo.jpeg");
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
        cargarTexturasBarraParpadeo(gl);

        float rawX = 8.50f;
        float rawY = -1.25f;
        float rawZ = -2.30f;

        float finalX = (rawX - centerX) * modelScale;
        float finalY = (rawY - centerY) * modelScale;
        float finalZ = (rawZ - centerZ) * modelScale;

        if (Float.isNaN(finalX) || Math.abs(finalX) > 100) {
            finalX = 0.0f; finalY = -1.25f; finalZ = 0.0f;
        }

        scp173 = new SCP173Entidad();
        scp173.init(gl, SCP_OBJ_PATH, SCP_TEX_PATH, 1.8f, finalX, finalY, finalZ, camX, camZ);

        linterna(gl);

        try {
            File f = new File("./data/Menu.gif");  // Tu archivo Menu.gif
            if (f.exists()) {
                texturaMenu = TextureIO.newTexture(f, true);
            } else {
                System.out.println("No se encontró Menu.gif en /data/");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void linterna(GL2 gl) {
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT1);

        // Luz ambiente global baja por defecto
        float[] globalAmbient = { OSCURIDAD_BASE, OSCURIDAD_BASE,OSCURIDAD_BASE, 1.0f };
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

        // CONFIGURACIÓN DE LA LINTERNA
        float[] ambient  = {0.0f, 0.0f, 0.0f, 1.0f};
        float[] diffuse  = {2.0f, 2.0f, 1.8f, 1.0f};   // luz intensa
        float[] specular = {0.3f, 0.3f, 0.3f, 1.0f};   // ligero brillo

        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT,  ambient, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE,  diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, specular, 0);

        // Cono cerrado tipo linterna
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_CUTOFF, 20.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_EXPONENT, 40.0f);

        // Atenuación con la distancia
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_CONSTANT_ATTENUATION,  1.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_LINEAR_ATTENUATION,    0.005f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_QUADRATIC_ATTENUATION, 0.001f);
    }



    private void pestaneo() {
        if (muerte || scp173 == null) return;

        parpadeo = true;

        if (parpadeoForzado) {
            contadorParpadeo = PARPADEO_DURACION_DOBLE;
            vecesAvanceSCP = 2; // avanza dos veces
        } else {
            contadorParpadeo = PARPADEO_DURACION_NORMAL;
            vecesAvanceSCP = 1; // avanza solo una vez
        }

        parpadeoForzado = false;

        // el SCP avanza paso a paso para que no "atraviese" al jugador
        for (int i = 0; i < vecesAvanceSCP; i++) {

            float dx = camX - scp173.x;
            float dz = camZ - scp173.z;
            float distancia = (float) Math.sqrt(dx * dx + dz * dz);

            if (distancia < DISTANCIA_MUERTE) {
                muerte = true;
                return;
            }

            if (distancia == 0.0f) return;

            float avance = Math.min(distancia, SCP_MOVIMIENTO_DISTANCIA);

            float moveX = (dx / distancia) * avance;
            float moveZ = (dz / distancia) * avance;

            scp173.actualizarPosicion(
                    scp173.x + moveX,
                    scp173.z + moveZ,
                    camX,
                    camZ
            );

            float nuevaDist = (float) Math.sqrt(
                    (camX - scp173.x) * (camX - scp173.x) +
                            (camZ - scp173.z) * (camZ - scp173.z)
            );

            if (nuevaDist < DISTANCIA_MUERTE) {
                muerte = true;
                return;
            }
        }
    }
    private void dibujarMenu(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        int width = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0, width, 0, height, -1, 1);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);

        if (texturaMenu != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            texturaMenu.bind(gl);
            gl.glColor3f(1f, 1f, 1f);

            gl.glBegin(GL2.GL_QUADS);
            gl.glTexCoord2f(0, 1); gl.glVertex2f(0, 0);
            gl.glTexCoord2f(1, 1); gl.glVertex2f(width, 0);
            gl.glTexCoord2f(1, 0); gl.glVertex2f(width, height);
            gl.glTexCoord2f(0, 0); gl.glVertex2f(0, height);
            gl.glEnd();

            gl.glDisable(GL2.GL_TEXTURE_2D);
        }

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);

        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }


    @Override
    public void display(GLAutoDrawable drawable) {
        if (enMenu) {
            dibujarMenu(drawable);
            return; // ⛔ NO se ejecuta nada del juego
        }

        if (muerte) {
            dibujaPantallaMuerte(drawable);
            return;
        }

        handleMovement();


        // Cuenta regresiva de parpadeo: 5 → 1 (baja 1 nivel por segundo)
        if (!parpadeo && !muerte) {
            contadorFramesBarra++;

            if (contadorFramesBarra >= FRAMES_POR_NIVEL_BARRA) {
                contadorFramesBarra = 0;

                if (nivelBarraParpadeo > 0) {
                    nivelBarraParpadeo--;  // baja 1 nivel
                }

                //parpadeo forzado
                if (nivelBarraParpadeo <= 0) {
                    parpadeoForzado = true;   // Avance doble del SCP
                    pestaneo();               // Ejecuta la mecanica de parpadeo

                    // Reiniciamos la barra para el siguiente ciclo
                    nivelBarraParpadeo = NIVELES_BARRA_PARPADEO;
                    contadorFramesBarra = 0;
                }
            }
        }


        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // 1) Luz ambiente global
        float ambientLevel = luzGlobal ? LUZ_MAXIMA : OSCURIDAD_BASE;
        float[] globalAmbient = { ambientLevel, ambientLevel, ambientLevel, 1.0f };
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

        // 2) Linterna en espacio de ojo
        if (estadoLinterna && !luzGlobal) {
            gl.glEnable(GL2.GL_LIGHT1);

            float[] lightPosEye = {
                    0.25f,
                    alturaLinterna,
                    0.0f,
                    1.0f
            };
            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, lightPosEye, 0);

            float[] spotDir = {0.0f, 0.0f, -1.0f};
            gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPOT_DIRECTION, spotDir, 0);

        } else {
            gl.glDisable(GL2.GL_LIGHT1);
        }

        // 3) Rotación de cámara
        gl.glRotatef(viewAngleX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(viewAngleY, 0.0f, 1.0f, 0.0f);

        // 4) Traslación del jugador
        gl.glTranslatef(-camX, -camY, -camZ);

        // 5) Dibujado del mapa y SCP SOLO si no hay parpadeo
        if (!parpadeo) {
            gl.glPushMatrix();
            gl.glScalef(modelScale, modelScale, modelScale);
            gl.glTranslatef(-centerX, -centerY, -centerZ);
            drawMapModel(gl, false);
            drawMapModel(gl, true);
            if (DebugColisiones && !modoEspectador) drawDebugWalls(gl);
            gl.glPopMatrix();

            if (scp173 != null) {
                scp173.aparicion173(gl);
            }

            if (DebugColisiones && !modoEspectador) drawDebugSCPBox(gl);
        }

        // 6) HUD
        drawHUD(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
    }




    private void dibujaPantallaMuerte(GLAutoDrawable drawable) {
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
        for (ParedColision w : murosMapa) {
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
        float playerMinX = targetX - ESPACIO_JUGADOR;
        float playerMaxX = targetX + ESPACIO_JUGADOR;
        float playerMinZ = targetZ - ESPACIO_JUGADOR;
        float playerMaxZ = targetZ + ESPACIO_JUGADOR;
        float scpMinX = scp173.minWorldX;
        float scpMaxX = scp173.maxWorldX;
        float scpMinZ = scp173.minWorldZ;
        float scpMaxZ = scp173.maxWorldZ;
        boolean overlapX = playerMaxX > scpMinX && playerMinX < scpMaxX;
        boolean overlapZ = playerMaxZ > scpMinZ && playerMinZ < scpMaxZ;
        return overlapX && overlapZ;
    }

    private void handleMovement() {
        if (enMenu || parpadeo || muerte) return;

        float dx = 0, dz = 0;
        double rads = Math.toRadians(viewAngleY);

        if (teclas[KeyEvent.VK_W]) { dx += Math.sin(rads) * VELOCIDAD_MOV; dz -= Math.cos(rads) * VELOCIDAD_MOV; }
        if (teclas[KeyEvent.VK_S]) { dx -= Math.sin(rads) * VELOCIDAD_MOV; dz += Math.cos(rads) * VELOCIDAD_MOV; }
        if (teclas[KeyEvent.VK_A]) { dx -= Math.cos(rads) * VELOCIDAD_MOV; dz -= Math.sin(rads) * VELOCIDAD_MOV; }
        if (teclas[KeyEvent.VK_D]) { dx += Math.cos(rads) * VELOCIDAD_MOV; dz += Math.sin(rads) * VELOCIDAD_MOV; }

        if (dx != 0) {
            float nextX = camX + dx;
            if (modoEspectador || (!isCollidingWithWall(nextX, camZ) && !checkSCPCollision(nextX, camZ))) camX = nextX;
        }
        if (dz != 0) {
            float nextZ = camZ + dz;
            if (modoEspectador || (!isCollidingWithWall(camX, nextZ) && !checkSCPCollision(camX, nextZ))) camZ = nextZ;
        }
    }

    private boolean isCollidingWithWall(float targetX, float targetZ) {
        float playerObjX = (targetX / modelScale) + centerX;
        float playerObjZ = (targetZ / modelScale) + centerZ;
        float radiusObj = ESPACIO_JUGADOR / modelScale;

        for (ParedColision wall : murosMapa) {
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
        GrupoMaterial currentGroup = new GrupoMaterial("default");
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
                        currentGroup = new GrupoMaterial(matName);
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
                        currentGroup.Caras.add(faceData);

                        if (numPoints >= 3) {
                            float[] n = (faceData[0][2] >= 0) ? normals.get(faceData[0][2]) : new float[]{0, 1, 0};
                            float absNy = Math.abs(n[1]);

                            if (absNy < 0.5f) {
                                float[] v1 = vertices.get(faceData[0][0]);
                                float[] v2 = vertices.get(faceData[1][0]);
                                float[] v3 = vertices.get(faceData[2][0]);

                                String matNameLower = currentGroup.nombreMaterial.toLowerCase();
                                boolean mightBeWall = matNameLower.contains("white") || matNameLower.contains("wall");
                                boolean isMetalFrame = matNameLower.contains("metal") ||
                                        matNameLower.contains("frame") ||
                                        matNameLower.contains("door") ||
                                        matNameLower.contains("glass") ||
                                        matNameLower.contains("dark");

                                if (mightBeWall && !isMetalFrame) {
                                    murosMapa.add(new ParedColision(v1[0], v1[2], v2[0], v2[2]));
                                    murosMapa.add(new ParedColision(v2[0], v2[2], v3[0], v3[2]));
                                    murosMapa.add(new ParedColision(v3[0], v3[2], v1[0], v1[2]));
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
        GL2 gl = GLContext.getCurrentGL().getGL2();

        // Si estamos en parpadeo: pantalla negra y no se dibuja HUD
        if (parpadeo) {
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glOrtho(0, width, 0, height, -1, 1);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();

            gl.glDisable(GL2.GL_LIGHTING);
            gl.glDisable(GL2.GL_DEPTH_TEST);
            gl.glColor3f(0.0f, 0.0f, 0.0f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(width, 0);
            gl.glVertex2f(width, height);
            gl.glVertex2f(0, height);
            gl.glEnd();
            gl.glEnable(GL2.GL_DEPTH_TEST);
            gl.glEnable(GL2.GL_LIGHTING);

            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);

            contadorParpadeo--;
            if (contadorParpadeo <= 0) {
                parpadeo = false;
            }
            return;
        }

        // Texto HUD (usa su propio modo 2D interno, independiente de la luz)
        textRenderer.beginRendering(width, height);

        if (modoEspectador) {
            textRenderer.setColor(Color.RED);
            textRenderer.draw("MODO FANTASMA (NOCLIP) ACTIVADO", width / 2 - 100, height - 80);
        }

        textRenderer.setColor(Color.GREEN);
        textRenderer.draw(String.format("POS: %.2f, %.2f, %.2f", camX, camY, camZ), 10, height - 20);
        textRenderer.draw(String.format("ANG: %.1f / %.1f", viewAngleX, viewAngleY), 10, height - 40);
        textRenderer.draw("[ESPACIO] Parpadear", 10, height - 60);
        textRenderer.draw("[F] Linterna: " + (estadoLinterna ? "ON" : "OFF"), 10, height - 80);
        textRenderer.draw("[L] Luz Global: " + (luzGlobal ? "ON" : "OFF"), 10, height - 95);

        textRenderer.endRendering();

        // Barra de parpadeo (gráfica) SIEMPRE VISIBLE (independiente de la luz)
        if (texturasBarraParpadeo != null &&
                nivelBarraParpadeo >= 1 &&
                nivelBarraParpadeo <= NIVELES_BARRA_PARPADEO &&
                texturasBarraParpadeo[nivelBarraParpadeo] != null) {

            Texture t = texturasBarraParpadeo[nivelBarraParpadeo];

            int barWidth = t.getImageWidth();
            int barHeight = t.getImageHeight();

            // Posición: centrada abajo
            int x = (width - barWidth) / 2;
            int y = 10;

            // Configurar proyección 2D para dibujar HUD
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glOrtho(0, width, 0, height, -1, 1);

            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();

            // Desactivar luces y profundidad para que no afecten el HUD
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glDisable(GL2.GL_DEPTH_TEST);

            gl.glEnable(GL2.GL_TEXTURE_2D);
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

            t.bind(gl);
            gl.glColor3f(1f, 1f, 1f); // dibujar textura tal cual

            gl.glBegin(GL2.GL_QUADS);
            gl.glTexCoord2f(0f, 0f); gl.glVertex2f(x, y);
            gl.glTexCoord2f(1f, 0f); gl.glVertex2f(x + barWidth, y);
            gl.glTexCoord2f(1f, 1f); gl.glVertex2f(x + barWidth, y + barHeight);
            gl.glTexCoord2f(0f, 1f); gl.glVertex2f(x, y + barHeight);
            gl.glEnd();

            // Restaurar estado de OpenGL
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glDisable(GL2.GL_BLEND);
            gl.glEnable(GL2.GL_DEPTH_TEST);
            gl.glEnable(GL2.GL_LIGHTING);

            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);
        }
    }


    private void loadMapTextures(GL2 gl) {
        for (GrupoMaterial group : renderGroups) {
            String matName = group.nombreMaterial.toLowerCase();
            if (!loadedTextures.containsKey(matName)) {
                String fileName = texturaMapa.getOrDefault(matName,
                        texturaMapa.entrySet().stream().filter(e -> matName.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("metal.jpeg"));
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

        for (GrupoMaterial group : renderGroups) {
            String matName = group.nombreMaterial.toLowerCase();
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
            for (int[][] face : group.Caras) {
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

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        if (height <= 0) height = 1;
        float aspect = (float) width / height;
        gl.glViewport(0, 0, width, height);

        float zNear = 0.1f;
        float zFar = 200.0f;
        float fovy = 45.0f;

        if (this.isShowing()) {
            Point loc = this.getLocationOnScreen();
            centrado = new Point(loc.x + width / 2, loc.y + height / 2);
        }

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float top = (float) Math.tan(Math.toRadians(fovy) / 2.0) * zNear;
        gl.glFrustum(-top * aspect, top * aspect, -top, top, zNear, zFar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

    private float normalizeAngle360(float angle) {
        angle %= 360.0f;
        if (angle < 0) angle += 360.0f;
        return angle;
    }

    @Override
    public void keyPressed(KeyEvent e) {

        // ===== MENÚ PRINCIPAL =====
        // Si estamos en el menú, SOLO reaccionar a ENTER o ESC
        if (enMenu) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                enMenu = false;   // salir del menú e iniciar el juego
            } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                System.exit(0);   // salir del programa desde el menú
            }
            return; // no permitir ninguna otra acción mientras esté el menú
        }

        // ===== JUEGO NORMAL (ya saliste del menú) =====
        if (e.getKeyCode() < 256) {
            teclas[e.getKeyCode()] = true;
        }

        if (e.getKeyCode() == KeyEvent.VK_N) {
            modoEspectador = !modoEspectador;
        }

        if (e.getKeyCode() == KeyEvent.VK_F) {
            estadoLinterna = !estadoLinterna;
            if (estadoLinterna) {
                luzGlobal = false;
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_L) {
            luzGlobal = !luzGlobal;
            if (luzGlobal) {
                estadoLinterna = false;
            }
        }

        // ===== PARPADEO MANUAL =====
        if (e.getKeyCode() == KeyEvent.VK_SPACE && !parpadeo && !muerte) {
            parpadeoForzado = false;                       // parpadeo normal
            nivelBarraParpadeo = NIVELES_BARRA_PARPADEO;   // reinicia la barra a 5
            contadorFramesBarra = 0;                       // reinicia contador
            pestaneo();                                   // ejecuta parpadeo
        }

        // ===== SALIR DEL JUEGO =====
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() < 256) {
            teclas[e.getKeyCode()] = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (isRobotMoving || centrado == null || parpadeo || muerte || enMenu) return;

        int dx = e.getXOnScreen() - centrado.x;
        int dy = e.getYOnScreen() - centrado.y;
        if (dx == 0 && dy == 0) return;

        viewAngleY += dx * sensibilidadMouse;
        viewAngleX += dy * sensibilidadMouse;

        viewAngleY = normalizeAngle360(viewAngleY);

        if (viewAngleX > 89) viewAngleX = 89;
        if (viewAngleX < -89) viewAngleX = -89;

        isRobotMoving = true;
        robot.mouseMove(centrado.x, centrado.y);
        isRobotMoving = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }
}
